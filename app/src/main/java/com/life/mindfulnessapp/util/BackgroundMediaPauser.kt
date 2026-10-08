package com.life.mindfulnessapp.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * 尽量停掉**指定 App**还在播的音视频。
 *
 * 第三方没有权限点名杀掉某个包的播放器，这里做三件合规的事：
 * 1. 发媒体暂停键（多数短视频 / 播放器注册了 MediaSession，会响应）
 * 2. 抢占音频焦点，让遵守焦点协议的 App 暂停
 * 3. 拦截盖层期间：若探测到目标仍在出媒体声，把系统媒体流静音（无法按包静音；卸门恢复）
 *
 * **保护后台音乐：** 暂停键 / 抢焦点 / 静音都是全局的。若另有 App（如网易云）在播，
 * 一律不动作，避免把阅读 App 盖门时把后台歌停掉或静音。
 *
 * 用于：
 * - 回桌面 / 守住离开：[pauseActivePlayback] 短促一拍（仅目标在播且无外部媒体时）
 * - 拦截页盖住期间：[beginSuppressPlayback] 仅在目标出声且无外部媒体时压/静音，持有到 [endSuppressPlayback]
 *
 * 无法保证所有 App（尤其主动忽略焦点的短视频）都停下；盖层路径会多拍补压，必要时静音兜底。
 */
object BackgroundMediaPauser {

    private const val TAG = "MediaPauser"
    private const val REPAUSE_MS = 420L
    private const val ABANDON_FOCUS_MS = 1600L
    private const val PLAYBACK_POLL_MS = 500L
    private const val PLAYER_STATE_STARTED = 2
    private const val FIRST_ISOLATED_UID = 99000
    private const val LAST_ISOLATED_UID = 99999
    private const val FIRST_APP_ZYGOTE_ISOLATED_UID = 90000
    private const val LAST_APP_ZYGOTE_ISOLATED_UID = 98999
    private const val UNSET_FOREIGN = "__unset_foreign__"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var suppressPackage: String? = null
    private var stopSuppressObserve: (() -> Unit)? = null
    private var clientUidResolved = false
    private var clientPkgResolved = false
    private var playerStateResolved = false
    private var cachedClientUid: java.lang.reflect.Method? = null
    private var cachedClientPkg: java.lang.reflect.Method? = null
    private var cachedPlayerState: java.lang.reflect.Method? = null

    private var suppressAppContext: Context? = null

    /** 盖层期间我们把 STREAM_MUSIC 静音过；卸门必须恢复，避免把用户音量留在 0。 */
    private var mutedMediaStreamByUs = false
    private var volumeBeforeMute = -1

    private val repauseRunnable = Runnable {
        val am = audioManager ?: return@Runnable
        val pkg = suppressPackage
        val ctx = suppressAppContext
        if (pkg != null && ctx != null) {
            // 仅目标出声且无外部媒体时再压；有网易云等后台歌则绝不发暂停键
            suppressTargetAudioIfSafe(ctx, am, pkg)
        }
    }

    private val abandonFocusRunnable = Runnable { abandonFocus() }

    private fun scheduleSuppressRepauses() {
        mainHandler.removeCallbacks(repauseRunnable)
        // 多拍：首帧漏检 / OEM 晚起播 / 冷启动自动播仍能压住（仍受「无外部媒体」约束）
        mainHandler.postDelayed(repauseRunnable, 120L)
        mainHandler.postDelayed(repauseRunnable, REPAUSE_MS)
        mainHandler.postDelayed(repauseRunnable, 900L)
        mainHandler.postDelayed(repauseRunnable, 1_800L)
        mainHandler.postDelayed(repauseRunnable, 3_200L)
    }

    /** 盖门压声：暂停键 + 停键 + 抢焦点（不先放弃已有焦点，避免空隙里又出声） */
    private fun suppressNow(am: AudioManager) {
        dispatchPause(am)
        requestFocus(am)
    }

    /**
     * 仅当 [targetPackage] 在出媒体声、且没有其他 App 在播时，才暂停/抢焦点/静音。
     * 有后台歌（网易云等）时整段跳过，避免全局媒体键误伤。
     */
    private fun suppressTargetAudioIfSafe(
        appContext: Context,
        am: AudioManager,
        targetPackage: String
    ): Boolean {
        val foreign = findForeignMediaPackage(appContext, am, targetPackage)
        if (foreign != null) {
            Log.d(TAG, "跳过压声：后台另有媒体 $foreign，保护其播放")
            return false
        }
        if (!packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)) {
            Log.d(TAG, "跳过压声：$targetPackage 未在出媒体声")
            return false
        }
        suppressNow(am)
        muteMediaIfTargetPlaying(appContext, am, targetPackage)
        return true
    }

    /**
     * 探测到 [targetPackage] 仍在出媒体声、且无外部媒体时，静音系统媒体流。
     * Android 没有公开的「按包静音」API，只能动 STREAM_MUSIC；卸门时 [restoreMediaMute] 还原。
     */
    private fun muteMediaIfTargetPlaying(
        appContext: Context,
        am: AudioManager,
        targetPackage: String
    ) {
        if (mutedMediaStreamByUs) return
        val foreign = findForeignMediaPackage(appContext, am, targetPackage)
        if (foreign != null) {
            Log.d(TAG, "跳过静音：后台另有媒体 $foreign")
            return
        }
        if (!packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)) {
            return
        }
        applyMediaMute(am)
    }

    private fun applyMediaMute(am: AudioManager) {
        if (mutedMediaStreamByUs) return
        try {
            val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (vol <= 0 && am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.d(TAG, "媒体流已静音，不接管")
                return
            }
            volumeBeforeMute = vol
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
            // 部分 OEM 的 ADJUST_MUTE 不生效，再落到音量 0
            if (!am.isStreamMute(AudioManager.STREAM_MUSIC) &&
                am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
            ) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
            mutedMediaStreamByUs = true
            Log.d(TAG, "已静音媒体流 volWas=$vol")
        } catch (e: Exception) {
            Log.w(TAG, "静音媒体流失败", e)
            volumeBeforeMute = -1
            mutedMediaStreamByUs = false
        }
    }

    private fun restoreMediaMute() {
        if (!mutedMediaStreamByUs) return
        mutedMediaStreamByUs = false
        val saved = volumeBeforeMute
        volumeBeforeMute = -1
        val am = audioManager ?: return
        try {
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    0
                )
            }
            // setStreamVolume(0) 路径：UNMUTE 后音量仍为 0 时写回盖门前档位
            // 若用户在静音期间自己调高了音量，尊重当前值，不再覆盖
            val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (saved > 0 && now == 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            }
            Log.d(TAG, "已恢复媒体音量 saved=$saved now=${am.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        } catch (e: Exception) {
            Log.w(TAG, "恢复媒体音量失败", e)
        }
    }

    /**
     * @param targetPackage 要停掉的包；若该包当前没有活跃播放，或另有 App 在播媒体，则什么也不做。
     */
    fun pauseActivePlayback(context: Context, targetPackage: String) {
        if (targetPackage.isBlank()) return
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        // 拦截页已在压着同一包时：仍须过「无外部媒体」门，避免误停后台歌
        if (suppressPackage == targetPackage) {
            mainHandler.post { suppressTargetAudioIfSafe(appContext, am, targetPackage) }
            return
        }
        mainHandler.post {
            val foreign = findForeignMediaPackage(appContext, am, targetPackage)
            if (foreign != null) {
                Log.d(TAG, "跳过暂停：后台另有媒体 $foreign，保护其播放")
                return@post
            }
            if (!packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)) {
                Log.d(TAG, "跳过暂停：$targetPackage 无活跃媒体播放")
                return@post
            }
            audioManager = am
            mainHandler.removeCallbacks(repauseRunnable)
            mainHandler.removeCallbacks(abandonFocusRunnable)
            suppressNow(am)
            // 短促离开：不挂长期 suppressPackage，单独补一拍后再交还焦点
            mainHandler.postDelayed({
                if (suppressPackage != null) return@postDelayed
                if (findForeignMediaPackage(appContext, am, targetPackage) != null) return@postDelayed
                if (!packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)) {
                    return@postDelayed
                }
                dispatchPause(am)
            }, REPAUSE_MS)
            mainHandler.postDelayed(abandonFocusRunnable, ABANDON_FOCUS_MS)
        }
    }

    /**
     * 拦截页盖住 [targetPackage] 期间压住其播放。
     * 仅当目标在出媒体声、且没有其他 App 在播时才暂停/抢焦点/静音。
     * 卸门时调 [endSuppressPlayback]：只交还焦点与恢复音量，不再发暂停键。
     */
    fun beginSuppressPlayback(context: Context, targetPackage: String) {
        if (targetPackage.isBlank()) return
        val appContext = context.applicationContext
        mainHandler.post {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@post
            if (suppressPackage == targetPackage && stopSuppressObserve != null) {
                suppressTargetAudioIfSafe(appContext, am, targetPackage)
                scheduleSuppressRepauses()
                return@post
            }
            endSuppressInternal()
            suppressPackage = targetPackage
            suppressAppContext = appContext
            audioManager = am
            mainHandler.removeCallbacks(abandonFocusRunnable)
            suppressTargetAudioIfSafe(appContext, am, targetPackage)
            scheduleSuppressRepauses()
            stopSuppressObserve = observePackageMediaPlayback(appContext, targetPackage) { playing ->
                if (!playing || suppressPackage != targetPackage) return@observePackageMediaPlayback
                Log.d(TAG, "拦截页期间 $targetPackage 又在播，尝试补压（仍保护外部媒体）")
                suppressTargetAudioIfSafe(appContext, am, targetPackage)
            }
            Log.d(TAG, "开始压播放监听：$targetPackage")
        }
    }

    /** 卸拦截层 / 确认进门后结束压播放：恢复音量、交还焦点；不发暂停键，不误停第三方音乐。 */
    fun endSuppressPlayback() {
        mainHandler.post { endSuppressInternal() }
    }

    private fun endSuppressInternal() {
        val pkg = suppressPackage
        mainHandler.removeCallbacks(repauseRunnable)
        mainHandler.removeCallbacks(abandonFocusRunnable)
        stopSuppressObserve?.invoke()
        stopSuppressObserve = null
        suppressPackage = null
        suppressAppContext = null
        restoreMediaMute()
        abandonFocus()
        if (pkg != null) {
            Log.d(TAG, "结束压播放：$pkg")
        }
    }

    private fun pauseIfPlaying(
        appContext: Context,
        am: AudioManager,
        targetPackage: String,
        mediaOnly: Boolean
    ) {
        if (findForeignMediaPackage(appContext, am, targetPackage) != null) {
            Log.d(TAG, "跳过暂停：后台另有媒体")
            return
        }
        if (!packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = mediaOnly)) {
            Log.d(TAG, "跳过暂停：$targetPackage 无活跃播放")
            return
        }
        audioManager = am
        dispatchPause(am)
        requestFocus(am)
    }

    /**
     * 当前前台包是否在出媒体声（音乐 / 播客 / 视频音轨 / 带声游戏）。
     * 不需要通知监听权限；分不出「歌」和「视频」，短视频也会亮。
     *
     * 哔哩哔哩等会把播放器放在 isolated 进程（UID 对不上主包），
     * 因此除 UID / 包名匹配外，还会在「系统认为在播、且没有别的 App 声」时视为当前包。
     */
    fun packageHasActiveMediaPlayback(context: Context, targetPackage: String): Boolean {
        if (targetPackage.isBlank()) return false
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)
    }

    /**
     * 监听 [targetPackage] 媒体播放起停。返回取消函数。
     * 回调在主线程。部分 OEM 的 [AudioManager.AudioPlaybackCallback] 不可靠，故同时短轮询。
     */
    fun observePackageMediaPlayback(
        context: Context,
        targetPackage: String,
        onChanged: (Boolean) -> Unit
    ): () -> Unit {
        if (targetPackage.isBlank()) {
            onChanged(false)
            return {}
        }
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) {
            onChanged(false)
            return {}
        }
        var last: Boolean? = null
        fun emit() {
            val next = packageHasActivePlayback(appContext, am, targetPackage, mediaOnly = true)
            if (last != next) {
                last = next
                onChanged(next)
            }
        }
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                emit()
            }
        }
        val poll = object : Runnable {
            override fun run() {
                emit()
                mainHandler.postDelayed(this, PLAYBACK_POLL_MS)
            }
        }
        emit()
        am.registerAudioPlaybackCallback(callback, mainHandler)
        mainHandler.postDelayed(poll, PLAYBACK_POLL_MS)
        return {
            mainHandler.removeCallbacks(poll)
            try {
                am.unregisterAudioPlaybackCallback(callback)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    /**
     * 当前正在出媒体声、且不是 [excludePackage] 的那个包。
     * 找不到包名（隔离进程未署名）时返回 null，避免误停当前 App。
     */
    fun findForeignMediaPackage(context: Context, excludePackage: String): String? {
        if (excludePackage.isBlank()) return null
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return findForeignMediaPackage(appContext, am, excludePackage)
    }

    /**
     * 监听「别的 App 在播」。回调在主线程；返回取消函数。
     */
    fun observeForeignMediaPlayback(
        context: Context,
        excludePackage: String,
        onChanged: (String?) -> Unit
    ): () -> Unit {
        if (excludePackage.isBlank()) {
            onChanged(null)
            return {}
        }
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) {
            onChanged(null)
            return {}
        }
        var last: String? = UNSET_FOREIGN
        fun emit() {
            val next = findForeignMediaPackage(appContext, am, excludePackage)
            if (last != next) {
                last = next
                onChanged(next)
            }
        }
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                emit()
            }
        }
        val poll = object : Runnable {
            override fun run() {
                emit()
                mainHandler.postDelayed(this, PLAYBACK_POLL_MS)
            }
        }
        emit()
        am.registerAudioPlaybackCallback(callback, mainHandler)
        mainHandler.postDelayed(poll, PLAYBACK_POLL_MS)
        return {
            mainHandler.removeCallbacks(poll)
            try {
                am.unregisterAudioPlaybackCallback(callback)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun packageHasActivePlayback(
        context: Context,
        am: AudioManager,
        targetPackage: String,
        mediaOnly: Boolean
    ): Boolean {
        return try {
            val pm = context.packageManager
            val targetUid = runCatching { pm.getPackageUid(targetPackage, 0) }.getOrDefault(-1)
            val getUid = clientUidMethod()
            val getPkg = clientPackageMethod()
            val getState = playerStateMethod()
            val configs = am.activePlaybackConfigurations
            var matched = false
            var foreignMedia = false
            for (cfg in configs) {
                if (!isPlayerStarted(cfg, getState)) continue
                val mediaLike = isMediaLikePlayback(cfg)
                if (mediaOnly && !mediaLike) continue
                if (configBelongsToPackage(cfg, targetPackage, targetUid, pm, getUid, getPkg)) {
                    matched = true
                } else if (mediaLike && isForeignAttributed(cfg, targetUid, getUid, getPkg)) {
                    foreignMedia = true
                }
            }
            if (matched) return true
            // 隔离进程 / USAGE 乱标：UID 对不上时，系统在播且没有别的 App 声 → 视为当前包
            if (mediaOnly && am.isMusicActive && !foreignMedia) return true
            false
        } catch (e: Exception) {
            Log.w(TAG, "读取播放配置失败", e)
            if (mediaOnly) {
                runCatching { am.isMusicActive }.getOrDefault(false)
            } else {
                false
            }
        }
    }

    private fun findForeignMediaPackage(
        context: Context,
        am: AudioManager,
        excludePackage: String
    ): String? {
        return try {
            val pm = context.packageManager
            val excludeUid = runCatching { pm.getPackageUid(excludePackage, 0) }.getOrDefault(-1)
            val getUid = clientUidMethod()
            val getPkg = clientPackageMethod()
            val getState = playerStateMethod()
            val selfPkg = context.packageName
            for (cfg in am.activePlaybackConfigurations) {
                if (!isPlayerStarted(cfg, getState)) continue
                if (!isMediaLikePlayback(cfg)) continue
                if (configBelongsToPackage(cfg, excludePackage, excludeUid, pm, getUid, getPkg)) {
                    continue
                }
                val pkg = attributedPackage(cfg, pm, getUid, getPkg) ?: continue
                if (pkg == excludePackage || pkg.startsWith("$excludePackage:")) continue
                if (pkg == selfPkg) continue
                return pkg
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "读取外部播放失败", e)
            null
        }
    }

    private fun attributedPackage(
        cfg: AudioPlaybackConfiguration,
        pm: android.content.pm.PackageManager,
        getUid: java.lang.reflect.Method?,
        getPkg: java.lang.reflect.Method?
    ): String? {
        val clientPkg = runCatching { getPkg?.invoke(cfg) as? String }.getOrNull()?.trim().orEmpty()
        if (clientPkg.isNotEmpty()) return clientPkg.substringBefore(':')
        val clientUid = runCatching { getUid?.invoke(cfg) as? Int }.getOrNull() ?: return null
        if (isIsolatedUid(clientUid)) return null
        if (clientUid <= 0) return null
        return runCatching { pm.getPackagesForUid(clientUid)?.firstOrNull() }.getOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun configBelongsToPackage(
        cfg: AudioPlaybackConfiguration,
        targetPackage: String,
        targetUid: Int,
        pm: android.content.pm.PackageManager,
        getUid: java.lang.reflect.Method?,
        getPkg: java.lang.reflect.Method?
    ): Boolean {
        val clientPkg = runCatching { getPkg?.invoke(cfg) as? String }.getOrNull()?.trim().orEmpty()
        if (clientPkg.isNotEmpty()) {
            if (clientPkg == targetPackage || clientPkg.startsWith("$targetPackage:")) return true
        }
        val clientUid = runCatching { getUid?.invoke(cfg) as? Int }.getOrNull() ?: return false
        if (targetUid >= 0 && clientUid == targetUid) return true
        return runCatching {
            pm.getPackagesForUid(clientUid)?.contains(targetPackage) == true
        }.getOrDefault(false)
    }

    private fun isForeignAttributed(
        cfg: AudioPlaybackConfiguration,
        targetUid: Int,
        getUid: java.lang.reflect.Method?,
        getPkg: java.lang.reflect.Method?
    ): Boolean {
        val clientPkg = runCatching { getPkg?.invoke(cfg) as? String }.getOrNull()?.trim().orEmpty()
        if (clientPkg.isNotEmpty()) return true
        val clientUid = runCatching { getUid?.invoke(cfg) as? Int }.getOrNull() ?: return false
        if (isIsolatedUid(clientUid)) return false
        if (targetUid >= 0 && clientUid == targetUid) return false
        return clientUid > 0
    }

    private fun isPlayerStarted(
        cfg: AudioPlaybackConfiguration,
        getState: java.lang.reflect.Method?
    ): Boolean {
        if (getState == null) return true
        val state = runCatching { getState.invoke(cfg) as? Int }.getOrNull() ?: return true
        // @hide: 0 released / 1 idle / 2 started / 3 paused / 4 stopped
        return state == PLAYER_STATE_STARTED
    }

    private fun isMediaLikePlayback(cfg: AudioPlaybackConfiguration): Boolean {
        val usage = cfg.audioAttributes.usage
        return when (usage) {
            AudioAttributes.USAGE_ALARM,
            AudioAttributes.USAGE_NOTIFICATION,
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> false
            else -> true
        }
    }

    private fun isIsolatedUid(uid: Int): Boolean =
        uid in FIRST_ISOLATED_UID..LAST_ISOLATED_UID ||
            uid in FIRST_APP_ZYGOTE_ISOLATED_UID..LAST_APP_ZYGOTE_ISOLATED_UID

    private fun clientUidMethod(): java.lang.reflect.Method? = synchronized(this) {
        if (!clientUidResolved) {
            clientUidResolved = true
            cachedClientUid = runCatching {
                AudioPlaybackConfiguration::class.java.getMethod("getClientUid")
            }.getOrNull()
        }
        cachedClientUid
    }

    private fun clientPackageMethod(): java.lang.reflect.Method? = synchronized(this) {
        if (!clientPkgResolved) {
            clientPkgResolved = true
            cachedClientPkg = runCatching {
                AudioPlaybackConfiguration::class.java.getMethod("getClientPackageName")
            }.getOrNull()
        }
        cachedClientPkg
    }

    private fun playerStateMethod(): java.lang.reflect.Method? = synchronized(this) {
        if (!playerStateResolved) {
            playerStateResolved = true
            cachedPlayerState = runCatching {
                AudioPlaybackConfiguration::class.java.getMethod("getPlayerState")
            }.getOrNull()
        }
        cachedPlayerState
    }

    private fun dispatchPause(am: AudioManager) {
        val now = SystemClock.uptimeMillis()
        fun tap(code: Int) {
            am.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0)
            )
            am.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0)
            )
        }
        // PAUSE 多数 MediaSession 会听；STOP / PLAY_PAUSE 补一层短视频与 OEM 差异
        tap(KeyEvent.KEYCODE_MEDIA_PAUSE)
        tap(KeyEvent.KEYCODE_MEDIA_STOP)
        tap(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        Log.d(TAG, "已发送 MEDIA_PAUSE/STOP/PLAY_PAUSE")
    }

    private fun requestFocus(am: AudioManager) {
        // 先抢新焦点，再放弃旧请求：中间不能空窗，否则短视频会立刻续播
        val previous = focusRequest
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            .build()
        focusRequest = request
        val result = am.requestAudioFocus(request)
        Log.d(TAG, "requestAudioFocus result=$result")
        if (previous != null && previous !== request) {
            try {
                am.abandonAudioFocusRequest(previous)
            } catch (e: Exception) {
                Log.w(TAG, "abandon 旧焦点失败", e)
            }
        }
    }

    private fun abandonFocus() {
        val am = audioManager
        val request = focusRequest ?: return
        focusRequest = null
        if (am == null) return
        try {
            am.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            Log.w(TAG, "abandonAudioFocus 失败", e)
        }
    }
}
