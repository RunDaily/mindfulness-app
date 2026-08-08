package com.life.mindfulnessapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.overlay.ManualEndDestination
import com.life.mindfulnessapp.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MonitorForegroundService : Service() {

    companion object {
        const val TAG = "MonitorService"
        const val CHANNEL_ID = "mindfulness_monitor"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP"
        /** 打开首页备注弹窗的 Intent Action */
        const val ACTION_OPEN_NOTE = "ACTION_OPEN_NOTE"
        /** Intent extra key：需要弹出备注弹窗的 recordId */
        const val EXTRA_NOTE_RECORD_ID = "extra_note_record_id"
        /** Intent extra：结束流程中是否已完成意图回顾 */
        const val EXTRA_SESSION_REVIEWED = "extra_session_reviewed"
        /** 打开监控配置页的 Intent Action */
        const val ACTION_OPEN_APP_LIMIT_EDIT = "ACTION_OPEN_APP_LIMIT_EDIT"
        /** Intent extra key：要编辑限制的 App 包名 */
        const val EXTRA_APP_PACKAGE_NAME = "extra_app_package_name"
        /** 打开心锚首页（拦截页「去做点别的」） */
        const val ACTION_OPEN_HOME = "ACTION_OPEN_HOME"
        /** 打开「想去的地方」配置页 */
        const val ACTION_OPEN_POSITIVE_DESTINATIONS = "ACTION_OPEN_POSITIVE_DESTINATIONS"
        /**
         * LocalBroadcast Action：用户在 Anchor App 内手动结束会话时发送。
         * MainActivity 收到后显示 Snackbar 轻提示。
         */
        const val ACTION_SESSION_ENDED_IN_APP = "com.life.mindfulnessapp.SESSION_ENDED_IN_APP"
        /** 会话结束通知的渠道 ID */
        const val SESSION_END_CHANNEL_ID = "session_end_notify"
        /** 会话结束通知 ID */
        const val SESSION_END_NOTIFICATION_ID = 3001
        const val POLL_INTERVAL_MS = 1000L
        /** 含意图门：离开倒计时默认秒数（实际以 AppPreferences 为准） */
        const val DEFAULT_AWAY_COUNTDOWN_SEC =
            AppPreferences.DEFAULT_AWAY_COUNTDOWN_SECONDS.toLong()
        /**
         * 后台切换防抖延迟（毫秒）。
         * 用于过滤通知栏下拉、系统弹框等导致的短暂"离开前台"误判。
         * 设置为 1500ms：通知栏操作通常 < 1s，真正切到桌面/其他 App 则持续较长时间。
         */
        const val BACKGROUND_DEBOUNCE_MS = 1500L

        /**
         * 锁屏宽限时间（毫秒）。
         * 用户息屏后 3 分钟内亮屏并回到被监控 App，视为「同一次使用意图中断」：
         *   - 不重新弹拦截页
         *   - 息屏期间不计入使用时长（计时已在息屏时冻结）
         * 注意：息屏时无论会话当时是否已被 UsageStats 误判为后台，都只进宽限期、不直接结束，
         * 避免「锁屏→亮屏回 App」被当成新进入而重弹首次拦截。
         * 超过 3 分钟未回到该 App，则静默结束会话，并写入待确认中断；
         * 下次进入时在标准拦截页意图门区提供「继续上次」或重写意图。
         */
        const val SCREEN_OFF_GRACE_MS = 3 * 60 * 1000L
        const val SCREEN_OFF_GRACE_MINUTES = 3

        fun start(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var appLimitRepository: AppLimitRepository
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var limitResetRepository: LimitResetRepository
    @Inject lateinit var usageRecordRepository: UsageRecordRepository
    @Inject lateinit var pendingInterruptStore: com.life.mindfulnessapp.data.PendingInterruptStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var backgroundTimeoutJob: Job? = null
    /** 含意图门离开倒计时剩余秒；锁屏时冻结递减 */
    private var awayCountdownRemainingSec: Long = -1L
    /** 锁屏导致离开倒计时冻结 */
    @Volatile private var awayCountdownFrozen: Boolean = false

    /** 当前配置的离开倒计时秒数（意图门暂停胶囊 / 纯锁静默等待同量级） */
    private fun configuredAwayCountdownSec(): Long =
        appPreferences.getAwayCountdownSeconds().toLong()
    /** 防抖：检测到被监控 App 离开前台后，延迟确认是否真正进入后台的协程 */
    private var backgroundDebounceJob: Job? = null
    /** 常驻通知刷新协程（每分钟刷新一次，展示今日使用汇总） */
    private var notificationRefreshJob: Job? = null

    /**
     * 锁屏超时协程：息屏后启动，3 分钟内如果用户未回到被监控 App，静默结束会话。
     * 亮屏并回到 App 时取消。
     */
    private var screenOffTimeoutJob: Job? = null

    /**
     * 息屏时被监控 App 的包名（用于亮屏后判断是否需要恢复会话）。
     * 仅在「前台息屏」进入宽限期时赋值；桌面暂停态息屏不走此标记。
     */
    private var screenOffPackage: String? = null

    /**
     * 桌面暂停态下锁屏的包名。
     * 息屏时会收起胶囊；解锁后若会话仍在后台，应还原暂停胶囊（而不是当成息屏宽限把胶囊弄丢）。
     */
    private var lockedWhilePausedPackage: String? = null

    /**
     * 拦截页展示期间息屏的目标包名。
     * 息屏常被 UsageStats 误判成「离开」，不能记守住、不能拆页；解锁后若页已丢则静默重展且不计冲动。
     */
    private var screenOffDuringInterceptPkg: String? = null

    private var enabledPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    /**
     * 锁屏 / 亮屏广播接收器：
     *
     * - ACTION_SCREEN_OFF：
     *     • 拦截页展示中 → 标记假离开，保留拦截（不记守住）
     *     • 已在桌面暂停（含刚切入后台）→ 冻结离开倒计时，保留暂停胶囊
     *     • 仍在 App 内前台息屏 → 息屏宽限；亮屏回到 App 则静默续用
     *
     * - ACTION_SCREEN_ON / ACTION_USER_PRESENT：
     *     • 拦截页息屏：解锁后若页还在则继续；若已丢则静默重展且不计冲动
     *     • 桌面暂停后锁屏：解冻离开倒计时；解锁后还原暂停胶囊（或若已在 App 内则直接续用）
     *     • 息屏宽限：若 App 已在前台则续用；若仍在桌面则先还原暂停胶囊，等待点回
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (overlayManager.isInterceptVisible.get()) {
                        val pkg = overlayManager.interceptTargetPackage
                        if (pkg != null) {
                            screenOffDuringInterceptPkg = pkg
                            Log.d(TAG, "拦截页期间息屏，保留拦截、不记离开 [$pkg]")
                        }
                    }
                    val session = sessionManager.currentSession.value ?: return
                    cancelBackgroundDebounce()
                    if (session.isInBackground) {
                        Log.d(
                            TAG,
                            "屏幕关闭，会话已在桌面暂停，冻结离开倒计时 [${session.packageName}]"
                        )
                        // 含意图门：冻结算倒计时，保留暂停胶囊；纯时长锁本无桌面胶囊
                        awayCountdownFrozen = true
                        lockedWhilePausedPackage = session.packageName
                        screenOffPackage = null
                        cancelScreenOffTimeout()
                    } else {
                        cancelBackgroundTimeout()
                        sessionManager.onAppGoBackground()
                        Log.d(
                            TAG,
                            "屏幕关闭，进入息屏宽限期 [${session.packageName}]，" +
                                "宽限 ${SCREEN_OFF_GRACE_MINUTES} 分钟"
                        )
                        overlayManager.dismissAll()
                        lockedWhilePausedPackage = null
                        screenOffPackage = session.packageName
                        startScreenOffTimeout(session.packageName)
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    if (awayCountdownFrozen) {
                        awayCountdownFrozen = false
                        Log.d(TAG, "屏幕亮起/解锁，解冻离开倒计时")
                    }
                    val interceptLockedPkg = screenOffDuringInterceptPkg
                    if (interceptLockedPkg != null && intent.action == Intent.ACTION_USER_PRESENT) {
                        screenOffDuringInterceptPkg = null
                        if (!overlayManager.isInterceptVisible.get()) {
                            Log.d(
                                TAG,
                                "解锁后拦截页已丢，静默重展且不计冲动 [$interceptLockedPkg]"
                            )
                            serviceScope.launch {
                                showInterceptOverlay(interceptLockedPkg, countImpulse = false)
                            }
                        } else {
                            Log.d(TAG, "解锁后拦截页仍在，不计新冲动 [$interceptLockedPkg]")
                        }
                    }
                    val pausedPkg = lockedWhilePausedPackage
                    if (pausedPkg != null && intent.action == Intent.ACTION_USER_PRESENT) {
                        lockedWhilePausedPackage = null
                        Log.d(TAG, "屏幕解锁，还原暂停态 [$pausedPkg]")
                        serviceScope.launch {
                            restorePausedCapsuleAfterUnlock(pausedPkg)
                        }
                    }
                    val pkg = screenOffPackage
                    if (pkg != null && intent.action == Intent.ACTION_USER_PRESENT) {
                        Log.d(TAG, "屏幕解锁，息屏宽限期内尝试恢复 [$pkg]")
                        serviceScope.launch {
                            tryResumeAfterScreenOff(pkg, reason = "USER_PRESENT")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createSessionEndChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // ── 保活：注册 WorkManager 守护任务（第一层保活兜底）───────────────────
        // 每 15 分钟检测一次服务是否存活，若已被杀则自动重启。
        // 使用 KEEP 策略，多次调用不会重复入队。
        ServiceWatchdogWorker.schedule(this)

        // 用户点击胶囊「结束」后：
        //  HomeAligned → 回桌面（轻条由 OverlayManager 挂）
        //  HomeDrifted → 回桌面（可点轻条再进心锚）
        //  OpenRecord → 打开心锚并定位该条
        //  LegacyUnreviewed → 通知 / 广播（旧路径）
        overlayManager.onManualEndSession = { recordId, mindfulnessLevel, destination ->
            val endedPackage = overlayManager.capsuleAppPackageName.value
            when (destination) {
                ManualEndDestination.HomeAligned,
                ManualEndDestination.HomeDrifted -> {
                    Log.d(
                        TAG,
                        "[ManualEnd] 已回顾 → 回桌面 recordId=$recordId level=$mindfulnessLevel dest=$destination"
                    )
                    pressHomeButton()
                }
                ManualEndDestination.OpenRecord -> {
                    Log.d(TAG, "[ManualEnd] 去心锚定位 recordId=$recordId")
                    openMainActivityForRecord(recordId)
                }
                ManualEndDestination.LegacyUnreviewed -> {
                    when {
                        lastForegroundPackage == packageName -> {
                            Log.d(TAG, "[ManualEnd] 在心锚内结束 → 广播切今日高亮 recordId=$recordId")
                            LocalBroadcastManager.getInstance(this@MonitorForegroundService)
                                .sendBroadcast(
                                    Intent(ACTION_SESSION_ENDED_IN_APP)
                                        .putExtra(EXTRA_NOTE_RECORD_ID, recordId)
                                        .putExtra(EXTRA_SESSION_REVIEWED, false)
                                )
                        }
                        else -> {
                            Log.d(TAG, "[ManualEnd] 未回顾 → 通知可点回今日高亮 recordId=$recordId")
                            sendSessionEndNotification(endedPackage, recordId, reviewed = false)
                        }
                    }
                }
            }
        }

        overlayManager.onLaunchPositiveApp = { targetPackage ->
            Log.d(TAG, "[PositiveDest] 启动正向 App pkg=$targetPackage")
            launchApp(targetPackage)
        }

        overlayManager.onOpenPositiveDestinationSettings = {
            Log.d(TAG, "[PositiveDest] 打开想去的地方配置")
            openPositiveDestinationSettings()
        }

        overlayManager.onExtendSession = { extraMinutes ->
            serviceScope.launch {
                val ok = sessionManager.extendSessionOnce(extraMinutes)
                if (ok) {
                    val renewed = sessionManager.currentSession.value
                    Log.d(TAG, "[Extend] 胶囊续时 +${extraMinutes} 分 ok=${renewed != null}")
                } else {
                    Log.w(TAG, "[Extend] extendSessionOnce 失败")
                }
            }
        }

        serviceScope.launch {
            appLimitRepository.getEnabledAppLimits().collect { limits ->
                val newPackages = limits.filter { it.isEnabled }.map { it.packageName }.toSet()
                val isFirstLoad = enabledPackages.isEmpty() && newPackages.isNotEmpty()
                enabledPackages = newPackages
                Log.d(TAG, "监控列表更新：${enabledPackages.size} 个 App，包含：$enabledPackages")
                if (isFirstLoad && monitorJob == null) {
                    startMonitoring()
                }
            }
        }

        // 常驻通知刷新：每分钟更新一次今日使用汇总
        startNotificationRefreshJob()

        // 注册锁屏/亮屏广播
        // ACTION_SCREEN_OFF / ACTION_USER_PRESENT 只能动态注册，无法在 AndroidManifest 声明
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
        Log.d(TAG, "锁屏/亮屏广播已注册")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob == null || monitorJob?.isActive == false) {
            startMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        notificationRefreshJob?.cancel()
        serviceScope.launch {
            sessionManager.endSession(UsageRecordEntity.EndReason.APP_CLOSED)
        }
        overlayManager.dismissAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (true) {
                try {
                    val currentFg = getForegroundPackage(usageStatsManager)
                    handleForegroundChange(currentFg)
                } catch (e: Exception) {
                    Log.e(TAG, "检测前台 App 出错", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 启动常驻通知刷新协程，每 60 秒更新一次前台服务通知内容。
     * 通知展示今日各受监控 App 的使用时长汇总，让用户在通知栏即可快速了解当日情况。
     */
    private fun startNotificationRefreshJob() {
        notificationRefreshJob?.cancel()
        notificationRefreshJob = serviceScope.launch {
            Log.d(TAG, "[NotifRefresh] 常驻通知刷新协程已启动")
            while (true) {
                try {
                    refreshForegroundNotification()
                } catch (e: Exception) {
                    Log.e(TAG, "[NotifRefresh] 刷新通知出错", e)
                }
                delay(60_000L) // 每分钟刷新一次
            }
        }
    }

    /**
     * 查询今日使用记录，更新前台服务常驻通知内容。
     * 通知正文（展开时）展示今日总时长 + 各 App 时长列表。
     */
    private suspend fun refreshForegroundNotification() {
        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = UsageRecordRepository.getDayRange(now)
        val usageList = usageRecordRepository.getAppTotalByPeriod(dayStart, dayEnd)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val totalSeconds = usageList.sumOf { it.totalSeconds }
        val summaryLine: String
        val bigText: String

        if (usageList.isEmpty()) {
            summaryLine = "今日暂无使用记录"
            bigText = "今日暂无使用记录\n守护进行中，继续保持 🌿"
        } else {
            val totalText = formatDuration(totalSeconds)
            summaryLine = "今日正念时长 $totalText"
            bigText = buildString {
                append("今日正念时长 $totalText\n")
                val sorted = usageList.sortedByDescending { it.totalSeconds }
                sorted.forEach { app ->
                    val name = getAppName(app.packageName)
                    val time = formatDuration(app.totalSeconds)
                    append("• $name  $time\n")
                }
            }.trimEnd()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("时间守护运行中")
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "[NotifRefresh] 常驻通知已更新：$summaryLine")
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0分钟"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 && seconds > 0 -> "${minutes}分${seconds}秒"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }

    /**
     * 通过 UsageEvents 获取当前前台 App 包名，排除自身。
     *
     * ⚠️ 关键设计原则：
     *   只使用**进程级别**的 MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND 事件。
     *   绝对不使用 ACTIVITY_RESUMED / ACTIVITY_PAUSED——它们是 Activity 级别的事件，
     *   App 内部的页面跳转（如微信切换聊天、刷新Feed翻页等）会频繁产生这两个事件，
     *   而且 ACTIVITY_PAUSED（旧页面）和 ACTIVITY_RESUMED（新页面）的顺序在不同
     *   Android 版本上并不固定，极易导致误判"App 已进入后台"。
     *
     * 查询策略：
     *   用足够长的时间窗口（30 分钟）一次性扫描所有 MOVE_TO_FOREGROUND/BACKGROUND 事件，
     *   找到每个包名最后一次进程级事件的状态。若在窗口内完全没有任何进程级事件，
     *   说明 App 进入前台的时间已超过窗口范围，但它从未离开——认为仍在前台。
     */
    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        // 30 分钟的查询窗口，覆盖绝大多数正常使用时长
        val events = usageStatsManager.queryEvents(now - 30 * 60_000L, now)
        val event = android.app.usage.UsageEvents.Event()

        // key: packageName, value: Pair(是否在前台, 最后一次进程级事件的时间戳)
        val processState = mutableMapOf<String, Pair<Boolean, Long>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName) continue  // 排除监控服务自身
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // 进程级：App 进入前台
                    val prev = processState[event.packageName]
                    if (prev == null || event.timeStamp > prev.second) {
                        processState[event.packageName] = Pair(true, event.timeStamp)
                    }
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // 进程级：App 进入后台
                    val prev = processState[event.packageName]
                    if (prev == null || event.timeStamp > prev.second) {
                        processState[event.packageName] = Pair(false, event.timeStamp)
                    }
                }
                // ACTIVITY_RESUMED / ACTIVITY_PAUSED 完全忽略：
                // 它们是 Activity 级别事件，App 内页面切换会产生，与"App 是否在前台"无关
            }
        }

        // 优先：30 分钟内有明确进程级事件，取最后状态为前台的那个
        val detected = processState.entries
            .filter { it.value.first }
            .maxByOrNull { it.value.second }
            ?.key

        if (detected != null) return detected

        // 兜底：30 分钟内没有任何进程级 FOREGROUND 事件
        // 若 lastForegroundPackage 在窗口内也没有 MOVE_TO_BACKGROUND 事件，
        // 说明它进入前台的时间超过了 30 分钟，且从未离开 → 认为仍在前台
        val lastPkg = lastForegroundPackage ?: return null
        val lastPkgState = processState[lastPkg]
        return when {
            // 窗口内有该 App 的进程级事件，且最后是后台 → 已离开
            lastPkgState != null && !lastPkgState.first -> null
            // 窗口内没有任何该 App 的进程级事件（使用时间超过 30 分钟）→ 仍在前台
            lastPkgState == null -> lastPkg
            else -> null
        }
    }

    private suspend fun handleForegroundChange(currentPkg: String?) {
        // 结束确认 / 续时弹窗期间会话会被临时标成后台以冻结计时。
        // 若此处仍走「回前台 → showCapsule」，会拆掉刚弹出的浮层（表现为点结束无反应）。
        if (overlayManager.isCapsuleDialogBlocking.get()) {
            Log.d(TAG, "胶囊结束确认/续时弹窗中，忽略前台切换 $lastForegroundPackage -> $currentPkg")
            return
        }

        val prevPkg = lastForegroundPackage

        if (currentPkg == prevPkg) {
            // 锁屏宽限期内：部分机型 UsageStats 仍报告同一包名（无 FOREGROUND 变化），
            // 若不在此处恢复，会卡在「计时冻结 + 胶囊已dismiss」且最终误走超时结束。
            if (currentPkg != null && screenOffPackage == currentPkg) {
                if (tryResumeAfterScreenOff(currentPkg, reason = "same-pkg-poll")) return
            }
            // 拦截页/超限页正在展示期间，跳过超限检查，避免重复触发
            if (overlayManager.isInterceptVisible.get()) return
            val session = sessionManager.currentSession.value
            if (session != null && !session.isInBackground) {
                // 会话中跨入时段锁：硬踢（优先于日限/单次限）
                val liveLimit = appLimitRepository.getAppLimit(session.packageName)
                if (liveLimit != null && isPeriodHardLocked(liveLimit, session.packageName)) {
                    handlePeriodLock(session.packageName, endActiveSession = true)
                    return
                }
                if (session.isSessionLimitReached) {
                    handleSessionLimitReached(session.packageName)
                } else if (session.isDailyLimitExceeded || session.isWeeklyLimitExceeded) {
                    // 超限续记 session：用户已明确知晓超限并主动选择继续，不再重复弹超限页
                    if (!session.isOverLimitSession) {
                        handleLimitExceeded(session.packageName)
                    }
                }
            }
            return
        }

        // ── 处理新进入前台的 App（优先级最高）────────────────────────────────
        // 若被监控 App 重新回到前台，立即取消防抖计时并恢复会话，避免误触发后台逻辑
        if (currentPkg != null && enabledPackages.contains(currentPkg)) {
            val existingSession = sessionManager.currentSession.value

            // 如果有防抖计时正在等待（即被监控 App 刚离开又马上回来），直接取消，不触发后台
            if (backgroundDebounceJob?.isActive == true) {
                cancelBackgroundDebounce()
                Log.d(TAG, "$currentPkg 快速回到前台，取消后台防抖计时")
            }

            lastForegroundPackage = currentPkg
            Log.d(TAG, "前台切换: $prevPkg -> $currentPkg")

            when {
                // 仅当拦截页就是当前这个 App 时跳过；若用户从 A 的拦截切到 B，必须收口 A 并处理 B
                overlayManager.isInterceptVisible.get() &&
                    overlayManager.interceptTargetPackage == currentPkg -> {
                    Log.d(TAG, "$currentPkg 拦截弹窗正在展示中，跳过本轮")
                }
                existingSession?.packageName == currentPkg && existingSession.isInBackground -> {
                    val limit = appLimitRepository.getAppLimit(currentPkg)
                    if (limit != null && isPeriodHardLocked(limit, currentPkg)) {
                        Log.d(TAG, "$currentPkg 后台恢复时时段锁已生效，硬踢")
                        handlePeriodLock(currentPkg, endActiveSession = true)
                    } else {
                        resumeBackgroundSession(currentPkg, existingSession)
                    }
                }
                existingSession?.packageName == currentPkg && !existingSession.isInBackground -> {
                    Log.d(TAG, "$currentPkg 已在前台运行中，跳过")
                }
                else -> {
                    val limit = appLimitRepository.getAppLimit(currentPkg)
                    if (limit != null && isPeriodHardLocked(limit, currentPkg)) {
                        Log.d(TAG, "$currentPkg 时段锁生效，展示硬挡页")
                        handlePeriodLock(currentPkg, endActiveSession = false)
                    } else if (limit != null && !limit.requireIntentOnOpen) {
                        // 纯时长锁 / 仅时段已过：只关心限额与计时，与中断确认无关
                        if (pendingInterruptStore.get(currentPkg) != null) {
                            pendingInterruptStore.clear(currentPkg)
                            Log.d(TAG, "$currentPkg 意图门关闭，丢弃残留中断确认快照")
                        }
                        Log.d(TAG, "$currentPkg 意图门关闭，尝试无拦截进入")
                        enterWithoutIntentGate(currentPkg, limit.appName)
                    } else {
                        Log.d(TAG, "显示拦截浮窗: $currentPkg，existingSession=$existingSession")
                        showInterceptOverlay(currentPkg)
                    }
                }
            }
            return
        }

        // ── 处理离开的 App（带防抖：避免通知栏/系统弹框等临时遮挡误触发）──────
        // 只有当前有被监控 App 的活跃会话（非后台状态），才需要防抖处理
        // 注意：切换到另一个被监控 App 的情况已在上方优先处理并 return，此处不会到达

        // 特殊情况：拦截/广告/超限覆盖层正在展示，且前台发生了切换。
        //
        // 使用 interceptTargetPackage（OverlayManager 记录的本次覆盖层目标包名）来判断：
        // 只有当前台「从目标包名切走」时，才执行关闭——这精确对应用户按 Home 键的场景。
        //
        // 优点：不依赖 lastForegroundPackage 的历史值，也不需要 enabledPackages 做守卫。
        //   广告结束后展示拦截页，interceptTargetPackage 仍是被监控 App，
        //   用户在桌面不会触发新的"从目标包名切走"事件，拦截页得以保留。
        //   只有用户真正打开了目标 App 再按 Home，才会触发 dismiss。
        //
        //  - isAdPlaying=true  → 广告正在播放，不关闭，保留广告等倒计时结束
        //  - isAdPlaying=false → 普通拦截/超限页，立即关闭
        val interceptTarget = overlayManager.interceptTargetPackage
        if (interceptTarget != null && prevPkg == interceptTarget && overlayManager.isInterceptVisible.get()) {
            if (overlayManager.isAdPlaying.get()) {
                Log.d(TAG, "$interceptTarget 广告播放期间用户按 Home 离开，保留广告页继续播放")
                lastForegroundPackage = currentPkg
                return
            }
            // 息屏常被 UsageStats 报成「切走」：不记守住、不拆拦截
            val screenOffFakeLeave =
                screenOffDuringInterceptPkg == interceptTarget || !isScreenInteractive()
            if (screenOffFakeLeave) {
                if (screenOffDuringInterceptPkg == null) {
                    screenOffDuringInterceptPkg = interceptTarget
                }
                Log.d(TAG, "$interceptTarget 拦截页期间息屏导致的假离开，保留拦截、不记次数")
                lastForegroundPackage = currentPkg
                return
            }
            Log.d(TAG, "$interceptTarget 拦截页展示期间用户按 Home 离开，关闭拦截并走离开肯定")
            // Home / 切走与点「离开」同等：计入门外离开（守住），并走轻量肯定（尊重冷却）
            recordInterceptDismiss(interceptTarget, toOwnApp = false)
            overlayManager.isInterceptVisible.set(false)
            overlayManager.showDismissCeremony(
                packageName = interceptTarget,
                destination = com.life.mindfulnessapp.overlay.DismissDestination.HOME
            ) {
                overlayManager.dismissIntercept()
            }
            lastForegroundPackage = currentPkg
            return
        }

        val session = sessionManager.currentSession.value
        if (prevPkg != null && session != null && session.packageName == prevPkg && !session.isInBackground) {
            // 切换到非被监控 App 或系统 UI（含通知栏、桌面等）
            // 防抖：延迟 BACKGROUND_DEBOUNCE_MS 后再确认是否真正进入后台
            // 如果用户只是拉了下通知栏或系统弹框，很快就会回来，防抖期间不做任何处理
            cancelBackgroundDebounce()
            val debouncePackage = prevPkg
            Log.d(TAG, "$prevPkg 疑似离开前台（切换到 $currentPkg），启动防抖计时 ${BACKGROUND_DEBOUNCE_MS}ms")
            backgroundDebounceJob = serviceScope.launch {
                delay(BACKGROUND_DEBOUNCE_MS)
                // 防抖期满后，再次检查：被监控 App 是否仍然不在前台
                if (lastForegroundPackage != debouncePackage) {
                    val currentSession = sessionManager.currentSession.value
                    if (currentSession != null && currentSession.packageName == debouncePackage && !currentSession.isInBackground) {
                        // 进入后台暂停 + 超时等待。
                        // 注意：不要用 ActivityManager.getRunningAppProcesses() 判断第三方 App
                        // 是否被杀——Android 11+ 该 API 基本只能看到本应用进程，会把「回桌面」
                        // 误判成「进程已死」，导致几秒内胶囊消失、再次进入又全屏拦截。
                        Log.d(TAG, "$debouncePackage 确认进入后台，触发暂停逻辑")
                        handleAppWentBackground(debouncePackage)
                    }
                } else {
                    Log.d(TAG, "$debouncePackage 防抖期间回到前台，取消后台逻辑")
                }
            }
            // 更新 lastForegroundPackage，防止下一轮重复触发防抖
            lastForegroundPackage = currentPkg
            Log.d(TAG, "前台切换: $prevPkg -> $currentPkg（防抖中）")
        } else {
            // 没有活跃会话，或当前包名与会话不符，直接更新前台包名
            lastForegroundPackage = currentPkg
            Log.d(TAG, "前台切换: $prevPkg -> $currentPkg")
        }
    }

    private fun cancelBackgroundDebounce() {
        backgroundDebounceJob?.cancel()
        backgroundDebounceJob = null
    }

    /**
     * 从一个被监控 App 切到另一个时，收口前一会话。
     * 写入 [UsageRecordEntity.EndReason.SWITCHED_AWAY]，下次回到原 App 在意图门区续航；
     * 同时清掉其后台超时与锁屏宽限，避免孤悬协程误伤新会话。
     */
    private suspend fun endSessionForMonitoredAppSwitch(previousPackage: String) {
        cancelBackgroundTimeout()
        cancelScreenOffTimeout()
        awayCountdownRemainingSec = -1L
        awayCountdownFrozen = false
        if (screenOffPackage == previousPackage) {
            screenOffPackage = null
        }
        if (lockedWhilePausedPackage == previousPackage) {
            lockedWhilePausedPackage = null
        }
        sessionManager.endSession(UsageRecordEntity.EndReason.SWITCHED_AWAY)
        overlayManager.dismissCapsule()
    }

    /**
     * 意图门关闭时：若时长已超限则弹超限页，否则直接开会话并显示胶囊。
     */
    private suspend fun enterWithoutIntentGate(packageName: String, appNameHint: String) {
        val existing = sessionManager.currentSession.value
        if (existing != null && existing.packageName != packageName) {
            Log.w(TAG, "enterWithoutIntentGate：从 [${existing.packageName}] 切到 [$packageName]，收口前一会话")
            endSessionForMonitoredAppSwitch(existing.packageName)
        }

        val limit = appLimitRepository.getAppLimit(packageName) ?: return
        if (isPeriodHardLocked(limit, packageName)) {
            handlePeriodLock(packageName, endActiveSession = false)
            return
        }
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            appNameHint.ifBlank { limit.appName }
        }

        if (limit.timeLimitEnabled) {
            val now = System.currentTimeMillis()
            val todayUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val weekUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
            val dailyLimitSeconds = limit.effectiveDailyLimitMinutes() * 60L
            val weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L
            val over = (dailyLimitSeconds > 0 && todayUsed >= dailyLimitSeconds) ||
                (weeklyLimitSeconds > 0 && weekUsed >= weeklyLimitSeconds)
            if (over) {
                Log.d(TAG, "$packageName 意图门关且已超限，展示超限页")
                handleLimitExceeded(packageName)
                return
            }
        }

        val session = sessionManager.startSession(packageName, appName, purpose = null)
        if (session != null) {
            if (lastForegroundPackage == packageName) {
                overlayManager.showCapsule(session)
                Log.d(TAG, "无意图门进入，会话已创建并显示胶囊：$packageName")
            } else {
                Log.d(TAG, "无意图门进入时 App 已不在前台，隐藏胶囊并静默等待：$packageName")
                handleAppWentBackground(packageName, alreadyInBackground = false)
            }
        } else {
            Log.w(TAG, "enterWithoutIntentGate startSession 返回 null：$packageName")
        }
    }

    /** 当前是否应被时段锁硬挡 */
    private fun isPeriodHardLocked(
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity,
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!limit.periodLockEnabled) return false
        val windows = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.decode(limit.periodWindowsJson)
        return com.life.mindfulnessapp.domain.model.PeriodLockPolicy.isLockedNow(
            enabled = true,
            windows = windows,
            nowMillis = nowMillis
        )
    }

    /**
     * 展示时段锁硬挡（无破界入口）。
     * @param endActiveSession 会话中跨入窗口时先收口会话
     */
    private suspend fun handlePeriodLock(packageName: String, endActiveSession: Boolean) {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return
        val windows = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.decode(limit.periodWindowsJson)
        val active = com.life.mindfulnessapp.domain.model.PeriodLockPolicy.activeWindow(windows)
            ?: return

        if (endActiveSession) {
            val session = sessionManager.currentSession.value
            if (session != null && session.packageName == packageName) {
                sessionManager.endSession(UsageRecordEntity.EndReason.LIMIT_REACHED)
                overlayManager.dismissCapsule()
            }
        }

        // 若仍挂着别的拦截层，先拆
        if (overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage != null &&
            overlayManager.interceptTargetPackage != packageName
        ) {
            overlayManager.dismissIntercept()
        }

        overlayManager.showPeriodLock(
            packageName = packageName,
            appName = limit.appName,
            windowLabel = active.label(),
            daysLabel = active.daysLabel(),
            commitment = limit.periodLockCommitment,
            remainingUnlockLabel = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
                .remainingUnlockLabel(active),
            onDismiss = {
                pressHomeButton()
                serviceScope.launch {
                    recordInterceptDismiss(packageName, toOwnApp = false)
                }
            },
            onOpenOwnApp = {
                openMainActivityHome()
                serviceScope.launch {
                    recordInterceptDismiss(packageName, toOwnApp = true)
                }
            }
        )
    }

    private suspend fun showInterceptOverlay(
        packageName: String,
        countImpulse: Boolean = true
    ) {
        val existing = sessionManager.currentSession.value
        if (existing != null && existing.packageName != packageName) {
            Log.w(TAG, "showInterceptOverlay：从 [${existing.packageName}] 切到 [$packageName]，收口前一会话")
            endSessionForMonitoredAppSwitch(existing.packageName)
        }

        // 若仍挂着另一个 App 的拦截层，先拆掉再展示新目标
        if (overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage != null &&
            overlayManager.interceptTargetPackage != packageName
        ) {
            Log.d(
                TAG,
                "关闭旧拦截页 [${overlayManager.interceptTargetPackage}]，准备拦截 [$packageName]"
            )
            overlayManager.interceptTargetPackage?.let { oldPkg ->
                recordInterceptDismiss(oldPkg, toOwnApp = false)
            }
            overlayManager.dismissIntercept()
        }

        val limit = appLimitRepository.getAppLimit(packageName) ?: return

        if (isPeriodHardLocked(limit, packageName)) {
            handlePeriodLock(packageName, endActiveSession = false)
            return
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            limit.appName
        }

        overlayManager.showIntercept(
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = limit.effectiveDailyLimitMinutes(),
            weeklyLimitMinutes = limit.effectiveWeeklyLimitMinutes(),
            countImpulse = countImpulse,
            onContinue = { decision ->
                // onContinue 从主线程（Compose onClick）触发
                serviceScope.launch {
                    val session = sessionManager.startSession(
                        packageName = packageName,
                        appName = appName,
                        purpose = decision.purpose,
                        intentKind = decision.intentKind,
                        sessionLimitMinutes = decision.sessionLimitMinutes
                    )
                    if (session != null) {
                        // 检查：用户在拦截页期间是否已按 Home 键离开（App 不在前台）
                        // 若 lastForegroundPackage 已不是被监控的 App，说明用户已经回到桌面，
                        // 此时不应显示胶囊（或立刻进入后台模式），避免胶囊一闪即灭
                        if (lastForegroundPackage == packageName) {
                            overlayManager.showCapsule(session)
                            Log.d(
                                TAG,
                                "会话已创建（kind=${decision.intentKind}, purpose=${decision.purpose}, " +
                                    "sessionMin=${decision.sessionLimitMinutes}），胶囊已请求显示：$packageName"
                            )
                        } else {
                            // 用户已在拦截页按 Home 离开：按能力分流（意图门倒计时 / 纯锁藏胶囊）
                            Log.d(TAG, "用户在拦截页按 Home 离开后确认意图，App 不在前台：$packageName")
                            overlayManager.isInterceptVisible.set(false)
                            handleAppWentBackground(packageName, alreadyInBackground = false)
                        }
                    } else {
                        overlayManager.isInterceptVisible.set(false)
                        Log.w(TAG, "startSession 返回 null，跳过胶囊显示")
                    }
                }
            },
            onSessionResumed = { session ->
                // 「继续上次」：跳过写意图，直接恢复胶囊计时
                serviceScope.launch {
                    if (lastForegroundPackage == session.packageName) {
                        // 继续上次：跳过进入仪式，直接出胶囊
                        overlayManager.showCapsule(session, playEnterAnimation = false)
                        Log.d(TAG, "中断会话已恢复，胶囊已显示：${session.packageName}，accumulated=${session.accumulatedActiveSeconds}s")
                    } else {
                        Log.d(TAG, "恢复会话时 App 不在前台：${session.packageName}")
                        handleAppWentBackground(session.packageName, alreadyInBackground = false)
                    }
                }
            },
            onDismiss = {
                // 用户在拦截页选择离开：写入一条极短的拦截退出记录
                recordInterceptDismiss(packageName, toOwnApp = false)
                pressHomeButton()
            },
            onOpenOwnApp = {
                recordInterceptDismiss(packageName, toOwnApp = true)
                openMainActivityHome()
            },
            onReset = {
                handleResetLimit(packageName)
            }
        )
    }

    /**
     * 拦截页离开：写入极短克制记录。
     * @param toOwnApp true = 打开心锚；false = 离开回桌面
     */
    private fun recordInterceptDismiss(packageName: String, toOwnApp: Boolean) {
        serviceScope.launch {
            val now = System.currentTimeMillis()
            val reason = if (toOwnApp) {
                UsageRecordEntity.EndReason.GATE_DISMISS_OWN_APP
            } else {
                UsageRecordEntity.EndReason.GATE_DISMISS
            }
            val recordId = usageRecordRepository.insertRecord(
                UsageRecordEntity(
                    packageName = packageName,
                    startTime = now,
                    endTime = now,
                    durationSeconds = 0L,
                    endReason = reason,
                    purpose = null
                )
            )
            android.util.Log.d(
                TAG,
                "拦截退出记录已写入 [id=$recordId, pkg=$packageName, toOwnApp=$toOwnApp]"
            )
        }
    }

    /**
     * 本次会话时长到点：展示收口页（有意图时可对照），出口回心锚并定位该条。
     * 续时已前移到胶囊临近结束时，本页不再提供续时。
     */
    private fun handleSessionLimitReached(packageName: String) {
        if (overlayManager.isInterceptVisible.getAndSet(true)) return
        serviceScope.launch {
            try {
                val session = sessionManager.currentSession.value
                if (session == null || session.packageName != packageName) {
                    overlayManager.isInterceptVisible.set(false)
                    return@launch
                }
                // 读页期间冻结会话计时，避免边读边耗尽
                sessionManager.onAppGoBackground()
                overlayManager.dismissCapsule()

                val appName = session.appName.ifBlank {
                    try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)
                        ).toString()
                    } catch (_: Exception) {
                        appLimitRepository.getAppLimit(packageName)?.appName ?: packageName
                    }
                }
                val committedMinutes = (session.effectiveSessionLimitSeconds / 60L)
                    .toInt()
                    .coerceAtLeast(1)
                val endingRecordId = session.recordId

                overlayManager.showSessionLimitReached(
                    packageName = packageName,
                    appName = appName,
                    purpose = session.purpose,
                    committedMinutes = committedMinutes,
                    onConfirm = { mindfulnessLevel, note ->
                        serviceScope.launch {
                            sessionManager.endSession(
                                reason = UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED,
                                note = note,
                                mindfulnessLevel = mindfulnessLevel
                            )
                            Log.d(
                                TAG,
                                "[$packageName] 单次时长到点 → 回心锚定位 recordId=$endingRecordId" +
                                    " level=$mindfulnessLevel"
                            )
                            openMainActivityForRecord(endingRecordId)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "handleSessionLimitReached 异常", e)
                overlayManager.isInterceptVisible.set(false)
            }
        }
    }

    private fun handleLimitExceeded(packageName: String) {
        // 防止监控循环每秒重复触发：用 isInterceptVisible 作为叠加层针，
        // 同时防止 handleResetLimit 完成后新 session 也被错误判定为超限
        if (overlayManager.isInterceptVisible.getAndSet(true)) {
            // 已经在展示拦截页/超限页或正在执行 reset，跳过本次调用
            return
        }
        serviceScope.launch {
            try {
                // 超限续记需保留原意图：先快照再 endSession
                val prior = sessionManager.currentSession.value
                val priorPurpose = prior?.purpose
                val priorIntentKind = prior?.intentKind

                sessionManager.endSession(UsageRecordEntity.EndReason.LIMIT_REACHED)
                overlayManager.dismissCapsule()

                // 提前获取 appName，供超限续记 session 使用
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    appLimitRepository.getAppLimit(packageName)?.appName ?: packageName
                }

                overlayManager.showLimitReached(
                    packageName = packageName,
                    onDismiss = {
                        // 只有当被监控的 App 仍在前台时，才需要按 Home 键把用户"推出去"。
                        // 如果用户已经切换到其他 App，直接关闭弹框即可，不应强制跳回桌面。
                        // isInterceptVisible 在 OverlayManager.showLimitReached 的 onDismiss 包装里会被正确清除
                        if (lastForegroundPackage == packageName) {
                            pressHomeButton()
                        }
                    },
                    onOpenOwnApp = {
                        openMainActivityHome()
                    },
                    onReset = {
                        handleResetLimit(packageName)
                    },
                    onContinueOverLimit = {
                        // 用户明确点击「我知道超了，继续使用」：
                        // 开启超限续记 session，后续时长照常记录，不再弹超限页；保留原意图叙事。
                        serviceScope.launch {
                            val overSession = sessionManager.startOverLimitSession(
                                packageName = packageName,
                                appName = appName,
                                purpose = priorPurpose,
                                intentKind = priorIntentKind
                            )
                            if (overSession != null) {
                                overlayManager.showCapsule(overSession)
                                Log.d(TAG, "[$packageName] 用户主动选择超限继续使用，续记 session 已开启 [id=${overSession.recordId}]")
                            } else {
                                Log.w(TAG, "[$packageName] startOverLimitSession 返回 null")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "handleLimitExceeded 出现异常", e)
                overlayManager.isInterceptVisible.set(false)
            }
        }
    }

    /**
     * 用户点击「重新设定今日目标」后的处理：
     * 1. 结束当前会话
     * 2. 关闭超限浮窗（由 OverlayManager.onReset 包装已完成）
     * 3. 发 Intent 打开 MainActivity，导航到该 App 的监控配置页
     */
    private fun handleResetLimit(packageName: String) {
        Log.d(TAG, "[Reset] 用户点击重新设定，跳转到设置页: pkg=$packageName")
        // isInterceptVisible 已由 OverlayManager 的 onReset 包装设为 false，
        // 这里不需要再修改，防止监控循环重新触发由正常逻辑保障（session 已结束）
        serviceScope.launch {
            try {
                // 结束当前会话（如果有的话），避免旧 session 数据污染
                sessionManager.endSession(UsageRecordEntity.EndReason.LIMIT_REACHED)
            } catch (e: Exception) {
                Log.w(TAG, "[Reset] endSession 异常（可忽略）", e)
            }
            // 发 Intent 打开 App 设置页
            val intent = Intent(this@MonitorForegroundService, MainActivity::class.java).apply {
                action = ACTION_OPEN_APP_LIMIT_EDIT
                putExtra(EXTRA_APP_PACKAGE_NAME, packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            Log.d(TAG, "[Reset] Intent 已发送，等待用户在设置页修改限制")
        }
    }

    /**
     * App 确认离开前台后的分流：
     * - 含意图门：桌面可见暂停胶囊 + 离开倒计时，归零按中断收口（可续）；锁屏冻结算秒
     * - 仅时长锁：立刻藏胶囊（仅前台显示）；会话暂停，超时后静默收口
     *
     * @param alreadyInBackground 调用前是否已执行过 onAppGoBackground
     */
    private fun handleAppWentBackground(
        packageName: String,
        alreadyInBackground: Boolean = false
    ) {
        val sessionBefore = sessionManager.currentSession.value
        if (sessionBefore == null || sessionBefore.packageName != packageName) return
        if (!alreadyInBackground && !sessionBefore.isInBackground) {
            sessionManager.onAppGoBackground()
        }
        val session = sessionManager.currentSession.value ?: return
        if (session.packageName != packageName) return

        cancelBackgroundTimeout()
        awayCountdownFrozen = false

        if (session.hasIntentGate) {
            Log.d(TAG, "$packageName 含意图门：展示暂停胶囊 + ${configuredAwayCountdownSec()}s 离开倒计时")
            val remain = if (awayCountdownRemainingSec > 0L) awayCountdownRemainingSec else configuredAwayCountdownSec()
            awayCountdownRemainingSec = remain
            overlayManager.showPausedCapsule(
                session = session,
                returnToAppAction = { launchApp(packageName) },
                awayCountdownSeconds = remain
            )
            startAwayCountdown(packageName)
        } else {
            Log.d(TAG, "$packageName 仅时长锁：藏起胶囊，静默等待收口")
            awayCountdownRemainingSec = -1L
            overlayManager.dismissCapsule()
            startSilentBackgroundEnd(packageName)
        }
    }

    /** 含意图门：可见离开倒计时；锁屏时 awayCountdownFrozen=true 不递减 */
    private fun startAwayCountdown(packageName: String) {
        cancelBackgroundTimeout()
        if (awayCountdownRemainingSec <= 0L) {
            awayCountdownRemainingSec = configuredAwayCountdownSec()
        }
        // 用 elapsedRealtime 墙钟，避免协程调度抖动导致 UI 停更；冻结时平移 deadline
        var deadlineElapsedMs =
            SystemClock.elapsedRealtime() + awayCountdownRemainingSec * 1000L
        var wasFrozen = awayCountdownFrozen
        overlayManager.updateAwayCountdown(awayCountdownRemainingSec)
        backgroundTimeoutJob = serviceScope.launch {
            Log.d(TAG, "$packageName 离开倒计时开始 remaining=${awayCountdownRemainingSec}s")
            while (isActive && awayCountdownRemainingSec > 0L) {
                delay(250L)
                val session = sessionManager.currentSession.value
                if (session == null || session.packageName != packageName || !session.isInBackground) {
                    Log.d(TAG, "$packageName 离开倒计时取消（已回前台或会话结束）")
                    return@launch
                }
                val frozen = awayCountdownFrozen
                if (frozen && !wasFrozen) {
                    // 刚冻结：把剩余秒钉住
                    awayCountdownRemainingSec =
                        ((deadlineElapsedMs - SystemClock.elapsedRealtime() + 999L) / 1000L)
                            .coerceAtLeast(0L)
                    overlayManager.updateAwayCountdown(awayCountdownRemainingSec)
                } else if (!frozen && wasFrozen) {
                    // 刚解冻：按钉住的剩余重设 deadline
                    deadlineElapsedMs =
                        SystemClock.elapsedRealtime() + awayCountdownRemainingSec * 1000L
                }
                wasFrozen = frozen
                if (frozen) continue

                val remain =
                    ((deadlineElapsedMs - SystemClock.elapsedRealtime() + 999L) / 1000L)
                        .coerceAtLeast(0L)
                if (remain != awayCountdownRemainingSec) {
                    awayCountdownRemainingSec = remain
                    overlayManager.updateAwayCountdown(remain)
                }
            }
            val session = sessionManager.currentSession.value
            if (session == null || session.packageName != packageName || !session.isInBackground) {
                return@launch
            }
            if (awayCountdownRemainingSec > 0L) return@launch
            Log.d(TAG, "$packageName 离开倒计时归零，按中断收口（可续）")
            val endingRecordId = session.recordId
            overlayManager.capsuleAppPackageName.value = packageName
            sessionManager.endSession(UsageRecordEntity.EndReason.AWAY_COUNTDOWN)
            awayCountdownRemainingSec = -1L
            overlayManager.dismissCapsule()
            // 中断语义：发「可续」通知，不走手动结束回调（避免「计时已结束 ✓」）
            sendInterruptEndNotification(packageName, endingRecordId)
        }
    }

    /** 仅时长锁：无桌面胶囊，超时后静默结束（不写中断确认） */
    private fun startSilentBackgroundEnd(packageName: String) {
        cancelBackgroundTimeout()
        backgroundTimeoutJob = serviceScope.launch {
            val waitMs = configuredAwayCountdownSec() * 1000L
            Log.d(TAG, "$packageName 纯时长锁静默收口计时 ${waitMs}ms")
            delay(waitMs)
            val session = sessionManager.currentSession.value
            if (session == null || session.packageName != packageName || !session.isInBackground) {
                return@launch
            }
            Log.d(TAG, "$packageName 纯时长锁静默结束会话")
            sessionManager.endSession(UsageRecordEntity.EndReason.BACKGROUND_TIMEOUT)
            overlayManager.dismissCapsule()
        }
    }

    private fun cancelBackgroundTimeout() {
        backgroundTimeoutJob?.cancel()
        backgroundTimeoutJob = null
    }

    /**
     * 锁屏宽限期内尝试静默恢复会话。
     *
     * @return true 表示已成功恢复（或确认无需再处理宽限状态）
     */
    private fun tryResumeAfterScreenOff(packageName: String, reason: String): Boolean {
        if (screenOffPackage != packageName) return false
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName) {
            Log.w(TAG, "[$packageName] 宽限恢复失败（无会话），reason=$reason")
            screenOffPackage = null
            cancelScreenOffTimeout()
            return false
        }
        if (!session.isInBackground) {
            cancelScreenOffTimeout()
            screenOffPackage = null
            return true
        }

        if (reason == "USER_PRESENT") {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentFg = getForegroundPackage(usageStatsManager)
            if (currentFg != packageName) {
                Log.d(TAG, "[$packageName] 解锁后尚未回到 App（当前前台=$currentFg），按能力还原暂停态")
                showPausedCapsule(session)
                return false
            }
            lastForegroundPackage = packageName
        }

        resumeBackgroundSession(packageName, session)
        Log.d(TAG, "[$packageName] 锁屏宽限内静默恢复，reason=$reason")
        return true
    }

    /**
     * 桌面暂停态锁屏后解锁：若仍在后台则还原暂停态；若已在 App 内则直接续用。
     */
    private fun restorePausedCapsuleAfterUnlock(packageName: String) {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName || !session.isInBackground) {
            Log.d(TAG, "[$packageName] 解锁后无需还原暂停态（session=${session?.packageName}, bg=${session?.isInBackground}）")
            return
        }
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentFg = getForegroundPackage(usageStatsManager)
        if (currentFg == packageName) {
            lastForegroundPackage = packageName
            resumeBackgroundSession(packageName, session)
            Log.d(TAG, "[$packageName] 解锁后已在 App 内，直接续用")
            return
        }
        Log.d(TAG, "[$packageName] 解锁后仍在桌面，还原暂停态")
        showPausedCapsule(session)
    }

    /**
     * 展示切走后的暂停态：
     * - 含意图门：暂停胶囊 + 剩余离开倒计时
     * - 仅时长锁：保持藏起
     */
    private fun showPausedCapsule(session: UsageSession) {
        val packageName = session.packageName
        if (!session.hasIntentGate) {
            overlayManager.dismissCapsule()
            if (backgroundTimeoutJob?.isActive != true) {
                startSilentBackgroundEnd(packageName)
            }
            return
        }
        val remain = if (awayCountdownRemainingSec > 0L) awayCountdownRemainingSec else configuredAwayCountdownSec()
        awayCountdownRemainingSec = remain
        overlayManager.showPausedCapsule(
            session = session,
            returnToAppAction = { launchApp(packageName) },
            awayCountdownSeconds = remain
        )
        if (backgroundTimeoutJob?.isActive != true) {
            startAwayCountdown(packageName)
        } else {
            overlayManager.updateAwayCountdown(remain)
        }
    }

    /** 从后台/锁屏冻结态恢复计时并重新展示胶囊（不重走拦截）。 */
    private fun resumeBackgroundSession(
        packageName: String,
        existingSession: UsageSession
    ) {
        val isFromScreenOff = screenOffPackage == packageName
        sessionManager.onAppReturnToForeground()
        cancelBackgroundTimeout()
        cancelScreenOffTimeout()
        screenOffPackage = null
        lockedWhilePausedPackage = null
        awayCountdownFrozen = false
        awayCountdownRemainingSec = -1L
        overlayManager.resumeCapsule()
        val restoredSession = sessionManager.currentSession.value ?: existingSession
        overlayManager.showCapsule(restoredSession, playEnterAnimation = false)
        if (isFromScreenOff) {
            Log.d(
                TAG,
                "$packageName 锁屏后回来（宽限期内），恢复计时，" +
                    "accumulated=${restoredSession.accumulatedActiveSeconds}s"
            )
        } else {
            Log.d(
                TAG,
                "$packageName 从后台回来，继续计时，" +
                    "accumulated=${restoredSession.accumulatedActiveSeconds}s"
            )
        }
    }

    /**
     * 启动锁屏宽限超时协程。
     * 息屏后 [SCREEN_OFF_GRACE_MS]（3 分钟）内若用户未回到被监控 App，静默结束会话。
     * 用户回到 App 后应调用 [cancelScreenOffTimeout] 取消。
     */
    private fun startScreenOffTimeout(packageName: String) {
        cancelScreenOffTimeout()
        screenOffTimeoutJob = serviceScope.launch {
            Log.d(TAG, "[$packageName] 锁屏宽限计时启动，${SCREEN_OFF_GRACE_MINUTES} 分钟后超时")
            delay(SCREEN_OFF_GRACE_MS)
            // 宽限期到：检查是否仍有该 App 的后台会话
            val session = sessionManager.currentSession.value
            if (session != null && session.packageName == packageName && session.isInBackground) {
                Log.d(TAG, "[$packageName] 锁屏宽限期超时，静默结束会话")
                sessionManager.endSession(UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT)
                overlayManager.dismissAll()
            }
            if (screenOffPackage == packageName) {
                screenOffPackage = null
            }
            if (lockedWhilePausedPackage == packageName) {
                lockedWhilePausedPackage = null
            }
        }
    }

    private fun cancelScreenOffTimeout() {
        screenOffTimeoutJob?.cancel()
        screenOffTimeoutJob = null
    }

    /**
     * 将已有任务整栈拉回前台（模拟点桌面图标），尽量回到离开时的页面。
     * 不要只用 LAUNCHER Activity + REORDER_TO_FRONT：那常会重开入口页，丢掉用户刚才的界面。
     */
    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // setPackage(null) + RESET_TASK_IF_NEEDED：与系统桌面启动一致，优先恢复既有 task
                launchIntent.setPackage(null)
                launchIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                startActivity(launchIntent)
                Log.d(TAG, "成功拉起App（恢复任务栈）: $packageName")
            } else {
                Log.w(TAG, "无法获取 $packageName 的启动Intent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "拉起App失败: $packageName", e)
        }
    }

    private fun pressHomeButton() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    /** 打开心锚首页（拦截/超限页「去做点别的」） */
    private fun openMainActivityHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_HOME
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /** 打开「想去的地方」配置页 */
    private fun openPositiveDestinationSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_POSITIVE_DESTINATIONS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /** 打开心锚今日页并高亮指定记录（补备注） */
    private fun openMainActivityForRecord(recordId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTE
            putExtra(EXTRA_NOTE_RECORD_ID, recordId)
            putExtra(EXTRA_SESSION_REVIEWED, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * 离开倒计时等中断收口后的轻量通知：强调可续，而非「已完成」。
     */
    private fun sendInterruptEndNotification(endedPackage: String, recordId: Long) {
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(endedPackage, 0)
            ).toString()
        } catch (e: Exception) {
            endedPackage.substringAfterLast(".")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            SESSION_END_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_RECORD_ID, recordId)
                putExtra(EXTRA_SESSION_REVIEWED, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setContentTitle("计时已暂停结束")
            .setContentText("$appName · 10 分钟内可继续上次")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    /**
     * 发送「会话已结束」轻量通知。
     * 手动结束不强制跳心锚；用通知确认，点击后再进今日高亮。
     *
     * @param endedPackage 被结束会话的 App 包名
     * @param recordId 刚结束的记录 ID，供通知 deep link 高亮
     * @param reviewed 是否已在结束流程中完成意图回顾
     */
    private fun sendSessionEndNotification(
        endedPackage: String,
        recordId: Long,
        reviewed: Boolean = false
    ) {
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(endedPackage, 0)
            ).toString()
        } catch (e: Exception) {
            endedPackage.substringAfterLast(".")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            SESSION_END_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_RECORD_ID, recordId)
                putExtra(EXTRA_SESSION_REVIEWED, reviewed)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (reviewed) {
            "$appName · 对照已保存"
        } else {
            "$appName · 点此回看"
        }

        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setContentTitle("计时已结束 ✓")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    // -------- 通知相关 --------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "使用时长监控服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于监控 App 使用时长的后台服务"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * 创建「会话结束」轻量通知渠道。
     * 用于用户在第三方 App 内手动结束计时时，发出静默确认通知。
     * 使用最低重要性（不弹出、不响铃），仅在通知抽屉可见。
     */
    private fun createSessionEndChannel() {
        val channel = NotificationChannel(
            SESSION_END_CHANNEL_ID,
            "计时结束提醒",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "手动结束或离开倒计时中断后的静默确认通知"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("时间守护运行中")
            .setContentText("正在守护你的注意力")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
