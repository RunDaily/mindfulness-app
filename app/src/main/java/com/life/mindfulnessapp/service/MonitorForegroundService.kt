package com.life.mindfulnessapp.service

import android.app.KeyguardManager
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
import com.life.mindfulnessapp.DualSpaceGateActivity
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.PlanAddAppActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.TodayReceiptActivity
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.analytics.AnalyticsBuckets
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.DualAppRegistry
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkLauncher
import com.life.mindfulnessapp.domain.model.BrowseCasualCooldown
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CompanionScene
import com.life.mindfulnessapp.domain.model.DailyCapFacts
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.IntentLandMode
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.overlay.InterceptOverlayKind
import com.life.mindfulnessapp.overlay.SoftDoorChoice
import com.life.mindfulnessapp.overlay.ManualEndDestination
import com.life.mindfulnessapp.overlay.OverlayManager
import com.life.mindfulnessapp.util.BackgroundMediaPauser
import com.life.mindfulnessapp.util.KeepAliveAlarmScheduler
import com.life.mindfulnessapp.util.OemDualSpace
import com.life.mindfulnessapp.util.QuotePushScheduler
import com.life.mindfulnessapp.util.MonitorHealthStore
import com.life.mindfulnessapp.util.PackageProcessLiveness
import com.life.mindfulnessapp.util.RecentsHider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
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
        /** Intent extra：打开心锚后自动弹出对照编辑 */
        const val EXTRA_OPEN_COMPARE = "extra_open_compare"
        /** 打开监控配置页的 Intent Action */
        const val ACTION_OPEN_APP_LIMIT_EDIT = "ACTION_OPEN_APP_LIMIT_EDIT"
        /** 打开快捷标签管理 */
        const val ACTION_OPEN_QUICK_INTENT_TAGS = "ACTION_OPEN_QUICK_INTENT_TAGS"
        /** Intent extra key：要编辑限制的 App 包名 */
        const val EXTRA_APP_PACKAGE_NAME = "extra_app_package_name"
        /** Intent extra：App 显示名（可选） */
        const val EXTRA_APP_NAME = "extra_app_name"
        /**
         * Intent extra：打开单能力配置（[CapabilityKind.name]）。
         * 有值时走能力编辑页，否则走 App 详情。
         */
        const val EXTRA_OPEN_CAPABILITY = "extra_open_capability"
        /** 打开「想去的地方」配置页 */
        const val ACTION_OPEN_POSITIVE_DESTINATIONS = "ACTION_OPEN_POSITIVE_DESTINATIONS"
        /** 打开某 App 的使用记录页（离开轻条「详细」） */
        const val ACTION_OPEN_APP_HISTORY = "ACTION_OPEN_APP_HISTORY"
        /** 打开意见反馈页并展示某条开发者回复 */
        const val ACTION_OPEN_FEEDBACK_REPLY = "ACTION_OPEN_FEEDBACK_REPLY"
        /** 打开日程表 Tab */
        const val ACTION_OPEN_SCHEDULE = "ACTION_OPEN_SCHEDULE"
        /** Intent extra：反馈 id */
        const val EXTRA_FEEDBACK_ID = "extra_feedback_id"
        /**
         * LocalBroadcast Action：用户在 Anchor App 内手动结束会话时发送。
         * MainActivity 收到后显示 Snackbar 轻提示。
         */
        const val ACTION_SESSION_ENDED_IN_APP = "com.life.mindfulnessapp.SESSION_ENDED_IN_APP"
        /** 会话结束通知的渠道 ID */
        const val SESSION_END_CHANNEL_ID = "session_end_notify"
        /** 会话结束通知 ID */
        const val SESSION_END_NOTIFICATION_ID = 3001
        /**
         * UsageStats 轮询间隔。部分 OEM 上 MOVE_TO_FOREGROUND 会滞后到首次触摸，
         * 故另用 ACTIVITY_RESUMED / 无障碍窗口切换做即时提示；轮询作兜底。
         */
        const val POLL_INTERVAL_MS = 300L
        /** 含意图门：离开倒计时默认秒数（实际以 AppPreferences 为准） */
        const val DEFAULT_AWAY_COUNTDOWN_SEC =
            AppPreferences.DEFAULT_AWAY_COUNTDOWN_SECONDS.toLong()
        /**
         * 进程已死（划掉 / 强停）后弹出离开横条前的固定等待秒数。
         * 会话会立刻 [APP_CLOSED] 收口（避免「杀进程后马上重开」被当成无缝恢复）；
         * 横条仍稍晚弹出，且与设置里的离开倒计时（60/120/300）无关。
         */
        const val PROCESS_GONE_AWAY_COUNTDOWN_SEC = 3L
        /**
         * 后台切换防抖延迟（毫秒）。
         * 用于过滤通知栏下拉、系统弹框等导致的短暂"离开前台"误判。
         * 设置为 1500ms：通知栏操作通常 < 1s，真正切到桌面/其他 App 则持续较长时间。
         */
        const val BACKGROUND_DEBOUNCE_MS = 1500L
        /**
         * 刚进后台又回来：多半是音量条 / 通知栏噪声。
         * 比 [BACKGROUND_DEBOUNCE_MS] 宽一截，盖住「防抖期满 → 进后台 → UsageStats 又报回 App」整段。
         */
        const val BRIEF_BACKGROUND_RESUME_MS = 4_000L
        /** 回桌面后播放器切后台播放常有一拍空窗，停播确认后再静默离开倒计时 */
        const val MUSIC_AWAY_QUIET_GRACE_MS = 1_200L

        /** 进程内真实存活标记（供设置/「我」页展示） */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 供无障碍服务即时推送前台包名（弱引用，避免泄漏） */
        @Volatile
        private var runningInstance: MonitorForegroundService? = null

        fun markRunning(running: Boolean) {
            isRunning = running
        }

        /**
         * 无障碍 [TYPE_WINDOW_STATE_CHANGED] 回调：立刻探测前台，避免等 UsageStats / 触摸。
         * 与 AppBlock 类似，窗口一切换就响应。
         * 对需开门的包：同步先盖占位层，再异步走完整判定（对齐「拦截先于 App 露脸」）。
         */
        fun notifyForegroundHint(packageName: String?) {
            val svc = runningInstance ?: return
            if (packageName.isNullOrBlank()) return
            svc.tryEagerInterceptCover(packageName)
            svc.serviceScope.launch {
                svc.handleForegroundChange(packageName, fromAccessibility = true)
            }
        }

        /** 无障碍看到最近任务窗口：卸拦截层，露出系统进程列表。 */
        fun notifyRecentsOpened() {
            val svc = runningInstance ?: return
            svc.onRecentsOpenedFromAccessibility()
        }

        /** 无障碍观察到 Home 键（拦截页期间）；悬浮窗收不到这键。 */
        fun notifyHomePressedDuringIntercept() {
            val svc = runningInstance ?: return
            svc.onSystemHomePressedDuringIntercept()
        }

        /** 无障碍观察到返回键（拦截页期间）；部分机型悬浮窗收不到。 */
        fun notifyBackPressedDuringIntercept() {
            val svc = runningInstance ?: return
            svc.onSystemBackPressedDuringIntercept()
        }

        fun hasActiveInterceptForRecentsHint(): Boolean {
            val svc = runningInstance ?: return false
            return svc.overlayManager.interceptTargetPackage != null &&
                (svc.overlayManager.isInterceptLayerOnScreen() ||
                    svc.overlayManager.interceptParkedForRecents)
        }

        /**
         * 立刻用 UsageStats 再探一次前台（无障碍窗口变化 / 拦截层触摸恢复时调用）。
         * @param hintPackage 已知的目标包名（如拦截目标），优先于滞后的 UsageStats。
         */
        fun requestImmediateForegroundCheck(hintPackage: String? = null) {
            val svc = runningInstance ?: return
            if (!hintPackage.isNullOrBlank()) {
                svc.tryEagerInterceptCover(hintPackage)
            }
            svc.serviceScope.launch {
                try {
                    val usm =
                        svc.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val fromStats = svc.getForegroundPackage(usm)
                    // 音量面板等：UsageStats 常短暂报 systemui，当作没切走
                    if (svc.isSystemUiPackage(fromStats) ||
                        svc.isTransientSystemOverlayPackage(fromStats)
                    ) {
                        return@launch
                    }
                    val interceptTarget = svc.overlayManager.interceptTargetPackage
                        ?: svc.overlayManager.gateEnteringPackage
                    if (interceptTarget != null &&
                        svc.overlayManager.isInterceptLayerOnScreen(interceptTarget)
                    ) {
                        // 拦截盖住时 UsageStats 常仍报目标 App；优先探测 Home/后台
                        if (fromStats == null || fromStats == interceptTarget) {
                            svc.handleForegroundChange(fromStats ?: interceptTarget)
                            return@launch
                        }
                    }
                    val interceptVisible = svc.overlayManager.isInterceptLayerOnScreen()
                    val resolved = when {
                        interceptVisible && fromStats != null -> fromStats
                        hintPackage != null &&
                            svc.enabledPackages.contains(
                                svc.dualAppRegistry.canonicalize(hintPackage)
                            ) -> hintPackage
                        fromStats != null -> fromStats
                        hintPackage != null -> hintPackage
                        else -> null
                    }
                    // 音量条等：UsageStats 常短暂读空，勿当成离开去跑防抖 / 重弹门
                    if (resolved == null) return@launch
                    if (resolved != hintPackage) {
                        svc.tryEagerInterceptCover(resolved)
                    }
                    svc.handleForegroundChange(resolved)
                } catch (e: Exception) {
                    Log.w(TAG, "即时前台探测失败", e)
                }
            }
        }
        /**
         * 前台使用中息屏后的宽限秒数与 [AppPreferences.awayCountdownSeconds]（暂停恢复时长）一致。
         * 宽限内亮屏并回到被监控 App → 静默续用，不重新弹拦截页，息屏期间不计入使用时长。
         * 超过宽限未回到该 App → 静默结束会话，下次进入在意图门区提供「继续上次」。
         */
        /**
         * 拦截页「疑似离开」防抖：UsageStats 常比 SCREEN_OFF 更早报切走，
         * 过短会在自动息屏/屏保时误走「守住了」。
         */
        const val INTERCEPT_LEAVE_DEBOUNCE_MS = 900L
        /** 解锁后短时内仍把「切到桌面」当成息屏余波，不记离开、不弹横条 */
        const val INTERCEPT_UNLOCK_GUARD_MS = 3_000L
        /**
         * 拦截页刚挂上后的保护窗：冷启动 / 从桌面点开时，UsageStats 常滞后上报桌面 RESUMED，
         * 过短会把「门刚起来」误判成 Home 离开并弹守住横条。
         *
         * 全屏可聚焦拦截层一挂上，目标 App 常立刻写出 [MOVE_TO_BACKGROUND]；
         * 保护窗内的这类事件一律不算 Home。
         */
        const val INTERCEPT_SHOW_GUARD_MS = 2_500L
        /**
         * 拦截页挂稳后，认「新的桌面进入」的最短间隔。
         * 比 [INTERCEPT_SHOW_GUARD_MS] 短：开场误报用 openTransition 挡；
         * 真按 Home 时 UsageStats/无障碍要尽快卸层（悬浮窗盖在桌面上，不卸就看不见桌面）。
         */
        const val INTERCEPT_HOME_EVENT_SETTLE_MS = 600L
        /**
         * 刚卸层进进程列表的保护窗：UsageStats 仍报目标 App，不能把门盖回概览上。
         * 过后再由无障碍「点回该 App」重挂门。绝不能靠超时按 Home。
         */
        const val RECENTS_PARK_HOLD_MS = 420L
        /** 部分 ROM 方块键会紧跟一个假 HOME，这段时间内忽略 Home */
        const val RECENTS_HOME_KEY_IGNORE_MS = 1_200L
        /** 失焦后探底分类最长等待 */
        const val INTERCEPT_FOCUS_PROBE_MS = 900L

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
    @Inject lateinit var analyticsRepository: AnalyticsRepository
    @Inject lateinit var feedbackInboxRepository: com.life.mindfulnessapp.data.repository.FeedbackInboxRepository
    @Inject lateinit var walkAwarenessCoordinator: WalkAwarenessCoordinator
    @Inject lateinit var dualAppRegistry: DualAppRegistry
    @Inject lateinit var planBlockRepository: PlanBlockRepository
    @Inject lateinit var rulePlanStore: com.life.mindfulnessapp.data.plan.RulePlanStore
    @Inject lateinit var systemUsageRepository: com.life.mindfulnessapp.data.repository.SystemUsageRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var backgroundTimeoutJob: Job? = null
    /** 监控 App 互切时挂起会话的到期落库协程 */
    private var parkedExpiryJob: Job? = null
    /** 含意图门离开倒计时剩余秒（暂停态锁屏期间同样流逝） */
    private var awayCountdownRemainingSec: Long = -1L
    /** 离开倒计时墙钟截止（elapsedRealtime）；锁屏/Doze 下仍准确 */
    private var awayCountdownDeadlineElapsedMs: Long = -1L

    /** 当前配置的离开倒计时秒数（意图门暂停胶囊 / 前台息屏宽限 / 纯锁静默等待） */
    private fun configuredAwayCountdownSec(): Long =
        appPreferences.getAwayCountdownSeconds().toLong()

    private fun configuredScreenOffGraceMs(): Long =
        configuredAwayCountdownSec() * 1000L

    private fun isAwayCountdownExpired(): Boolean {
        val deadline = awayCountdownDeadlineElapsedMs
        return deadline > 0L && SystemClock.elapsedRealtime() >= deadline
    }

    /**
     * 离开宽限还没归零：点暂停条回来是同一次，不再过门。
     * 必须已有墙钟 deadline；听歌离开清掉 deadline 后不算宽限（走 [isMusicAwayHold]）。
     */
    private fun isAwayGraceHolding(packageName: String): Boolean {
        val session = sessionManager.currentSession.value ?: return false
        if (session.packageName != packageName || !session.isInBackground) return false
        if (awayCountdownDeadlineElapsedMs <= 0L) return false
        if (isAwayCountdownExpired()) return false
        return true
    }

    private fun syncAwayCountdownRemainingFromDeadline() {
        val deadline = awayCountdownDeadlineElapsedMs
        if (deadline <= 0L) return
        awayCountdownRemainingSec =
            ((deadline - SystemClock.elapsedRealtime() + 999L) / 1000L).coerceAtLeast(0L)
    }

    private fun armAwayCountdownDeadline(remainingSec: Long) {
        val remain = remainingSec.coerceAtLeast(0L)
        awayCountdownRemainingSec = remain
        // remain=0 时 deadline 标成「已到期」，勿清成 -1，否则会误当成未武装又重开一轮
        awayCountdownDeadlineElapsedMs =
            if (remain > 0L) {
                SystemClock.elapsedRealtime() + remain * 1000L
            } else {
                SystemClock.elapsedRealtime()
            }
    }

    private fun clearAwayCountdownState() {
        awayCountdownRemainingSec = -1L
        awayCountdownDeadlineElapsedMs = -1L
    }

    private fun markSessionBackgroundNow() {
        sessionBackgroundAtElapsed = SystemClock.elapsedRealtime()
        sessionBackgroundAtWallMs = System.currentTimeMillis()
    }

    private fun clearSessionBackgroundMark() {
        sessionBackgroundAtElapsed = -1L
        sessionBackgroundAtWallMs = -1L
        leftToLauncherWhileAway = false
    }

    /**
     * 桌面暂停后 UsageStats 常误报目标 App 仍在前台。
     * - 进后台后若桌面进入不早于目标最新前台 → 假回报
     * - 经桌面离开后，若没有后台之后的目标新前台事件 → 也视为假回报
     */
    private fun isSpuriousAppForegroundWhileAway(packageName: String): Boolean {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName || !session.isInBackground) {
            return false
        }
        val sinceWall = sessionBackgroundAtWallMs
        if (sinceWall <= 0L) return leftToLauncherWhileAway
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return leftToLauncherWhileAway
        val now = System.currentTimeMillis()
        if (now <= sinceWall) return leftToLauncherWhileAway
        val events = usm.queryEvents(sinceWall, now)
        val event = android.app.usage.UsageEvents.Event()
        var latestLauncher = 0L
        var latestTarget = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (event.timeStamp < sinceWall) continue
            val isFg =
                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            if (!isFg) continue
            when {
                isLauncherPackage(pkg) ->
                    latestLauncher = maxOf(latestLauncher, event.timeStamp)
                pkg == packageName ->
                    latestTarget = maxOf(latestTarget, event.timeStamp)
            }
        }
        if (leftToLauncherWhileAway && latestTarget <= 0L) return true
        return latestLauncher > 0L && latestLauncher >= latestTarget
    }

    /**
     * 听歌离开：陪伴条是应用内在场，回桌面 / 进别的 App 只留桌面圆球。
     * 播放仍在时不挂暂停条、不走离开倒计时；停播后再静默倒计时收口。
     */
    private var musicAwayPackage: String? = null
    private var stopMusicAwayObserve: (() -> Unit)? = null
    private val musicAwayHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val armSilentAwayRunnable = Runnable { armSilentAwayAfterMusicQuiet() }

    private fun isMusicAwayHold(packageName: String): Boolean =
        musicAwayPackage == packageName

    private fun shouldHoldCompanionOffForMusic(session: UsageSession): Boolean {
        if (!session.hasIntentGate) return false
        val pkg = session.packageName
        if (isMusicAwayHold(pkg)) return true
        if (CompanionScene.kindOf(pkg).isPureMusic) return true
        return BackgroundMediaPauser.packageHasActiveMediaPlayback(this, pkg)
    }

    private fun beginMusicAwayHold(packageName: String) {
        if (musicAwayPackage != packageName) {
            stopMusicAwayObserveOnly()
            musicAwayPackage = packageName
        }
        cancelBackgroundTimeout()
        clearAwayCountdownState()
        overlayManager.dismissCapsule()
        musicAwayHandler.removeCallbacks(armSilentAwayRunnable)
        if (stopMusicAwayObserve == null) {
            stopMusicAwayObserve = BackgroundMediaPauser.observePackageMediaPlayback(
                this,
                packageName
            ) { playing ->
                onMusicAwayPlaybackChanged(packageName, playing)
            }
        }
        Log.d(TAG, "$packageName 听歌离开：收起陪伴条，桌面只留圆球")
    }

    private fun onMusicAwayPlaybackChanged(packageName: String, playing: Boolean) {
        if (musicAwayPackage != packageName) return
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName || !session.isInBackground) {
            return
        }
        if (overlayManager.isCapsuleAttached()) {
            overlayManager.dismissCapsule()
        }
        musicAwayHandler.removeCallbacks(armSilentAwayRunnable)
        if (playing) {
            if (awayCountdownDeadlineElapsedMs > 0L || backgroundTimeoutJob != null) {
                cancelBackgroundTimeout()
                clearAwayCountdownState()
                Log.d(TAG, "$packageName 后台仍在播：取消离开倒计时")
            }
        } else {
            musicAwayHandler.postDelayed(armSilentAwayRunnable, MUSIC_AWAY_QUIET_GRACE_MS)
        }
    }

    private fun armSilentAwayAfterMusicQuiet() {
        val pkg = musicAwayPackage ?: return
        val session = sessionManager.currentSession.value ?: return
        if (session.packageName != pkg || !session.isInBackground) return
        if (BackgroundMediaPauser.packageHasActiveMediaPlayback(this, pkg)) return
        if (PackageProcessLiveness.isProcessGone(this, pkg)) {
            serviceScope.launch { closeSessionForProcessGone(pkg) }
            return
        }
        Log.d(TAG, "$pkg 后台音乐已停：静默离开倒计时，不挂暂停条")
        val remain = configuredAwayCountdownSec().coerceAtLeast(1L)
        armAwayCountdownDeadline(remain)
        startAwayCountdown(pkg)
        overlayManager.dismissCapsule()
    }

    private fun stopMusicAwayObserveOnly() {
        musicAwayHandler.removeCallbacks(armSilentAwayRunnable)
        stopMusicAwayObserve?.invoke()
        stopMusicAwayObserve = null
    }

    private fun stopMusicAwayHold() {
        stopMusicAwayObserveOnly()
        musicAwayPackage = null
    }

    private fun isScreenOffGraceExpired(): Boolean {
        val deadline = screenOffDeadlineElapsedMs
        return deadline > 0L && SystemClock.elapsedRealtime() >= deadline
    }

    /** 拦截页确认进入后的这一次：直到真的离开（亮屏下切走）才结束 */
    private var openVisitPackage: String? = null
    private var openVisitSawApp = false
    private var openVisitRelaunchElapsed = 0L

    private fun armOpenVisit(packageName: String) {
        openVisitPackage = packageName
        openVisitSawApp = false
    }

    private fun clearOpenVisit(packageName: String) {
        if (openVisitPackage == packageName) {
            openVisitPackage = null
            openVisitSawApp = false
        }
    }

    private fun noteOpenVisitForeground(packageName: String?) {
        if (packageName == null || packageName != openVisitPackage) return
        if (isDeviceLockedOrAsleep()) return
        // 拦截页按过 Home。保护窗里 UsageStats 仍可能报目标包，那是推桌面之前的旧前台，不能当成已经进了 App。
        if (isGateEnterHoldActive(packageName)) return
        openVisitSawApp = true
    }

    /** 还没在亮屏里看见这个 App，或正处于锁屏/屏保：不要收条、不要再弹门 */
    private fun isOpenVisitProtected(packageName: String?): Boolean {
        if (packageName == null || packageName != openVisitPackage) return false
        if (!openVisitSawApp) return true
        if (isDeviceLockedOrAsleep() || screenOffPackage == packageName) return true
        return false
    }

    /** 拦截页刚点「继续」：短时忽略假离开，避免拆掉刚挂上的胶囊 */
    @Volatile
    private var gateEnterHoldPackage: String? = null
    @Volatile
    private var gateEnterHoldUntilElapsed: Long = 0L

    private fun beginGateEnterHold(packageName: String, holdMs: Long = 2_800L) {
        gateEnterHoldPackage = packageName
        gateEnterHoldUntilElapsed = SystemClock.elapsedRealtime() + holdMs
        lastForegroundPackage = packageName
        cancelBackgroundDebounce()
        cancelMonitoredSwitchDebounce()
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        interceptLeavePendingPkg = null
        screenOffDuringInterceptPkg = null
        screenOffDuringInterceptShownAt = 0L
        clearAccountingHome(resumeSessionClock = true)
        overlayManager.beginGateEnter(packageName)
    }

    /**
     * 拦截层盖住后把目标 App 推回桌面，系统 UsageStats 不再把犹豫算进该应用。
     * 分身靠托管页抢焦点，不必再按 Home。
     */
    private fun parkTargetBehindIntercept(packageName: String) {
        if (packageName.isBlank()) return
        if (isGateEnterHoldActive(packageName) || overlayManager.gateEnteringPackage == packageName) {
            return
        }
        if (interceptGateDismissingPkg == packageName) return
        if (overlayManager.interceptParkedForRecents) return
        accountingHomePackage = packageName
        val session = sessionManager.currentSession.value
        if (session?.packageName == packageName && !session.isInBackground) {
            sessionManager.onAppGoBackground()
        }
        if (dualAppRegistry.hasSystemDual(packageName) &&
            (DualSpaceGateActivity.isShowing() || systemDualForegroundHint == packageName)
        ) {
            Log.d(TAG, "$packageName 分身拦截由托管页占住前台，不另按 Home")
            return
        }
        // 拦截层是全屏的，App 留在层下面。不要按 Home：按下去之后系统前台停在桌面，
        // 确认进入时再从服务里拉起经常被系统拦住，人就停在桌面圆球上，锁屏还会被当成重新开门。
        Log.d(TAG, "$packageName 拦截页盖住，不把应用推回桌面")
    }

    private fun clearAccountingHome(resumeSessionClock: Boolean) {
        val pkg = accountingHomePackage
        accountingHomePackage = null
        if (!resumeSessionClock || pkg == null) return
        val session = sessionManager.currentSession.value
        if (session?.packageName == pkg && session.isInBackground) {
            sessionManager.onAppReturnToForeground()
        }
    }

    private fun isAccountingHomeLeave(currentPkg: String?, target: String): Boolean {
        if (accountingHomePackage != target) return false
        if (currentPkg == target) return false
        if (currentPkg != null && currentPkg != packageName && !isLauncherPackage(currentPkg)) {
            return false
        }
        return true
    }

    private fun noteInterceptObscuredStart() {
        if (interceptObscuredStartedAtWall > 0L) return
        interceptObscuredStartedAtWall = System.currentTimeMillis()
    }

    private fun noteInterceptObscuredEnd() {
        val started = interceptObscuredStartedAtWall
        if (started <= 0L) return
        interceptObscuredAccumulatedMs +=
            (System.currentTimeMillis() - started).coerceAtLeast(0L)
        interceptObscuredStartedAtWall = 0L
    }

    /** 是否应在解锁后恢复拦截页（已有会话 / 进门中 / 自身 App 则不再弹门） */
    private fun shouldRestoreInterceptAfterUnlock(packageName: String): Boolean {
        if (packageName == this.packageName) return false
        if (isGateEnterHoldActive(packageName)) return false
        if (overlayManager.gateEnteringPackage == packageName) return false
        val session = sessionManager.currentSession.value
        if (session?.packageName == packageName) return false
        // 解锁后前台已是心锚自己：不要把门盖回管理台
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager != null) {
            val fg = getForegroundPackage(usageStatsManager)
            if (fg == this.packageName) return false
        }
        return true
    }

    private fun isGateEnterHoldActive(packageName: String? = null): Boolean {
        if (SystemClock.elapsedRealtime() >= gateEnterHoldUntilElapsed) {
            if (gateEnterHoldPackage != null) {
                overlayManager.clearGateEnter(gateEnterHoldPackage)
                gateEnterHoldPackage = null
            }
            return false
        }
        val held = gateEnterHoldPackage ?: return false
        if (packageName != null && packageName != held) return false
        // 胶囊挂上后仍保持到时限。UsageStats 常还报桌面，提前结束会把刚出现的陪伴条当成离开收掉。
        return true
    }

    /** 防抖：检测到被监控 App 离开前台后，延迟确认是否真正进入后台的协程 */
    private var backgroundDebounceJob: Job? = null
    /**
     * 会话最近一次进入后台的 elapsedRealtime。
     * 音量条等会在 1～数秒内把 UsageStats 抖成「离开又回来」；短窗内静默续上，勿弹呼吸门。
     */
    private var sessionBackgroundAtElapsed: Long = -1L
    /** 与 [sessionBackgroundAtElapsed] 对应的墙钟，供 UsageEvents 反查桌面假回报 */
    private var sessionBackgroundAtWallMs: Long = -1L
    /** 本次后台是否经桌面离开；为真时 UsageStats 单独报回 App 不算真回前台 */
    private var leftToLauncherWhileAway: Boolean = false
    /**
     * 防抖：监控 App A 使用中闪报到另一监控 App B 时，延迟确认是否真互切。
     * 未确认前不 [parkSessionForMonitoredAppSwitch]，避免意图门会话被误 [SWITCHED_AWAY]
     * 后马上补出意图拦截页（表现为「没到点却弹意图门」）。
     */
    private var monitoredSwitchDebounceJob: Job? = null
    private var monitoredSwitchDebounceFromPkg: String? = null
    private var monitoredSwitchDebounceToPkg: String? = null
    /** 常驻通知刷新协程（每分钟刷新一次，展示今日使用汇总） */
    private var notificationRefreshJob: Job? = null
    /** 反馈回复低频拉取（服务存活时的辅助通道） */
    private var feedbackReplySyncJob: Job? = null
    /** 解锁后多次尝试弹出被息屏打断的回顾横条（锁屏超时已改走通知，不在此列） */
    private var flushAwayEndedBarJob: Job? = null
    /** 锁屏/息屏期间已发过「离开超时」对照通知的 recordId，避免重复 */
    private val awayEndedNotifiedRecordIds = mutableSetOf<Long>()
    /**
     * 回顾横条因息屏被静默收起后，解锁已重展过一次的 recordId。
     * 再锁再亮不再循环弹条，改走通知。
     */
    private val awayEndedBarReshowAttempted = mutableSetOf<Long>()

    /**
     * 锁屏超时协程：前台息屏后启动，[configuredScreenOffGraceMs] 内若用户未回到被监控 App，静默结束会话。
     * 亮屏并回到 App 后应调用 [cancelScreenOffTimeout] 取消。
     */
    private var screenOffTimeoutJob: Job? = null
    /** 前台息屏宽限墙钟截止（elapsedRealtime） */
    private var screenOffDeadlineElapsedMs: Long = -1L

    /**
     * 息屏时被监控 App 的包名（用于亮屏后判断是否需要恢复会话）。
     * 仅在「前台息屏」进入宽限期时赋值；桌面暂停态息屏不走此标记。
     */
    private var screenOffPackage: String? = null

    /** 息屏超过 2 分钟：亮屏后把应用送回桌面，在此之前不要再弹拦截 */
    private var screenOffForcedExitPkg: String? = null

    /**
     * 桌面暂停态下锁屏的包名。
     * 息屏时会收起胶囊；解锁后若会话仍在后台，应还原暂停胶囊（而不是当成息屏宽限把胶囊弄丢）。
     */
    private var lockedWhilePausedPackage: String? = null

    /** 进程已死后延迟弹出「离开结束」横条的协程。
     * 会话已立刻 [APP_CLOSED] 收口；若用户在等待期内又打开该 App（拦截/新会话），则取消横条。
     */
    private var processGoneEndedBarJob: Job? = null

    /** 灭屏期间倒计时归零时暂存，亮屏后再弹回顾横条（避免计时窗口在锁屏中被吞没）。 */
    private var pendingAwayEndedBar: PendingAwayEndedBar? = null

    /** 由 SCREEN_OFF / USER_PRESENT 维护，比 PowerManager 更可靠。 */
    private var screenIsOff = false

    /** 由 DREAMING_STARTED / DREAMING_STOPPED 维护；屏保期间屏幕仍可能 interactive。 */
    private var screenIsDreaming = false

    private data class PendingAwayEndedBar(
        val packageName: String,
        val appName: String,
        val recordId: Long,
        val purpose: String?,
        val intentKind: com.life.mindfulnessapp.domain.model.IntentKind?,
        val endedAt: Long,
        val durationSeconds: Long,
        val endReason: String
    )

    /**
     * 拦截页展示期间息屏 / 屏保的目标包名。
     * 息屏常被 UsageStats 误判成「离开」，不能记守住、不能拆页；解锁后若页已丢则静默重展且不计冲动。
     */
    private var screenOffDuringInterceptPkg: String? = null

    /** 息屏前拦截页首次出现的墙钟，静默重展时续上，避免门时钟被重置 */
    private var screenOffDuringInterceptShownAt: Long = 0L

    /** 拦截页「疑似 Home 离开」防抖协程：息屏/屏保广播到达时必须取消，避免误触发离开仪式 */
    private var interceptLeaveJob: Job? = null

    /** 与 [interceptLeaveJob] 配对，避免轮询每 300ms 重置 900ms 防抖导致 Home 永不完成 */
    private var interceptLeavePendingPkg: String? = null

    /** 守住收口进行中：回桌面会再走前台探测，避免同一扇门记两次 */
    @Volatile
    private var interceptGateDismissingPkg: String? = null

    /**
     * 拦截页为对齐系统使用时长，主动把目标 App 推回桌面。
     * 这次回桌面不是用户按 Home，不能记守住离开，也不能把 App 再拉回前台。
     */
    @Volatile
    private var accountingHomePackage: String? = null

    /** 拦截页息屏/屏保期间累计的墙钟，门上犹豫不把睡着的时间算进去 */
    private var interceptObscuredAccumulatedMs: Long = 0L
    private var interceptObscuredStartedAtWall: Long = 0L

    /** 最近任务仍打开（无障碍 / 方块键） */
    @Volatile
    private var interceptRecentsOpen: Boolean = false

    private var lastRecentsHintElapsed: Long = 0L

    /** 本次卸层进入进程列表的时刻，用来区分「概览下 UsageStats 仍报目标 App」 */
    private var recentsParkedAtElapsed: Long = 0L
    private var lastRecentsSuppressElapsed: Long = 0L

    private var interceptRecentsHomeJob: Job? = null

    /** Home 键短延迟：方块键常夹带假 HOME，等进程列表信号后再决定要不要守住 */
    private var interceptHomeDebounceJob: Job? = null

    /**
     * 时长锁 / 时段锁被 Home 静默关掉后，在出现一次「真正再打开」
     *（dismiss 之后新的进程级 MOVE_TO_FOREGROUND）之前不要立刻重展。
     */
    private var hardLockHomeSuppressedPkg: String? = null
    private var hardLockHomeDismissedAtWall: Long = 0L

    /**
     * 时段硬门「紧急进入」武装中的包名。
     * 进门到会话结束期间 [isPeriodHardLocked] 对该包返回 false；会话切走后清掉。
     */
    @Volatile
    private var periodExemptionPackage: String? = null

    /** 意图时长到点页正在展示时的会话上下文，供 Home = 稍后回顾使用 */
    private var sessionLimitEndingRecordId: Long = -1L
    private var sessionLimitEndingPkg: String? = null
    private var sessionLimitEndingAppName: String = ""
    private var sessionLimitEndingPurpose: String? = null

    /**
     * 解锁后守卫截止（elapsedRealtime）：此之前拦截页 UsageStats「离开」一律视为假离开。
     */
    private var interceptUnlockGuardUntilElapsed: Long = 0L

    private var enabledPackages: Set<String> = emptySet()
    /** 守计划 / 日程锁缓存：与 enabledPackages 同步刷新，供硬锁判定热路径读取 */
    @Volatile
    private var cachedPlanBlocks: List<PlanBlock> = emptyList()
    /** 已启用监控包名（不含日程锁展开），供 MONITORED 范围判定 */
    @Volatile
    private var cachedMonitoredPackages: Set<String> = emptySet()
    /** 系统桌面 / Launcher 包名，用于区分「回桌面守住」与「切到其他 App」 */
    private val launcherPackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }
    /**
     * 打开时大概率需要盖拦截层的包（意图门 或 时段锁）。
     * 供无障碍路径同步 [tryEagerInterceptCover]，不等 DB / 协程。
     */
    @Volatile
    private var interceptCoverPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null

    /** 最近一次探测到的系统分身前台包（抢焦点后 UsageStats 会丢，靠此标记） */
    @Volatile
    private var systemDualForegroundHint: String? = null
    /** 用户在心锚内显式停止监控时为 true；此时 onDestroy 应正常收口会话 */
    @Volatile
    private var userRequestedStop = false
    /** 当前拦截页的串接 ID（intercept_show → gate/limit 决策） */
    private var currentInterceptId: String = ""

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    private fun isDeviceLockedOrAsleep(): Boolean {
        if (screenIsDreaming) return true
        if (!isScreenInteractive()) return true
        val km = getSystemService(KeyguardManager::class.java) ?: return false
        return km.isKeyguardLocked
    }

    /** 拦截页场景：息屏 / 锁屏 / 屏保 / 开场过渡 → 不记守住 */
    private fun shouldSuppressInterceptLeave(
        packageName: String,
        currentPkg: String? = null,
        fromAccessibility: Boolean = false
    ): Boolean {
        if (screenOffDuringInterceptPkg == packageName) return true
        if (isDeviceLockedOrAsleep()) return true
        if (isInterceptOpenTransition(currentPkg, packageName)) return true
        if (overlayManager.interceptParkedForRecents || interceptRecentsOpen) return true
        if (SystemClock.elapsedRealtime() < interceptUnlockGuardUntilElapsed) return true
        return false
    }

    /**
     * 从桌面点开 App：拦截占位已出但 UsageStats / 无障碍仍报 Launcher。
     * eager cover 常把 [lastForegroundPackage] 写成目标包，不能再要求「两端都是桌面」。
     * 保护窗内若已有挂稳后的新桌面进入，则视为真 Home，不是开场噪声。
     */
    private fun isInterceptOpenTransition(
        currentPkg: String?,
        targetPackage: String
    ): Boolean {
        val shownAt = overlayManager.interceptShownAtWall
        if (shownAt <= 0L) return false
        if (System.currentTimeMillis() - shownAt >= INTERCEPT_SHOW_GUARD_MS) return false
        if (!isLauncherPackage(currentPkg)) return false
        // 挂稳后的桌面进入 = 用户真按了 Home，放行离开判定
        if (sawFreshLauncherForeground(targetPackage)) return false
        return true
    }

    private fun isLauncherPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return pkg in launcherPackages
    }

    /** 拦截页离开后是否回到桌面（仅回桌面才算守住）。 */
    private fun isInterceptLeaveToHome(
        currentPkg: String?,
        targetPackage: String,
        fromAccessibility: Boolean = false
    ): Boolean {
        if (isInterceptOpenTransition(currentPkg, targetPackage)) return false
        if (isLauncherPackage(currentPkg)) {
            // 开场保护窗内无障碍仍可能报到桌面窗口；须有挂稳后的桌面进入，避免首进误拆门
            val shownAt = overlayManager.interceptShownAtWall
            val withinShowGuard = shownAt > 0L &&
                System.currentTimeMillis() - shownAt < INTERCEPT_SHOW_GUARD_MS
            if (fromAccessibility && !withinShowGuard) return true
            return sawFreshLauncherForeground(targetPackage)
        }
        // 可聚焦拦截层一挂上，目标 App 常立刻 MOVE_TO_BACKGROUND；
        // 不能单凭这个历史事件当 Home，否则门会自己消失并弹出守住横条。
        return sawFreshLauncherForeground(targetPackage)
    }

    /** 已确认切到桌面：撤销息屏误标记，避免 Home 被永久 suppress。 */
    private fun clearFalseInterceptObscuredIfHome(
        currentPkg: String?,
        targetPackage: String,
        fromAccessibility: Boolean = false
    ) {
        if (!isInterceptLeaveToHome(currentPkg, targetPackage, fromAccessibility)) return
        // 解锁 / 退出屏保余波内 UsageStats 常误报桌面，此时清标记会立刻误走「守住了」
        if (SystemClock.elapsedRealtime() < interceptUnlockGuardUntilElapsed) return
        if (isDeviceLockedOrAsleep()) return
        if (screenOffDuringInterceptPkg == targetPackage) {
            screenOffDuringInterceptPkg = null
            screenOffDuringInterceptShownAt = 0L
            Log.d(TAG, "$targetPackage 确认 Home 离开，清除息屏假离开标记")
        }
    }

    private fun markInterceptScreenObscured(packageName: String?, reason: String) {
        val pkg = packageName
            ?: overlayManager.interceptTargetPackage
            ?: screenOffDuringInterceptPkg
            ?: return
        screenOffDuringInterceptPkg = pkg
        val shownAt = overlayManager.interceptShownAtWall
        if (shownAt > 0L) {
            screenOffDuringInterceptShownAt = shownAt
        }
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        interceptLeavePendingPkg = null
        overlayManager.reconcileInterceptVisibilityAfterObscured(pkg)
        Log.d(TAG, "拦截页期间$reason，保留拦截、不记离开 [$pkg]")
    }

    private fun armInterceptUnlockGuard(durationMs: Long = INTERCEPT_UNLOCK_GUARD_MS) {
        interceptUnlockGuardUntilElapsed =
            SystemClock.elapsedRealtime() + durationMs
    }

    private fun armInterceptShowGuard() {
        armInterceptUnlockGuard(INTERCEPT_SHOW_GUARD_MS)
    }

    private fun isKeyguardLocked(): Boolean {
        val km = getSystemService(KeyguardManager::class.java) ?: return false
        return km.isKeyguardLocked
    }

    /**
     * 息屏 / 屏保结束后恢复拦截页。
     * 屏保常只发 DREAMING_STOPPED、不发 USER_PRESENT；若只在解锁时恢复，
     * 层已被系统卸掉时会露出目标 App，随后 UsageStats 误报桌面再弹「守住了」。
     */
    private fun restoreInterceptAfterScreenObscured(
        packageName: String,
        reason: String
    ) {
        armInterceptUnlockGuard()
        overlayManager.suppressTransientLeaveFeedback()
        screenOffDuringInterceptPkg = null
        when {
            shouldRestoreInterceptAfterUnlock(packageName) -> {
                if (overlayManager.isInterceptLayerWindowAttached()) {
                    overlayManager.reconcileInterceptVisibilityAfterObscured(packageName)
                    Log.d(
                        TAG,
                        "$reason：拦截页仍在窗口，恢复可见且不计新冲动 [$packageName]"
                    )
                } else {
                    Log.d(
                        TAG,
                        "$reason：拦截层已丢，静默重挂且不计冲动 [$packageName]"
                    )
                    serviceScope.launch {
                        showInterceptOverlay(packageName, countImpulse = false)
                    }
                }
            }
            overlayManager.isInterceptLayerWindowAttached() ||
                overlayManager.hasInterceptLayerAttached() -> {
                overlayManager.dismissIntercept(keepCapsule = true)
                Log.d(
                    TAG,
                    "$reason：卸残留拦截层（已有会话或进门中）[$packageName]"
                )
            }
            else -> {
                overlayManager.dismissIntercept(keepCapsule = true)
                Log.d(
                    TAG,
                    "$reason：跳过拦截重展（已有会话或进门中）[$packageName]"
                )
            }
        }
    }

    /**
     * 意图门拦截页期间用户回桌面：与点「不进去了」相同（卸层 + 守住轻条 + 记 GATE_DISMISS）。
     * @return 是否已接管（调用方应 return）
     */
    private fun tryDismissIntentGateOnHome(
        currentPkg: String?,
        fromAccessibility: Boolean = false
    ): Boolean {
        val target = overlayManager.interceptTargetPackage ?: return false
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) return false
        if (overlayManager.interceptKind == null) return false
        if (!overlayManager.isInterceptLayerOnScreen(target)) return false
        if (overlayManager.isAdPlaying.get()) return false
        if (isGateEnterHoldActive(target) ||
            overlayManager.gateEnteringPackage == target
        ) {
            return false
        }
        if (interceptGateDismissingPkg == target) return false
        if (overlayManager.interceptParkedForRecents || interceptRecentsOpen) return false
        if (recentsHomeKeyShouldIgnore()) return false
        val shownAt = overlayManager.interceptShownAtWall
        if (shownAt > 0L &&
            System.currentTimeMillis() - shownAt < INTERCEPT_SHOW_GUARD_MS
        ) {
            return false
        }
        // 门还在、并且窗口仍有焦点：用户正在看门。UsageStats/无障碍报桌面是盖层抢焦噪声，不能拆门。
        if (overlayManager.interceptWindowHasFocus()) return false
        if (isAccountingHomeLeave(currentPkg, target)) {
            lastForegroundPackage = currentPkg ?: lastForegroundPackage
            return true
        }
        if (!isInterceptLeaveToHome(currentPkg, target, fromAccessibility)) return false
        clearFalseInterceptObscuredIfHome(currentPkg, target, fromAccessibility)
        if (shouldSuppressInterceptLeave(target, currentPkg, fromAccessibility)) return false
        lastForegroundPackage = currentPkg ?: lastForegroundPackage
        if (isLauncherPackage(currentPkg)) {
            Log.d(TAG, "$target 拦截页期间回到桌面，拉回门口（poll=$currentPkg）")
            pullInterceptBack(target, closeOverview = false)
            return true
        }
        Log.d(
            TAG,
            "$target 拦截页疑似回桌面，走防抖确认（poll=$currentPkg）"
        )
        beginInterceptHomeLeave(target, currentPkg)
        return true
    }

    /**
     * 无障碍刚报出目标包时同步盖占位层（主线程优先队列），
     * 不等 [handleForegroundChange] 里的 DB / 挂起会话。
     * 可恢复会话 / 无门进入路径会随后拆掉或替换。
     */
    private fun tryEagerInterceptCover(packageName: String) {
        val pkg = dualAppRegistry.canonicalize(packageName)
        // 心锚自身不作拦截目标（与走神提醒一致）
        if (pkg == this.packageName) return
        if (screenOffForcedExitPkg == pkg) return
        if (!interceptCoverPackages.contains(pkg)) return
        if (overlayManager.isCapsuleDialogBlocking.get()) return
        if (isGateEnterHoldActive(pkg)) {
            val live = sessionManager.currentSession.value?.packageName == pkg
            val entering = overlayManager.gateEnteringPackage == pkg
            if (live || entering) return
            // 无会话的残留保护窗不挡占位盖层
            overlayManager.clearGateEnter(pkg)
            if (gateEnterHoldPackage == pkg) {
                gateEnterHoldPackage = null
                gateEnterHoldUntilElapsed = 0L
            }
        }
        val session = sessionManager.currentSession.value
        if (session?.packageName == pkg) return
        // 另一包仍有前台活会话：等互切防抖确认，避免闪报先盖门再误收口
        if (session != null && !session.isInBackground && session.packageName != pkg) {
            Log.d(TAG, "eager cover 跳过：$pkg（[${session.packageName}] 前台会话未确认互切）")
            return
        }
        if (sessionManager.hasParkedSession(pkg)) return
        // 进程列表还在：不要把门盖回概览上。残留 park（人已在桌面）则放行，否则首进没门。
        if (overlayManager.interceptParkedForRecents && shouldHoldRecentsPark()) return
        // 标志位在但层已卸（息屏/竞态）：必须重盖，否则首进永久跳过
        if (overlayManager.isInterceptLayerOnScreen(pkg)) {
            return
        }
        if (pkg != packageName) {
            Log.d(TAG, "eager cover: $packageName → 主包 $pkg（分身同锁）")
        } else {
            Log.d(TAG, "eager cover: $pkg")
        }
        overlayManager.prepareInterceptCover(pkg)
        beginInterceptFocusHost(pkg)
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        interceptLeavePendingPkg = null
        // 不在这里改 lastForegroundPackage：否则随后同包轮询永远不走 showInterceptOverlay，
        // 用户只看到纯色占位、进不了真正的意图门。
        armInterceptShowGuard()
    }

    /**
     * 目标 App 已在前台、没有会话、门却不在：补出门。
     * 覆盖「第一次进入被同包轮询吞掉」和「进程列表残留 park 挡掉桌面再开」。
     */
    private suspend fun ensureIntentGateIfNeeded(packageName: String): Boolean {
        if (packageName == this.packageName) return false
        if (isGateEnterHoldActive(packageName) || overlayManager.gateEnteringPackage == packageName) {
            val live = sessionManager.currentSession.value?.packageName == packageName
            val entering = overlayManager.gateEnteringPackage == packageName
            if (live || entering) return false
            overlayManager.clearGateEnter(packageName)
            if (gateEnterHoldPackage == packageName) {
                gateEnterHoldPackage = null
                gateEnterHoldUntilElapsed = 0L
            }
        }
        if (sessionManager.currentSession.value?.packageName == packageName) return false
        if (sessionManager.hasParkedSession(packageName)) return false
        val needsCover = interceptCoverPackages.contains(packageName) ||
            enabledPackages.contains(packageName)
        if (!needsCover) return false
        if (overlayManager.interceptParkedForRecents && shouldHoldRecentsPark()) return false
        if (overlayManager.isInterceptLayerOnScreen(packageName) &&
            overlayManager.hasInterceptUiAttached()
        ) {
            return false
        }
        val limit = appLimitRepository.getAppLimit(packageName)
        if (limit == null || !limit.isEnabled) return false
        Log.d(TAG, "$packageName 应出门却未在屏上，补挂门口")
        tryEagerInterceptCover(packageName)
        showInterceptOverlay(packageName)
        return true
    }

    /** 占位层已盖住 App，但 Compose 意图门还没挂上。 */
    private fun shouldUpgradeInterceptPlaceholder(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (overlayManager.interceptTargetPackage != packageName) return false
        if (!overlayManager.isInterceptLayerOnScreen(packageName)) return false
        return !overlayManager.hasInterceptUiAttached()
    }

    /**
     * 仅系统分身需要托管页：机主侧悬浮窗盖不住 user 128，必须先抢回焦点。
     * 普通 App 不要起黑页——会盖住/抢焦意图门，首进看起来像没弹门，进程列表也会被当成守住离开。
     */
    private fun beginInterceptFocusHost(packageName: String) {
        if (!dualAppRegistry.hasSystemDual(packageName)) return
        val dualNow = systemDualForegroundHint == packageName ||
            dualAppRegistry.probeSystemDualForeground() == packageName
        if (!dualNow) return
        dualAppRegistry.lastInterceptWasSystemDual = true
        Log.d(TAG, "系统分身拦截：抢回机主焦点 $packageName")
        DualSpaceGateActivity.launch(this, packageName)
    }

    /**
     * 卸掉拦截焦点托管页。
     * [reopenTarget] 为 true 时：分身走系统分身启动，普通包恢复任务栈。
     */
    private fun endInterceptFocusHost(packageName: String, reopenTarget: Boolean) {
        val wasDual = dualAppRegistry.lastInterceptWasSystemDual
        dualAppRegistry.lastInterceptWasSystemDual = false
        if (systemDualForegroundHint == packageName) {
            systemDualForegroundHint = null
        }
        DualSpaceGateActivity.finishIfShowing()
        clearAccountingHome(resumeSessionClock = reopenTarget)
        if (!reopenTarget) return
        // 立刻拉起，且必须赶在拆拦截层之前。拆掉全屏层后再从服务里 startActivity，
        // 系统会当成后台启动拦住，人就留在刚才那次 Home 的桌面上。
        if (wasDual) {
            val ok = OemDualSpace.launchSystemDualInstance(this, packageName)
            Log.d(TAG, "确认进入后启动系统分身 $packageName => $ok")
        } else {
            launchApp(packageName)
            Log.d(TAG, "确认进入后恢复任务栈 $packageName")
        }
    }

    /**
     * 锁屏 / 亮屏 / 屏保广播接收器：
     *
     * - ACTION_SCREEN_OFF / ACTION_DREAMING_STARTED：
     *     • 拦截页展示中 → 保留拦截，允许锁屏；亮屏后页面不变，不计新冲动
     *     • 已在桌面暂停（含刚切入后台）→ 收起胶囊，离开倒计时继续流逝
     *     • 仍在 App 内前台息屏 → 息屏宽限（时长同暂停恢复时长）；亮屏回到 App 则静默续用
     *
     * - ACTION_SCREEN_ON / ACTION_USER_PRESENT / ACTION_DREAMING_STOPPED：
     *     • 拦截页息屏：解锁后若页还在则继续；若已丢则静默重展且不计冲动
     *     • 桌面暂停后锁屏：倒计时已归零则发通知栏对照（不弹横条）；否则解冻并还原暂停胶囊
     *     • 息屏宽限：若 App 已在前台则续用；若仍在桌面则先还原暂停胶囊，等待点回
     */
    private val packageLifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_PACKAGE_RESTARTED) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            val session = sessionManager.currentSession.value ?: return
            if (session.packageName != pkg) return
            Log.d(TAG, "$pkg 收到 PACKAGE_RESTARTED，按进程已死处理")
            serviceScope.launch { onMonitoredProcessGone(pkg) }
        }
    }

    /**
     * 使用中锁屏 / 屏保：这一次停住，不计时，不结束。
     * 亮屏回到同一个 App 时再看时段锁：在锁里就硬挡，否则把陪伴条续上。
     */
    private fun pauseForegroundSessionForScreenObscure(session: UsageSession, reason: String) {
        if (session.isInBackground) return
        if (screenOffPackage == session.packageName) return
        cancelBackgroundDebounce()
        cancelMonitoredSwitchDebounce()
        cancelBackgroundTimeout()
        clearAwayCountdownState()
        cancelScreenOffTimeout()
        sessionManager.onAppGoBackground()
        markSessionBackgroundNow()
        Log.d(TAG, "$reason，计时停住 [${session.packageName}]，不因灭屏结束这一次")
        overlayManager.dismissAll()
        lockedWhilePausedPackage = null
        screenOffPackage = session.packageName
        screenOffDeadlineElapsedMs = -1L
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_DREAMING_STARTED -> {
                    if (intent.action == Intent.ACTION_DREAMING_STARTED) {
                        screenIsDreaming = true
                        walkAwarenessCoordinator.onScreenOff()
                        if (overlayManager.isAwayEndedBarShowing()) {
                            overlayManager.dismissAwayEndedBarSilently()
                            Log.d(TAG, "屏保收起回顾横条，待退出屏保后再展示")
                        }
                    }
                    if (intent.action == Intent.ACTION_SCREEN_OFF) {
                        screenIsOff = true
                        walkAwarenessCoordinator.onScreenOff()
                        if (overlayManager.isAwayEndedBarShowing()) {
                            overlayManager.dismissAwayEndedBarSilently()
                            Log.d(TAG, "息屏收起回顾横条，待亮屏后再展示")
                        }
                    }
                    val obscuredReason =
                        if (intent.action == Intent.ACTION_DREAMING_STARTED) "进入屏保" else "息屏"
                    val interceptLayerActive =
                        overlayManager.isInterceptVisible.get() ||
                            screenOffDuringInterceptPkg != null ||
                            overlayManager.hasInterceptLayerAttached() ||
                            overlayManager.hasActiveInterceptTarget()
                    if (interceptLayerActive) {
                        val interceptPkg = overlayManager.interceptTargetPackage
                            ?: overlayManager.gateEnteringPackage
                            ?: screenOffDuringInterceptPkg
                        if (interceptPkg != null) {
                            markInterceptScreenObscured(interceptPkg, obscuredReason)
                            noteInterceptObscuredStart()
                        }
                        // 拦截态息屏/屏保：到此为止。勿落入下方会话息屏路径（会 dismissAll 拆掉拦截页）
                        return
                    }
                    if (intent.action == Intent.ACTION_DREAMING_STARTED) {
                        val live = sessionManager.currentSession.value
                        if (live != null && !live.isInBackground) {
                            pauseForegroundSessionForScreenObscure(live, "进入屏保")
                        }
                        return
                    }
                    val session = sessionManager.currentSession.value ?: return
                    cancelBackgroundDebounce()
                    cancelMonitoredSwitchDebounce()
                    if (session.isInBackground) {
                        lockedWhilePausedPackage = session.packageName
                        // 音量误判进后台后再锁屏：lastForeground 仍像「在 App 里」，
                        // 应记成息屏宽限，亮屏回 App 静默续上，勿再弹呼吸门。
                        val stillLooksInApp = lastForegroundPackage == null ||
                            lastForegroundPackage == session.packageName ||
                            isSystemUiPackage(lastForegroundPackage) ||
                            isTransientSystemOverlayPackage(lastForegroundPackage)
                        if (stillLooksInApp) {
                            if (screenOffPackage == null) {
                                screenOffPackage = session.packageName
                            }
                        } else {
                            screenOffPackage = null
                        }
                        if (screenOffDuringInterceptPkg == session.packageName) {
                            screenOffDuringInterceptPkg = null
                        }
                        cancelScreenOffTimeout()
                        // 桌面暂停态锁屏：不拆胶囊、不冻倒计时（墙钟继续流逝）
                        if (session.hasIntentGate) {
                            if (isMusicAwayHold(session.packageName)) {
                                overlayManager.dismissCapsule()
                            } else {
                                syncAwayCountdownRemainingFromDeadline()
                                overlayManager.updateAwayCountdown(
                                    awayCountdownRemainingSec.coerceAtLeast(0L)
                                )
                                if (backgroundTimeoutJob?.isActive != true) {
                                    startAwayCountdown(session.packageName)
                                }
                            }
                        }
                    } else {
                        pauseForegroundSessionForScreenObscure(session, "息屏")
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_DREAMING_STOPPED,
                Intent.ACTION_USER_PRESENT -> {
                    if (intent.action == Intent.ACTION_DREAMING_STOPPED) {
                        screenIsDreaming = false
                        val obscured = screenOffPackage
                        if (obscured != null && !isKeyguardLocked()) {
                            serviceScope.launch {
                            tryResumeAfterScreenOff(obscured, reason = "DREAMING_STOPPED")
                        }
                        }
                    }
                    if (intent.action == Intent.ACTION_USER_PRESENT) {
                        screenIsOff = false
                        walkAwarenessCoordinator.onScreenOn()
                    } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                        screenIsOff = false
                        walkAwarenessCoordinator.onScreenOn()
                    }
                    // 亮屏即压住误触发的离开横条；真正恢复仍等 USER_PRESENT
                    if (screenOffDuringInterceptPkg != null) {
                        overlayManager.suppressTransientLeaveFeedback()
                        armInterceptUnlockGuard()
                    }
                    if (intent.action == Intent.ACTION_SCREEN_ON ||
                        intent.action == Intent.ACTION_USER_PRESENT ||
                        intent.action == Intent.ACTION_DREAMING_STOPPED
                    ) {
                        noteInterceptObscuredEnd()
                    }
                    val interceptLockedPkg = screenOffDuringInterceptPkg
                    if (interceptLockedPkg != null && intent.action == Intent.ACTION_USER_PRESENT) {
                        // 先开守卫再清标记，避免解锁瞬间 UsageStats「切桌面」误走守住仪式
                        armInterceptUnlockGuard()
                        screenOffDuringInterceptPkg = null
                        when {
                            shouldRestoreInterceptAfterUnlock(interceptLockedPkg) -> {
                                if (overlayManager.isInterceptLayerWindowAttached()) {
                                    overlayManager.reconcileInterceptVisibilityAfterObscured(
                                        interceptLockedPkg
                                    )
                                    Log.d(
                                        TAG,
                                        "解锁后拦截页仍在窗口，恢复可见且不计新冲动 [$interceptLockedPkg]"
                                    )
                                } else {
                                    Log.d(
                                        TAG,
                                        "解锁后拦截层被系统卸掉，静默重挂且不计冲动 [$interceptLockedPkg]"
                                    )
                                    serviceScope.launch {
                                        showInterceptOverlay(
                                            interceptLockedPkg,
                                            countImpulse = false
                                        )
                                    }
                                }
                            }
                            overlayManager.isInterceptLayerWindowAttached() ||
                                overlayManager.hasInterceptLayerAttached() -> {
                                overlayManager.dismissIntercept(keepCapsule = true)
                                Log.d(
                                    TAG,
                                    "解锁后卸残留拦截层（已有会话或进门中）[$interceptLockedPkg]"
                                )
                            }
                            else -> {
                                overlayManager.dismissIntercept(keepCapsule = true)
                                Log.d(
                                    TAG,
                                    "解锁后跳过拦截重展（已有会话或进门中）[$interceptLockedPkg]"
                                )
                            }
                        }
                    }
                    val pausedPkg = lockedWhilePausedPackage
                    if (pausedPkg != null &&
                        (intent.action == Intent.ACTION_SCREEN_ON ||
                            intent.action == Intent.ACTION_USER_PRESENT)
                    ) {
                        Log.d(TAG, "屏幕亮起，确保暂停胶囊可见 [$pausedPkg]")
                        serviceScope.launch {
                            ensureIntentPauseCapsuleAlive(pausedPkg)
                        }
                    }
                    val pkg = screenOffPackage
                    if (pkg != null && intent.action == Intent.ACTION_USER_PRESENT) {
                        Log.d(TAG, "屏幕解锁，按当时是否落在时段锁里决定续上或硬挡 [$pkg]")
                        serviceScope.launch {
                            tryResumeAfterScreenOff(pkg, reason = "USER_PRESENT")
                        }
                    }
                    if (intent.action == Intent.ACTION_USER_PRESENT) {
                        serviceScope.launch {
                            // 仅息屏打断了正在显示的横条时才 flush；暂停锁屏超时已走通知
                            flushPendingAwayEndedBar(force = true)
                            scheduleFlushPendingAwayEndedBar(forceAfterFirst = true)
                        }
                    } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                        serviceScope.launch {
                            scheduleFlushPendingAwayEndedBar(forceAfterFirst = false)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        markRunning(true)
        runningInstance = this
        createNotificationChannel()
        createSessionEndChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 若距上次心跳过久，视为被清理后恢复 → 首页横幅提示
        MonitorHealthStore.onMonitorStarting(this)
        startHealthHeartbeat()

        // 会话恢复在 onStartCommand 中与 startMonitoring 串行，避免竞态弹门

        // ── 保活：WorkManager（15 分钟）+ AlarmManager（国产机约 3 分钟链式）────────
        ServiceWatchdogWorker.schedule(this)
        KeepAliveAlarmScheduler.schedulePeriodic(this)
        QuotePushScheduler.cancel(this)
        // 监控起来后立即按偏好隐藏最近任务（不必等用户打开主界面）
        RecentsHider.apply(this, appPreferences.isHideFromRecentsEnabled())

        // 用户点击胶囊「结束」后：
        //  HomeAligned / HomeDrifted / LegacyUnreviewed → 先由 onLeaveTargetToHome 回桌面
        //  OpenRecord → 打开心锚并定位该条
        overlayManager.onInterceptBackPressed = onBack@{
            val target = overlayManager.interceptTargetPackage ?: return@onBack
            if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) {
                deferSessionLimitReview(pushHome = true)
                return@onBack
            }
            Log.d(TAG, "$target 拦截页系统返回键，按守住离开")
            completeInterceptGateDismiss(target, pressHomeAfter = true)
        }
        overlayManager.onInterceptRecentsPressed = onRecents@{
            val target = overlayManager.interceptTargetPackage ?: return@onRecents
            Log.d(TAG, "$target 拦截页吞掉最近任务键，拉回门口")
            KeepAliveAccessibilityService.pressBack()
            overlayManager.requestInterceptFocus()
        }
        overlayManager.onInterceptLostFocus = onLostFocus@{
            onInterceptWindowLostFocus()
        }
        overlayManager.onInterceptGainedFocus = {
            // 门已盖稳并拿到焦点：结束进程列表会话，后续 Home 才是守住
            interceptRecentsOpen = false
            overlayManager.clearRecentsPark()
        }
        overlayManager.onInterceptCoverPrepared = { pkg ->
            parkTargetBehindIntercept(pkg)
            // 盖层即压声：进门保护窗内若又盖门（再次进入），仍要停播放
            BackgroundMediaPauser.beginSuppressPlayback(this, pkg)
        }
        overlayManager.onInterceptLayerRemoved = {
            // 马上又盖了新门（换包 / 占位升级前的竞态）则继续压；否则交还焦点
            val stillCovered = overlayManager.isInterceptVisible.get() &&
                !overlayManager.interceptTargetPackage.isNullOrBlank()
            if (!stillCovered) {
                BackgroundMediaPauser.endSuppressPlayback()
            }
        }
        overlayManager.onLeaveTargetToHome = { endedPackage ->
            if (lastForegroundPackage == packageName) {
                Log.d(TAG, "[ManualEnd] 已在心锚内，不回桌面 pkg=$endedPackage")
            } else {
                Log.d(TAG, "[ManualEnd] 立刻回桌面 pkg=$endedPackage")
                pressHomeButton(pauseMediaForPackage = endedPackage)
                scheduleEnsureLeftForeground(endedPackage)
            }
        }
        overlayManager.onManualEndSession = { recordId, mindfulnessLevel, destination ->
            val endedPackage = overlayManager.capsuleAppPackageName.value
            when (destination) {
                ManualEndDestination.HomeAligned,
                ManualEndDestination.HomeDrifted -> {
                    Log.d(
                        TAG,
                        "[ManualEnd] 已回顾 recordId=$recordId level=$mindfulnessLevel dest=$destination"
                    )
                    scheduleEnsureLeftForeground(endedPackage)
                }
                ManualEndDestination.OpenRecord -> {
                    Log.d(TAG, "[ManualEnd] 去心锚定位 recordId=$recordId")
                    // 先离开被监控 App，避免退出心锚后掉回原 App 再弹拦截
                    leaveTargetThenOpenOwnApp { openMainActivityForRecord(recordId) }
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
                            Log.d(TAG, "[ManualEnd] 未回顾 → 延后对照提醒 recordId=$recordId")
                            serviceScope.launch {
                                val rec = usageRecordRepository.getRecordById(recordId)
                                val purpose = rec?.purpose
                                if (!purpose.isNullOrBlank()) {
                                    scheduleCompareReminderIfNeeded(
                                        recordId = recordId,
                                        packageName = endedPackage,
                                        appName = resolveAppNameSync(endedPackage),
                                        purpose = purpose
                                    )
                                } else {
                                    sendSessionEndNotification(endedPackage, recordId)
                                }
                            }
                        }
                    }
                }
            }
        }

        overlayManager.onLaunchPositiveApp = { targetPackage ->
            Log.d(TAG, "[PositiveDest] 启动正向 App pkg=$targetPackage")
            launchApp(targetPackage)
        }

        overlayManager.onPositiveExit = { pkg, choice ->
            Log.d(TAG, "[PositiveExit] 去做了 kind=${choice.kind} title=${choice.title} from=$pkg")
            serviceScope.launch {
                recordPositiveExit(pkg, choice)
                endInterceptFocusHost(pkg, reopenTarget = false)
                pressHomeButton(pauseMediaForPackage = pkg)
                scheduleEnsureLeftForeground(pkg)
                val launchPkg = choice.launchPackageName?.trim().orEmpty()
                if (launchPkg.isNotEmpty() && launchPkg != pkg) {
                    delay(280L)
                    launchApp(launchPkg)
                }
            }
        }

        overlayManager.onOpenPositiveDestinationSettings = {
            Log.d(TAG, "[PositiveDest] 打开想去的地方配置")
            leaveTargetThenOpenOwnApp { openPositiveDestinationSettings() }
        }

        overlayManager.onOpenAppHistory = { targetPackage, recordId ->
            Log.d(TAG, "[LeaveDetail] 打开 App 记录页 pkg=$targetPackage recordId=$recordId")
            leaveTargetThenOpenOwnApp { openMainActivityForAppHistory(targetPackage, recordId) }
        }

        overlayManager.onOpenAppLimitEdit = { targetPackage ->
            Log.d(TAG, "[Intercept] 打开 App 整体设置 pkg=$targetPackage")
            leaveTargetThenOpenOwnApp {
                openMainActivityForAppLimitEdit(targetPackage)
            }
        }

        overlayManager.onOpenQuickIntentTags = { targetPackage, appName ->
            Log.d(TAG, "[Intercept] 打开快捷标签 pkg=$targetPackage")
            leaveTargetThenOpenOwnApp {
                openMainActivityForQuickIntentTags(targetPackage, appName)
            }
        }

        // 离开超时横条：条内保存对照 → 写入档位/备注、清可续（闭环）
        overlayManager.onAwayEndedCompareSaved = { recordId, packageName, level, note, driftSeconds ->
            clearPendingAwayEndedBarForRecord(recordId)
            awayEndedNotifiedRecordIds.remove(recordId)
            awayEndedBarReshowAttempted.remove(recordId)
            Log.d(
                TAG,
                "[AwayEnded] 条内对照已保存 recordId=$recordId pkg=$packageName level=$level drift=$driftSeconds"
            )
            SessionCompareReminderWorker.cancel(this@MonitorForegroundService, recordId)
            val appName = resolveAppNameSync(packageName)
            analyticsRepository.trackAwayBarAction(
                sessionId = recordId,
                action = HaEvents.AwayAction.COMPARE,
                app = appName,
                pkg = packageName
            )
            val trimmed = note?.trim()?.ifBlank { null }
            analyticsRepository.trackCompareSave(
                sessionId = recordId,
                source = HaEvents.Source.AWAY_BAR,
                hasNote = !trimmed.isNullOrBlank(),
                hasLevel = true,
                level = level,
                app = appName,
                pkg = packageName
            )
            appPreferences.recordCompareOutcome(level)
            pendingInterruptStore.clear(packageName)
            serviceScope.launch {
                val existing = usageRecordRepository.getRecordById(recordId)
                if (existing != null) {
                    val resolvedDrift = com.life.mindfulnessapp.domain.model.DriftSecondsPolicy.resolveStored(
                        level = level,
                        driftSeconds = driftSeconds,
                        durationSeconds = existing.durationSeconds
                    )
                    usageRecordRepository.updateRecord(
                        existing.copy(
                            endReason = UsageRecordEntity.EndReason.MANUAL,
                            note = trimmed ?: existing.note,
                            mindfulnessLevel = level,
                            driftSeconds = resolvedDrift
                        )
                    )
                } else {
                    usageRecordRepository.updateNoteAndMindfulness(
                        recordId,
                        trimmed,
                        level,
                        driftSeconds
                    )
                }
            }
        }

        // 环尽未点：保持未闭环，延后对照提醒
        overlayManager.onAwayEndedDismissedWithoutCompare = { recordId, packageName ->
            clearPendingAwayEndedBarForRecord(recordId)
            awayEndedBarReshowAttempted.remove(recordId)
            Log.d(TAG, "[AwayEnded] 未对照 → 保持可续 recordId=$recordId pkg=$packageName")
            analyticsRepository.trackAwayBarAction(
                sessionId = recordId,
                action = HaEvents.AwayAction.KEEP_RESUME,
                app = resolveAppNameSync(packageName),
                pkg = packageName
            )
            serviceScope.launch {
                val rec = usageRecordRepository.getRecordById(recordId)
                scheduleCompareReminderIfNeeded(
                    recordId = recordId,
                    packageName = packageName,
                    appName = resolveAppNameSync(packageName),
                    purpose = rec?.purpose
                )
            }
        }

        overlayManager.onAwayEndedBarAttachFailed = {
            Log.w(TAG, "回顾横条挂接失败，稍后重试")
            scheduleFlushPendingAwayEndedBar(forceAfterFirst = true)
        }

        overlayManager.onOpenHeartAnchorFromDesktop = {
            Log.d(TAG, "[DesktopAnchor] 打开心锚主界面")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        overlayManager.onOpenUsageLogFromDesktop = {
            Log.d(TAG, "[DesktopAnchor] 打开今天收据")
            val intent = TodayReceiptActivity.createIntent(this).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        overlayManager.onOpenScheduleFromDesktop = {
            Log.d(TAG, "[DesktopAnchor] 打开日程表")
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SCHEDULE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        overlayManager.onOpenPlanAddFromDesktop = { pkg ->
            Log.d(TAG, "[DesktopAnchor] 去方案添加 pkg=$pkg")
            val intent = PlanAddAppActivity.createIntent(this, pkg).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }

        overlayManager.syncDesktopAnchor(monitoringActive = true)

        overlayManager.onExtendSession = { extraMinutes ->
            serviceScope.launch {
                val ok = sessionManager.extendBudgetOnce(extraMinutes)
                if (!ok) {
                    Log.w(TAG, "[Extend] extendBudgetOnce 失败")
                    return@launch
                }
                val renewed = sessionManager.currentSession.value ?: return@launch
                if (renewed.isInBackground) {
                    sessionManager.onAppReturnToForeground()
                }
                val live = sessionManager.currentSession.value ?: renewed
                overlayManager.syncCapsuleSessionState(live)
                if (!overlayManager.isCapsuleAttached()) {
                    overlayManager.showCapsule(live, playEnterAnimation = false)
                }
                Log.d(TAG, "[Extend] 胶囊续时 +${extraMinutes} 分 ok pkg=${live.packageName}")
            }
        }

        overlayManager.onKeywordBlocked = { pkg, app ->
            analyticsRepository.trackGateBlockedKeyword(
                interceptId = currentInterceptId.ifBlank { AnalyticsBuckets.newInterceptId() },
                app = app,
                pkg = pkg
            )
        }

        serviceScope.launch {
            combine(
                appLimitRepository.getEnabledAppLimits(),
                planBlockRepository.observeAll()
            ) { limits, plans -> limits to plans }
                .collect { (limits, plans) ->
                    val enabled = limits.filter { it.isEnabled }
                    dualAppRegistry.refresh(enabled)
                    cachedPlanBlocks = plans
                    val monitored = enabled.map { it.packageName }.toSet()
                    cachedMonitoredPackages = monitored
                    val planPkgs = PlanBlockPolicy.watchedPackages(plans, monitored)
                    enabledPackages = monitored + planPkgs
                    interceptCoverPackages = monitored + planPkgs
                    val cloneAliasCount = enabled.count { it.lockClonesEnabled }
                    val systemDualCount = dualAppRegistry.systemDualPackages().size
                    Log.d(
                        TAG,
                        "监控列表更新：${enabledPackages.size} 个 App（含日程锁 ${planPkgs.size}），" +
                            "开门盖层 ${interceptCoverPackages.size} 个" +
                            "，分身同锁主 App $cloneAliasCount 个，系统分身 $systemDualCount 个"
                    )
                }
        }

        // 时段豁免：会话切走（结束或换包）后清掉本轮旁路
        serviceScope.launch {
            var lastExemptSessionPkg: String? = null
            sessionManager.currentSession.collect { session ->
                val livePkg = session?.packageName
                val exempt = periodExemptionPackage
                if (exempt != null &&
                    lastExemptSessionPkg == exempt &&
                    livePkg != exempt
                ) {
                    periodExemptionPackage = null
                    Log.d(TAG, "时段豁免会话结束，清旁路：$exempt")
                }
                if (exempt != null && livePkg == exempt) {
                    lastExemptSessionPkg = exempt
                }
                if (exempt == null) {
                    lastExemptSessionPkg = null
                }
            }
        }

        // 常驻通知刷新：每分钟更新一次今日使用汇总
        startNotificationRefreshJob()
        // 反馈回复低频拉取（辅助通道；主通道仍是进 App 拉取）
        startFeedbackReplySyncJob()
        // 步行觉察：全局路况锚点（偏好关闭时仅待命，开启后自动检测）
        walkAwarenessCoordinator.start(serviceScope, packageName)

        // 注册锁屏/亮屏/屏保广播
        // ACTION_SCREEN_OFF / ACTION_USER_PRESENT / DREAMING_* 只能动态注册
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
        // 强制停止等会发 PACKAGE_RESTARTED；从最近任务划掉通常只杀进程不发此广播，
        // 仍靠暂停态轮询 getPackageImportance 兜底。
        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_RESTARTED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageLifecycleReceiver, packageFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageLifecycleReceiver, packageFilter)
        }
        Log.d(TAG, "锁屏/亮屏/屏保/包重启广播已注册")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userRequestedStop = true
            KeepAliveAlarmScheduler.cancelAll(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob == null || monitorJob?.isActive == false) {
            serviceScope.launch {
                bootstrapSessionAfterServiceStart()
                startMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 用户从最近任务划掉心锚时回调。部分 ROM（含 vivo）会顺带杀进程，
     * 此处用短延迟 Alarm 请求自启，比在回调里立刻 startForegroundService 更稳。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "任务被移除，调度短延迟保活重启")
        KeepAliveAlarmScheduler.scheduleImmediateRestart(this)
        KeepAliveAlarmScheduler.schedulePeriodic(this)
    }

    override fun onDestroy() {
        if (runningInstance === this) runningInstance = null
        markRunning(false)
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(packageLifecycleReceiver) } catch (_: Exception) {}
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        processGoneEndedBarJob?.cancel()
        processGoneEndedBarJob = null
        BackgroundMediaPauser.endSuppressPlayback()
        stopMusicAwayHold()
        notificationRefreshJob?.cancel()
        walkAwarenessCoordinator.stop()
        val stoppingForUser = userRequestedStop
        try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                    if (stoppingForUser) {
                        sessionManager.finalizeAllParkedSessions(
                            UsageRecordEntity.EndReason.APP_CLOSED
                        )
                        sessionManager.endSession(UsageRecordEntity.EndReason.APP_CLOSED)
                        sessionManager.closeOrphanedOpenRecords()
                        sessionManager.clearActiveSessionCheckpoint()
                    } else {
                        sessionManager.syncActiveSessionCheckpoint()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy 会话处理失败", e)
        }
        overlayManager.dismissAll()
        serviceScope.cancel()
        // 非用户主动停止：国产机清理常直接 onDestroy 而不走 onTaskRemoved
        if (!stoppingForUser) {
            Log.w(TAG, "监控异常销毁，调度短延迟保活重启")
            KeepAliveAlarmScheduler.scheduleImmediateRestart(this)
            KeepAliveAlarmScheduler.schedulePeriodic(this)
        }
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
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
     * 服务启动：清理他包脏记录 → 从内存/checkpoint 恢复会话 → 重挂胶囊，避免误弹拦截页。
     */
    private suspend fun bootstrapSessionAfterServiceStart() {
        val exceptRecordId = sessionManager.currentSession.value?.recordId
            ?: sessionManager.peekCheckpointRecordId()
        sessionManager.closeOrphanedOpenRecords(exceptRecordId = exceptRecordId)
        val restored = sessionManager.currentSession.value
            ?: sessionManager.restoreSessionFromCheckpoint()
        if (restored != null) {
            reattachSessionUi(restored)
            Log.d(
                TAG,
                "服务恢复后重挂会话 [${restored.packageName}] " +
                    "background=${restored.isInBackground} " +
                    "accumulated=${restored.accumulatedActiveSeconds}s"
            )
        }
    }

    /** 恢复会话 UI：前台活跃 → 胶囊；后台暂停 → 暂停胶囊 + 离开倒计时 */
    private fun reattachSessionUi(session: UsageSession) {
        lastForegroundPackage = session.packageName
        overlayManager.dismissIntercept(keepCapsule = true)
        if (session.isInBackground) {
            if (session.hasIntentGate) {
                if (shouldHoldCompanionOffForMusic(session)) {
                    beginMusicAwayHold(session.packageName)
                } else {
                    val remain = configuredAwayCountdownSec().coerceAtLeast(1L)
                    armAwayCountdownDeadline(remain)
                    showPausedCapsule(session)
                }
            } else {
                overlayManager.dismissCapsule()
            }
        } else {
            cancelBackgroundTimeout()
            clearAwayCountdownState()
            cancelScreenOffTimeout()
            screenOffPackage = null
            screenOffDeadlineElapsedMs = -1L
            lockedWhilePausedPackage = null
            overlayManager.showCapsule(session, playEnterAnimation = false)
        }
    }

    /** 定期写存活心跳，供清理后恢复检测 */
    private fun startHealthHeartbeat() {
        serviceScope.launch {
            while (true) {
                try {
                    MonitorHealthStore.touchAlive(this@MonitorForegroundService)
                } catch (e: Exception) {
                    Log.w(TAG, "写监控心跳失败", e)
                }
                delay(60_000L)
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
     * 监控服务存活时，每 30 分钟顺带拉取一次开发者回复。
     * 这是辅助通道；可靠送达仍依赖用户打开 App，以及日后推送。
     */
    private fun startFeedbackReplySyncJob() {
        feedbackReplySyncJob?.cancel()
        feedbackReplySyncJob = serviceScope.launch {
            delay(45_000L) // 启动后稍等，避开冷启动高峰
            while (true) {
                try {
                    feedbackInboxRepository.syncReplies(
                        notify = true,
                        minIntervalMs = 0L
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "[FeedbackReply] 拉取失败", e)
                }
                delay(30 * 60_000L)
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
            .setSmallIcon(R.drawable.ic_stat_anchor)
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

    private fun formatAwayEndedTimeAgo(endedAt: Long, endReason: String): String {
        if (endReason == UsageRecordEntity.EndReason.APP_CLOSED) return "刚刚"
        val diff = (System.currentTimeMillis() - endedAt).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 60 * 24 -> "${minutes / 60}小时前"
            else -> "${minutes / (60 * 24)}天前"
        }
    }

    private fun clearPendingAwayEndedBar(packageName: String? = null) {
        val pending = pendingAwayEndedBar ?: return
        if (packageName == null || pending.packageName == packageName) {
            pendingAwayEndedBar = null
        }
    }

    private fun clearPendingAwayEndedBarForRecord(recordId: Long) {
        if (pendingAwayEndedBar?.recordId == recordId) {
            pendingAwayEndedBar = null
        }
    }

    private fun shouldDeferAwayEndedBar(): Boolean =
        screenIsOff || isDeviceLockedOrAsleep()

    private fun shouldSuppressPendingAwayEndedBar(pending: PendingAwayEndedBar): Boolean {
        val packageName = pending.packageName
        val live = sessionManager.currentSession.value
        if (live?.packageName == packageName && !live.isInBackground) {
            return true
        }
        if (overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage == packageName
        ) {
            return true
        }
        if (pending.endReason == UsageRecordEntity.EndReason.APP_CLOSED &&
            lastForegroundPackage == packageName
        ) {
            return true
        }
        return false
    }

    private fun presentAwayEndedBar(
        packageName: String,
        appName: String,
        recordId: Long,
        purpose: String?,
        endedAt: Long,
        durationSeconds: Long,
        endReason: String,
        force: Boolean = false
    ) {
        serviceScope.launch {
            val limit = appLimitRepository.getAppLimit(packageName)
            val record = usageRecordRepository.getRecordById(recordId)
            val intentKind = com.life.mindfulnessapp.domain.model.IntentKind.fromStorage(record?.intentKind)
            val hasIntentGate = limit?.requireIntentOnOpen == true

            val payload = PendingAwayEndedBar(
                packageName = packageName,
                appName = appName,
                recordId = recordId,
                purpose = purpose,
                intentKind = intentKind,
                endedAt = endedAt,
                durationSeconds = durationSeconds,
                endReason = endReason
            )
            if (ComparePolicy.shouldSkipActiveCompare(
                    hasIntentGate = hasIntentGate,
                    purpose = purpose,
                    durationSeconds = durationSeconds,
                    compareEnabled = limit?.compareEnabled == true,
                    compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                        limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                    ),
                    intentKind = intentKind,
                    practiceEnabled = appPreferences.isAwarenessPracticeEnabled()
                )
            ) {
                pendingAwayEndedBar = null
                Log.d(TAG, "[$packageName] 未达对照门槛 / 觉察练习关 → 跳过横条与提醒")
                return@launch
            }
            if (shouldSuppressPendingAwayEndedBar(payload)) {
                pendingAwayEndedBar = null
                Log.d(TAG, "[$packageName] 回顾横条已抑制（用户已回到 App）")
                return@launch
            }
            if (!force && shouldDeferAwayEndedBar()) {
                flushAwayEndedBarJob?.cancel()
                pendingAwayEndedBar = null
                sendAwayEndedCompareNotification(
                    packageName = packageName,
                    appName = appName,
                    recordId = recordId,
                    purpose = purpose
                )
                Log.d(TAG, "[$packageName] 锁屏/息屏期间离开超时 → 发对照通知（不弹横条）")
                return@launch
            }
            pendingAwayEndedBar = payload
            showAwayEndedBarFromPending(payload)
        }
    }

    private fun showAwayEndedBarFromPending(payload: PendingAwayEndedBar) {
        overlayManager.showAwayEndedBar(
            appName = payload.appName,
            packageName = payload.packageName,
            recordId = payload.recordId,
            timeAgoLabel = formatAwayEndedTimeAgo(payload.endedAt, payload.endReason),
            durationSeconds = payload.durationSeconds,
            purpose = payload.purpose,
            intentKind = payload.intentKind
        )
    }

    private suspend fun maybeSendAwayEndedNotificationIfNeeded(packageName: String) {
        if (pendingAwayEndedBar != null) return
        val interrupt = pendingInterruptStore.get(packageName) ?: return
        if (interrupt.endReason != UsageRecordEntity.EndReason.AWAY_COUNTDOWN) return
        if (awayEndedNotifiedRecordIds.contains(interrupt.recordId)) return
        val record = usageRecordRepository.getRecordById(interrupt.recordId) ?: return
        if (UsageRecordEntity.MindfulnessLevel.isValid(record.mindfulnessLevel)) return
        sendAwayEndedCompareNotification(
            packageName = interrupt.packageName,
            appName = interrupt.appName,
            recordId = interrupt.recordId,
            purpose = interrupt.purpose
        )
        Log.d(TAG, "[$packageName] 亮屏补发离开超时对照通知")
    }

    private fun flushPendingAwayEndedBar(force: Boolean = false) {
        val pending = pendingAwayEndedBar ?: return
        if (!force && shouldDeferAwayEndedBar()) return
        if (shouldSuppressPendingAwayEndedBar(pending)) {
            pendingAwayEndedBar = null
            return
        }
        if (overlayManager.isAwayEndedBarShowing()) return
        // 息屏打断后已重展过一次：再锁再亮不再循环弹条
        if (force && awayEndedBarReshowAttempted.contains(pending.recordId)) {
            Log.d(TAG, "[${pending.packageName}] 回顾横条已重展过，改发通知避免循环")
            sendAwayEndedCompareNotification(
                packageName = pending.packageName,
                appName = pending.appName,
                recordId = pending.recordId,
                purpose = pending.purpose
            )
            pendingAwayEndedBar = null
            return
        }
        if (force) {
            awayEndedBarReshowAttempted.add(pending.recordId)
        }
        showAwayEndedBarFromPending(pending)
        Log.d(TAG, "[${pending.packageName}] 展示回顾横条 force=$force")
    }

    /**
     * 息屏打断了正在显示的横条时，解锁后重试挂上。
     * 暂停胶囊锁屏超时走 [sendAwayEndedCompareNotification]，不在此列。
     */
    private fun scheduleFlushPendingAwayEndedBar(forceAfterFirst: Boolean) {
        if (pendingAwayEndedBar == null) return
        flushAwayEndedBarJob?.cancel()
        flushAwayEndedBarJob = serviceScope.launch {
            val delays = if (forceAfterFirst) {
                longArrayOf(0L, 180L, 420L, 800L)
            } else {
                longArrayOf(120L, 360L, 720L)
            }
            for ((index, wait) in delays.withIndex()) {
                if (wait > 0L) delay(wait)
                val pending = pendingAwayEndedBar ?: return@launch
                if (overlayManager.isAwayEndedBarShowing()) return@launch
                if (shouldSuppressPendingAwayEndedBar(pending)) {
                    pendingAwayEndedBar = null
                    return@launch
                }
                val force = forceAfterFirst || index >= 1
                flushPendingAwayEndedBar(force = force)
                if (overlayManager.isAwayEndedBarShowing()) return@launch
            }
        }
    }

    /**
     * 通过 UsageEvents 获取当前前台 App 包名，排除自身。
     *
     * 设计原则：
     * - 离开判定：只认进程级 [MOVE_TO_BACKGROUND]，**绝不**用 ACTIVITY_PAUSED
     *   （App 内页面切换会产生 PAUSED/RESUMED，顺序因 ROM 而异，易误判离前台）。
     * - 进入判定：MOVE_TO_FOREGROUND + ACTIVITY_RESUMED。
     *   部分 OEM 上 MOVE_TO_FOREGROUND 会拖到用户首次触摸才写入；ACTIVITY_RESUMED
     *   通常在窗口真正可见时就有，用于让意图门立刻盖上（对齐 AppBlock 体感）。
     * - 同包名内的 ACTIVITY_RESUMED 只会再次确认前台，不会重复弹门（同包 early-return）。
     *
     * 查询策略：30 分钟窗口扫描进入/离开事件，取最后仍为前台的包。
     */
    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        // 30 分钟的查询窗口，覆盖绝大多数正常使用时长
        val events = usageStatsManager.queryEvents(now - 30 * 60_000L, now)
        val event = android.app.usage.UsageEvents.Event()

        // key: packageName, value: Pair(是否在前台, 最后一次相关事件的时间戳)
        val processState = mutableMapOf<String, Pair<Boolean, Long>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName) continue  // 排除监控服务自身
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val prev = processState[event.packageName]
                    if (prev == null || event.timeStamp > prev.second) {
                        processState[event.packageName] = Pair(true, event.timeStamp)
                    }
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // 进程级：App 进入后台（不用 ACTIVITY_PAUSED）
                    val prev = processState[event.packageName]
                    if (prev == null || event.timeStamp > prev.second) {
                        processState[event.packageName] = Pair(false, event.timeStamp)
                    }
                }
            }
        }

        // 优先：窗口内最后状态为前台的包
        val detected = processState.entries
            .filter { it.value.first }
            .maxByOrNull { it.value.second }
            ?.key

        // 系统分身（华为 128 等）UsageStats 常仍报机主侧旧前台。
        // 一旦分身空间探测到受监控包在前台，优先认它。
        val dualFg = dualAppRegistry.probeSystemDualForeground()
        if (dualFg != null) {
            systemDualForegroundHint = dualFg
            if (detected != dualFg) {
                Log.d(TAG, "分身空间前台兜底: $dualFg (usageStats=$detected)")
            }
            return dualFg
        }

        if (detected != null) return detected

        // 兜底：窗口内没有任何进入事件
        // 若 lastForegroundPackage 在窗口内也没有 MOVE_TO_BACKGROUND，
        // 说明进入前台已超过窗口且从未离开 → 仍在前台
        val lastPkg = lastForegroundPackage ?: return null
        val lastPkgState = processState[lastPkg]
        return when {
            lastPkgState != null && !lastPkgState.first -> null
            lastPkgState == null -> lastPkg
            else -> null
        }
    }

    private suspend fun handleForegroundChange(
        rawPackage: String?,
        fromAccessibility: Boolean = false
    ) {
        val currentPkg = rawPackage?.let { dualAppRegistry.canonicalize(it) }
        // 音量条 / 通知浮层等 SystemUI：不是真的离开 App，勿启动离开防抖、勿重弹呼吸门
        if (isSystemUiPackage(currentPkg) || isTransientSystemOverlayPackage(currentPkg)) {
            Log.d(TAG, "忽略系统浮层前台变化 $currentPkg")
            return
        }
        // 音量 HUD 等常把当前 App 标成 MOVE_TO_BACKGROUND 且读不到新前台 → null。
        // 若仍有前台活会话，绝不能当离开，否则防抖期满进后台，松手立刻弹呼吸门。
        if (currentPkg == null) {
            val live = sessionManager.currentSession.value
            if (live != null && !live.isInBackground) {
                Log.d(TAG, "前台包名为空，会话仍在前台，忽略（疑似音量/系统浮层）")
                return
            }
        }
        // 结束确认 / 续时弹窗期间会话会被临时标成后台以冻结计时。
        // 若此处仍走「回前台 → showCapsule」，会拆掉刚弹出的浮层（表现为点结束无反应）。
        if (overlayManager.isCapsuleDialogBlocking.get()) {
            Log.d(TAG, "胶囊结束确认/续时弹窗中，忽略前台切换 $lastForegroundPackage -> $currentPkg")
            return
        }
        if (handleReturnFromRecents(currentPkg, fromAccessibility)) {
            return
        }
        walkAwarenessCoordinator.onForegroundPackage(currentPkg)
        noteOpenVisitForeground(currentPkg)
        if (isOpenVisitProtected(openVisitPackage)) {
            val held = openVisitPackage
            val live = sessionManager.currentSession.value
            if (held != null && live?.packageName == held) {
                if (currentPkg == held) {
                    lastForegroundPackage = held
                    // 灭屏宽限期间已回到同包且设备可交互：必须续上，勿被 open-visit 早退挡住
                    if (live.isInBackground &&
                        screenOffPackage == held &&
                        !isDeviceLockedOrAsleep()
                    ) {
                        serviceScope.launch {
                            tryResumeAfterScreenOff(held, reason = "open-visit-same-pkg")
                        }
                        return
                    }
                    val screenPause = isDeviceLockedOrAsleep() || screenOffPackage == held
                    if (live.isInBackground && !screenPause) {
                        sessionManager.onAppReturnToForeground()
                    }
                    val showing = sessionManager.currentSession.value ?: live
                    if (!screenPause && !showing.isInBackground && !overlayManager.isCapsuleAttached()) {
                        overlayManager.showCapsule(showing, playEnterAnimation = false)
                    }
                    return
                }
                // 进门后还没看见 App：保护窗内可拉回；窗过仍停桌面则结束保护，按离开走
                if (!openVisitSawApp && isLauncherPackage(currentPkg)) {
                    if (isGateEnterHoldActive(held)) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - openVisitRelaunchElapsed > 1_200L) {
                            openVisitRelaunchElapsed = now
                            launchApp(held)
                        }
                        return
                    }
                    Log.d(TAG, "$held 进门后停在桌面，结束进门保护并按离开收口")
                    clearOpenVisit(held)
                    // 落入下方正常离开判定（勿在桌面误 resume）
                } else if (!openVisitSawApp) {
                    return
                } else {
                    // sawApp + 锁屏保护：保持
                    return
                }
            }
        }
        if (currentPkg != null &&
            accountingHomePackage == currentPkg &&
            overlayManager.hasInterceptUiAttached() &&
            overlayManager.interceptTargetPackage == currentPkg &&
            !isGateEnterHoldActive(currentPkg)
        ) {
            parkTargetBehindIntercept(currentPkg)
            lastForegroundPackage = currentPkg
            return
        }
        if (isGateEnterHoldActive()) {
            val held = gateEnterHoldPackage
            if (currentPkg == held) {
                val live = sessionManager.currentSession.value
                val entering = overlayManager.gateEnteringPackage == held
                val hasLiveSession = live?.packageName == held
                if (hasLiveSession || entering) {
                    lastForegroundPackage = currentPkg
                    // 进门保护窗：会话/胶囊尚未挂上，勿重弹拦截或时长锁
                    return
                }
                // 保护窗未过但会话已无（很快离开又进）：放行重新开门，勿干等数秒
                Log.d(TAG, "进门保护窗内无会话，清除后允许再拦截 $currentPkg")
                overlayManager.clearGateEnter(held)
                gateEnterHoldPackage = null
                gateEnterHoldUntilElapsed = 0L
            } else {
                Log.d(TAG, "拦截确认进入中，忽略假离开 $lastForegroundPackage -> $currentPkg")
                return
            }
        }

        // 任意轮询：后台/暂停会话若已跨入时段锁，先静默收口并拆胶囊
        val pausedOrAway = sessionManager.currentSession.value
        if (pausedOrAway != null && pausedOrAway.isInBackground) {
            val awayLimit = appLimitRepository.getAppLimit(pausedOrAway.packageName)
            if (isPeriodHardLocked(awayLimit, pausedOrAway.packageName)) {
                enforcePeriodLockWhileAway(pausedOrAway.packageName)
            }
        }

        val prevPkg = lastForegroundPackage

        if (screenOffForcedExitPkg != null && isLauncherPackage(currentPkg)) {
            Log.d(TAG, "${screenOffForcedExitPkg} 息屏超时后已回到桌面")
            screenOffForcedExitPkg = null
        }
        // 意图门：无障碍/UsageStats 报到桌面 → 立刻等同「不进去了」
        if (tryDismissIntentGateOnHome(currentPkg, fromAccessibility)) return

        if (currentPkg == prevPkg) {
            // 同包轮询也会漏门：第一次进入时 lastForegroundPackage 可能已是该 App
            //（eager cover / 上次残留），后面永远走不到 showInterceptOverlay。
            if (currentPkg != null && ensureIntentGateIfNeeded(currentPkg)) return
            // 锁屏宽限期内：部分机型 UsageStats 仍报告同一包名（无 FOREGROUND 变化），
            // 若不在此处恢复，会卡在「计时冻结 + 胶囊已dismiss」且最终误走超时结束。
            if (currentPkg != null && screenOffPackage == currentPkg) {
                if (tryResumeAfterScreenOff(currentPkg, reason = "same-pkg-poll")) return
            }
            // 拦截页/超限页正在展示期间：默认跳过超限检查，避免重复触发。
            // 另判 Home：UsageStats 有时仍报原 App，但桌面已露出。
            if (overlayManager.isInterceptLayerOnScreen(currentPkg)) {
                val pkg = currentPkg
                if (pkg != null && shouldUpgradeInterceptPlaceholder(pkg)) {
                    Log.d(TAG, "$pkg 仅占位层在屏上，补挂意图门")
                    showInterceptOverlay(pkg)
                    return
                }
                maybeHomeLeaveWhileSamePackage(currentPkg)
                return
            }
            val session = sessionManager.currentSession.value
            // 强停 / 划掉后 UsageStats 常仍报本包：同包轮询也要探测进程已死，
            // 否则永远进不了后台防抖，意图门也就出不了离开横条。
            if (session != null &&
                currentPkg != null &&
                session.packageName == currentPkg &&
                PackageProcessLiveness.isProcessGone(this, currentPkg)
            ) {
                Log.d(TAG, "$currentPkg UsageStats 仍报本包但进程已死，按进程已死处理")
                onMonitoredProcessGone(currentPkg)
                return
            }
            if (session != null && !session.isInBackground) {
                // SCREEN_OFF 偶发漏接时，UsageStats 仍报同包「前台」→ 会偷偷把锁屏时长算进去
                if (isDeviceLockedOrAsleep()) {
                    pauseForegroundSessionForScreenObscure(session, "同包轮询·锁屏兜底")
                    return
                }
                if (isGateEnterHoldActive(session.packageName)) return
                // 会话中跨入时段锁：硬踢（优先于日限/单次限）
                val liveLimit = appLimitRepository.getAppLimit(session.packageName)
                if (isPeriodHardLocked(liveLimit, session.packageName)) {
                    handlePeriodLock(session.packageName, endActiveSession = true)
                    return
                }
                if (session.isSessionLimitReached) {
                    handleSessionLimitReached(session.packageName)
                } else if (session.isDailyLimitExceeded || session.isWeeklyLimitExceeded) {
                    // 超限续记 session：用户已明确知晓超限并主动选择继续，不再重复弹超限页
                    // 单次时长续时后：先走完会话续时额度，日锁不得抢先收口（否则胶囊刚恢复就被拆掉）
                    val sessionExtendActive = session.hasSessionLimit &&
                        session.sessionExtensionSeconds > 0L &&
                        !session.isSessionLimitReached
                    if (!session.isOverLimitSession && !sessionExtendActive) {
                        handleLimitExceeded(session.packageName)
                    }
                }
            } else {
                // 同包轮询：未进规则的 App 保持系统账使用中态
                syncUnmonitoredDesktopInAppPresence(currentPkg)
            }
            return
        }

        // ── 处理新进入前台的 App（优先级最高）────────────────────────────────
        // 若被监控 App 重新回到前台，立即取消防抖计时并恢复会话，避免误触发后台逻辑
        if (currentPkg != null && enabledPackages.contains(currentPkg)) {
            val existingSession = sessionManager.currentSession.value

            // 如果有防抖计时正在等待（即被监控 App 刚离开又马上回来），直接取消，不触发后台
            val quickReturnFromLeave = backgroundDebounceJob?.isActive == true
            if (quickReturnFromLeave) {
                cancelBackgroundDebounce()
                Log.d(TAG, "$currentPkg 快速回到前台，取消后台防抖计时")
            }

            // 回到当前会话包：取消未确认的监控互切（闪报回调）
            if (existingSession?.packageName == currentPkg) {
                cancelMonitoredSwitchDebounce()
            }

            // 另一包仍有前台活会话：先防抖，确认后再收口（真互切仍走立即 SWITCHED_AWAY）
            val foreignLive = existingSession?.takeIf {
                it.packageName != currentPkg && !it.isInBackground
            }
            if (foreignLive != null) {
                noteMonitoredSwitchCandidate(
                    from = foreignLive.packageName,
                    to = currentPkg
                )
                return
            }

            lastForegroundPackage = currentPkg
            Log.d(TAG, "前台切换: $prevPkg -> $currentPkg")
            overlayManager.clearUnmonitoredDesktopInApp()

            when {
                // 仅当正式意图门已挂上才跳过；纯色占位必须升级，否则首进永远没门
                overlayManager.isInterceptLayerOnScreen(currentPkg) &&
                    overlayManager.interceptTargetPackage == currentPkg -> {
                    if (shouldUpgradeInterceptPlaceholder(currentPkg)) {
                        Log.d(TAG, "$currentPkg 仅占位层在屏上，补挂意图门")
                        showInterceptOverlay(currentPkg)
                    } else {
                        Log.d(TAG, "$currentPkg 拦截弹窗正在展示中，跳过本轮")
                    }
                }
                existingSession?.packageName == currentPkg && existingSession.isInBackground -> {
                    // 灭屏宽限恢复不算「桌面假回报」——锁屏事件会污染 UsageEvents
                    if (screenOffPackage != currentPkg &&
                        isSpuriousAppForegroundWhileAway(currentPkg)
                    ) {
                        Log.d(TAG, "$currentPkg 桌面离开后的假回报，保持暂停胶囊")
                        // 勿把 lastForeground 钉成 App，否则后续桌面轮询走同包分支永远不进离开
                        when {
                            isLauncherPackage(prevPkg) -> lastForegroundPackage = prevPkg
                            prevPkg != null && prevPkg != currentPkg ->
                                lastForegroundPackage = prevPkg
                            leftToLauncherWhileAway -> {
                                // 保持非 App 标记，下一轮若报到桌面可正常走离开防抖
                                lastForegroundPackage = prevPkg ?: lastForegroundPackage
                            }
                        }
                        if (backgroundTimeoutJob?.isActive != true) {
                            startAwayCountdown(currentPkg)
                        }
                        showPausedCapsule(existingSession)
                        return
                    }
                    val limit = appLimitRepository.getAppLimit(currentPkg)
                    if (isPeriodHardLocked(limit, currentPkg)) {
                        Log.d(TAG, "$currentPkg 后台恢复时时段锁已生效，硬踢")
                        handlePeriodLock(currentPkg, endActiveSession = true)
                    } else if (screenOffPackage == currentPkg) {
                        Log.d(TAG, "$currentPkg 灭屏后回到前台，先看时段锁")
                        serviceScope.launch { tryResumeAfterScreenOff(currentPkg, reason = "return") }
                    } else if (isAwayGraceHolding(currentPkg)) {
                        Log.d(TAG, "$currentPkg 离开宽限内回来，续上这一次，不再过门")
                        resumeBackgroundSession(currentPkg, existingSession)
                    } else if (quickReturnFromLeave || isBriefBackgroundNoise()) {
                        // 防抖未落定 / 刚进后台又回来：音量条噪声，直接续上，勿弹呼吸门
                        Log.d(TAG, "$currentPkg 短暂离开噪声，静默续上")
                        dismissForeignInterceptIfNeeded(currentPkg)
                        resumeBackgroundSession(currentPkg, existingSession)
                    } else if (limit != null && !limit.requireIntentOnOpen) {
                        tryEagerInterceptCover(currentPkg)
                        if (showPlanDoor(currentPkg, limit) { choice ->
                            // 用户在门口写了意图：必须开新会话挂上胶囊，不能只 resume 旧的无意图会话
                            if (choice.text.isNotBlank() || choice.search || choice.minutes > 0) {
                                enterWithoutIntentGate(currentPkg, limit.appName, choice)
                            } else {
                                resumeBackgroundSession(currentPkg, existingSession)
                            }
                        }) {
                            Log.d(TAG, "$currentPkg 从后台回来，先过门口")
                        } else {
                            dismissForeignInterceptIfNeeded(currentPkg)
                            resumeBackgroundSession(currentPkg, existingSession)
                        }
                    } else if (isMusicAwayHold(currentPkg)) {
                        // 听歌离开：宽限清掉了，但仍是同一次，回来续上
                        Log.d(TAG, "$currentPkg 听歌离开后回来，续上这一次")
                        dismissForeignInterceptIfNeeded(currentPkg)
                        resumeBackgroundSession(currentPkg, existingSession)
                    } else if (limit?.requireIntentOnOpen == true) {
                        // 宽限已过 / 倒计时态丢失，却仍挂着后台会话：
                        // 旧逻辑会静默 resume，表现为「有时开门不弹」。收口后重新过门。
                        Log.d(TAG, "$currentPkg 后台会话已过离开宽限，收口后重新过门")
                        stopMusicAwayHold()
                        clearAwayCountdownState()
                        serviceScope.launch {
                            val ending = sessionManager.currentSession.value
                            if (ending != null &&
                                ending.packageName == currentPkg &&
                                ending.isInBackground
                            ) {
                                endStaleBackgroundSessionForGate(ending)
                            }
                            tryEagerInterceptCover(currentPkg)
                            showInterceptOverlay(currentPkg)
                        }
                    } else {
                        dismissForeignInterceptIfNeeded(currentPkg)
                        resumeBackgroundSession(currentPkg, existingSession)
                    }
                }
                existingSession?.packageName == currentPkg && !existingSession.isInBackground -> {
                    // 离开防抖未落定又回来（音量条/通知栏等）：会话从未进后台，继续用，不必再过门
                    if (quickReturnFromLeave) {
                        Log.d(TAG, "$currentPkg 离开未落定又回来，会话仍在前台，跳过门口")
                    } else {
                        Log.d(TAG, "$currentPkg 已在前台运行中，跳过")
                    }
                }
                else -> {
                    // 后台挂起中的他包会话：用户已离开过，互切无需再防抖
                    val foreign = sessionManager.currentSession.value
                    if (foreign != null && foreign.packageName != currentPkg) {
                        tryEagerInterceptCover(currentPkg)
                        parkSessionForMonitoredAppSwitch(foreign.packageName)
                    }
                    val parkedWaiting = sessionManager.hasParkedSession(currentPkg)
                    val parkedLimit = appLimitRepository.getAppLimit(currentPkg)
                    if (parkedWaiting && parkedLimit != null && !parkedLimit.requireIntentOnOpen &&
                        showPlanDoor(currentPkg, parkedLimit) { choice ->
                            val restored = sessionManager.restoreParkedSession(currentPkg)
                            if (restored != null) resumeBackgroundSession(currentPkg, restored)
                            else enterWithoutIntentGate(currentPkg, parkedLimit.appName, choice)
                        }
                    ) {
                        Log.d(TAG, "$currentPkg 挂起回切，先过门口")
                    } else {
                    val restored = sessionManager.restoreParkedSession(currentPkg)
                    if (restored != null) {
                        Log.d(TAG, "$currentPkg 从监控互切挂起中无缝恢复")
                        if (overlayManager.interceptTargetPackage == currentPkg) {
                            overlayManager.dismissIntercept(keepCapsule = true)
                        }
                        dismissForeignInterceptIfNeeded(currentPkg)
                        val limit = appLimitRepository.getAppLimit(currentPkg)
                        if (isPeriodHardLocked(limit, currentPkg)) {
                            handlePeriodLock(currentPkg, endActiveSession = true)
                        } else {
                            resumeBackgroundSession(currentPkg, restored)
                        }
                    } else {
                        tryEagerInterceptCover(currentPkg)
                        val limit = appLimitRepository.getAppLimit(currentPkg)
                        if (isPeriodHardLocked(limit, currentPkg)) {
                            Log.d(TAG, "$currentPkg 时段锁生效，展示硬挡页")
                            handlePeriodLock(currentPkg, endActiveSession = false)
                        } else if (limit != null && !limit.requireIntentOnOpen) {
                            if (pendingInterruptStore.get(currentPkg) != null) {
                                pendingInterruptStore.clear(currentPkg)
                                Log.d(TAG, "$currentPkg 意图门关闭，丢弃残留中断确认快照")
                            }
                            if (showPlanDoor(currentPkg, limit) { choice ->
                                    enterWithoutIntentGate(currentPkg, limit.appName, choice)
                                }
                            ) {
                                Log.d(TAG, "$currentPkg 意图门关，走呼吸或硬挡")
                            } else {
                                Log.d(TAG, "$currentPkg 意图门关闭且未触界，无拦截进入")
                                enterWithoutIntentGate(currentPkg, limit.appName)
                            }
                        } else if (limit == null) {
                            // 仅因守计划被监视、当前未锁：拆掉误盖层，不拦截、不建会话
                            if (overlayManager.interceptTargetPackage == currentPkg) {
                                overlayManager.dismissIntercept()
                            }
                            Log.d(TAG, "$currentPkg 守计划监视中但未到点，忽略")
                        } else {
                            Log.d(TAG, "显示拦截浮窗: $currentPkg，existingSession=$existingSession")
                            showInterceptOverlay(currentPkg)
                        }
                    }
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
        // Home 分流：
        //  - 广告播放中 → 不关
        //  - 意图门 → 关页 + 守住轻条
        //  - 日限 / 时段锁 → 静默关页（硬墙，走开即可）
        //  - 意图时长到点 → 关页 + 稍后回顾通知
        val interceptTarget = overlayManager.interceptTargetPackage
        if (interceptTarget != null &&
            overlayManager.isInterceptLayerOnScreen(interceptTarget)
        ) {
            // 焦点已在心锚托管页：目标 App 已推离前台，保留拦截，不记离开
            if (currentPkg == packageName) {
                lastForegroundPackage = currentPkg
                return
            }
            if (tryDismissIntentGateOnHome(currentPkg, fromAccessibility)) return
            if (prevPkg == interceptTarget &&
                shouldBeginInterceptLeave(currentPkg, interceptTarget, fromAccessibility)
            ) {
                beginInterceptHomeLeave(interceptTarget, currentPkg)
                return
            }
            // 托管页仍在时，prev 可能是心锚包名；切到桌面仍须能走守住
            if (DualSpaceGateActivity.isShowing() &&
                prevPkg == packageName &&
                shouldBeginInterceptLeave(currentPkg, interceptTarget, fromAccessibility)
            ) {
                beginInterceptHomeLeave(interceptTarget, currentPkg)
                return
            }
        }

        val session = sessionManager.currentSession.value
        if (session != null && !session.isInBackground && isDeviceLockedOrAsleep()) {
            pauseForegroundSessionForScreenObscure(session, "锁屏/屏保期间前台变化")
            lastForegroundPackage = currentPkg
            return
        }
        if (prevPkg != null && session != null && session.packageName == prevPkg && !session.isInBackground) {
            // 音量条 / 厂商浮层偶发报到奇怪包名：不当离开，保持会话与 lastForeground
            if (!isPlausibleUserLeaveDestination(currentPkg)) {
                Log.d(TAG, "$prevPkg 疑似被系统浮层遮挡（报到 $currentPkg），不启动离开防抖")
                return
            }
            // 切换到非被监控 App 或系统 UI（含通知栏、桌面等）
            // 防抖：延迟 BACKGROUND_DEBOUNCE_MS 后再确认是否真正进入后台
            // 如果用户只是拉了下通知栏或系统弹框，很快就会回来，防抖期间不做任何处理
            cancelBackgroundDebounce()
            cancelMonitoredSwitchDebounce()
            val debouncePackage = prevPkg
            Log.d(TAG, "$prevPkg 疑似离开前台（切换到 $currentPkg），启动防抖计时 ${BACKGROUND_DEBOUNCE_MS}ms")
            backgroundDebounceJob = serviceScope.launch {
                delay(BACKGROUND_DEBOUNCE_MS)
                if (isDeviceLockedOrAsleep()) {
                    val paused = sessionManager.currentSession.value
                    if (paused != null && paused.packageName == debouncePackage && !paused.isInBackground) {
                        pauseForegroundSessionForScreenObscure(paused, "离开防抖遇上锁屏/屏保")
                    }
                    return@launch
                }
                // 防抖期满后，再次检查：被监控 App 是否仍然不在前台
                if (lastForegroundPackage != debouncePackage) {
                    val currentSession = sessionManager.currentSession.value
                    if (currentSession != null && currentSession.packageName == debouncePackage && !currentSession.isInBackground) {
                        // 进入后台暂停 + 超时等待。
                        // 进程是否已死见 [PackageProcessLiveness]（getPackageImportance）；
                        // 勿用 getRunningAppProcesses——Android 11+ 基本只能看到本应用。
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
            syncUnmonitoredDesktopInAppPresence(currentPkg)
        } else {
            // 没有活跃会话，或当前包名与会话不符，直接更新前台包名
            lastForegroundPackage = currentPkg
            Log.d(TAG, "前台切换: $prevPkg -> $currentPkg")
            syncUnmonitoredDesktopInAppPresence(currentPkg)
        }
    }

    /**
     * 桌面球使用中态：前台还没加进规则的普通 App → 系统用量 Pulse；
     * 桌面 / 系统页 / 已在规则里 → 拆掉系统账态（心锚会话另有 present）。
     */
    private fun syncUnmonitoredDesktopInAppPresence(pkg: String?) {
        when {
            pkg.isNullOrBlank() -> overlayManager.clearUnmonitoredDesktopInApp()
            pkg == packageName -> overlayManager.clearUnmonitoredDesktopInApp()
            isLauncherPackage(pkg) -> overlayManager.clearUnmonitoredDesktopInApp()
            isSystemUiPackage(pkg) || isTransientSystemOverlayPackage(pkg) -> Unit
            enabledPackages.contains(dualAppRegistry.canonicalize(pkg)) ->
                overlayManager.clearUnmonitoredDesktopInApp()
            !isPlausibleUserLeaveDestination(pkg) -> Unit
            else -> overlayManager.ensureUnmonitoredDesktopInApp(pkg)
        }
    }

    /** 刚被标成后台又马上回来：音量 / 通知栏噪声，不应重开门。 */
    private fun isBriefBackgroundNoise(): Boolean {
        val at = sessionBackgroundAtElapsed
        if (at <= 0L) return false
        return SystemClock.elapsedRealtime() - at < BRIEF_BACKGROUND_RESUME_MS
    }

    /**
     * 只有桌面 / 其他用户 App 才算「真离开」。
     * 音量条、控制中心、权限框等系统壳报到的包名一律忽略。
     */
    private fun isPlausibleUserLeaveDestination(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (isSystemUiPackage(pkg) || isTransientSystemOverlayPackage(pkg)) return false
        if (isLauncherPackage(pkg)) return true
        if (enabledPackages.contains(dualAppRegistry.canonicalize(pkg))) return true
        val p = pkg.lowercase()
        // 明显的系统壳 / 插件 / 弹层（勿依赖 launcher 查询：包可见性会把真 App 误判成无入口）
        if (p == "android" ||
            p.startsWith("com.android.systemui") ||
            p.contains("systemui") ||
            p.contains("volume") ||
            p.contains("controlcenter") ||
            p.contains("screenshot") ||
            p.contains("permissioncontroller") ||
            p.contains("packageinstaller") ||
            p.contains("vpndialogs") ||
            p.contains("globalactions") ||
            p.endsWith(".ops.systemui")
        ) {
            return false
        }
        return true
    }

    private fun cancelBackgroundDebounce() {
        backgroundDebounceJob?.cancel()
        backgroundDebounceJob = null
    }

    private fun cancelMonitoredSwitchDebounce() {
        if (monitoredSwitchDebounceJob?.isActive == true) {
            Log.d(
                TAG,
                "取消监控互切防抖：" +
                    "${monitoredSwitchDebounceFromPkg} → ${monitoredSwitchDebounceToPkg}"
            )
        }
        monitoredSwitchDebounceJob?.cancel()
        monitoredSwitchDebounceJob = null
        monitoredSwitchDebounceFromPkg = null
        monitoredSwitchDebounceToPkg = null
    }

    /**
     * 前台活会话下看到另一监控包：启动/续上互切防抖，期满再 [commitMonitoredAppSwitch]。
     * 不改 [lastForegroundPackage]，以便回切原包或切桌面仍走既有路径。
     */
    private fun noteMonitoredSwitchCandidate(from: String, to: String) {
        if (monitoredSwitchDebounceJob?.isActive == true &&
            monitoredSwitchDebounceFromPkg == from &&
            monitoredSwitchDebounceToPkg == to
        ) {
            return
        }
        if (monitoredSwitchDebounceJob?.isActive == true &&
            monitoredSwitchDebounceFromPkg == from &&
            monitoredSwitchDebounceToPkg != to
        ) {
            Log.d(
                TAG,
                "监控互切防抖改目标：$from → $monitoredSwitchDebounceToPkg 改为 → $to"
            )
        }
        cancelMonitoredSwitchDebounce()
        monitoredSwitchDebounceFromPkg = from
        monitoredSwitchDebounceToPkg = to
        Log.d(
            TAG,
            "监控互切防抖：$from → $to，${BACKGROUND_DEBOUNCE_MS}ms 后确认"
        )
        monitoredSwitchDebounceJob = serviceScope.launch {
            delay(BACKGROUND_DEBOUNCE_MS)
            val pendingFrom = monitoredSwitchDebounceFromPkg
            val pendingTo = monitoredSwitchDebounceToPkg
            monitoredSwitchDebounceJob = null
            monitoredSwitchDebounceFromPkg = null
            monitoredSwitchDebounceToPkg = null
            if (pendingFrom == null || pendingTo == null) return@launch
            commitMonitoredAppSwitch(pendingFrom, pendingTo)
        }
    }

    /**
     * 互切防抖期满：UsageStats 仍指向他包则正式收口并进入目标；
     * 若已回到原包则作罢；若落到非监控包则走普通离开路径。
     */
    private suspend fun commitMonitoredAppSwitch(from: String, to: String) {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != from || session.isInBackground) {
            handleForegroundChange(to)
            return
        }
        if (isDeviceLockedOrAsleep()) {
            if (!session.isInBackground) {
                pauseForegroundSessionForScreenObscure(session, "监控互切防抖遇上锁屏/屏保")
            }
            return
        }
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val fg = usageStatsManager
            ?.let { getForegroundPackage(it) }
            ?.let { dualAppRegistry.canonicalize(it) }
        when {
            fg == from -> {
                Log.d(TAG, "监控互切防抖取消：$from 仍在前台")
            }
            fg != null && fg != to && !enabledPackages.contains(fg) -> {
                Log.d(TAG, "监控互切防抖落地非监控包 $fg，按离开处理")
                handleForegroundChange(fg)
            }
            else -> {
                val target = when {
                    fg != null && enabledPackages.contains(fg) -> fg
                    else -> to
                }
                Log.d(TAG, "监控互切防抖确认：$from → $target")
                // 保持 last 为 from，让后续 handleForegroundChange 走「新进入」分支
                if (lastForegroundPackage != from) {
                    lastForegroundPackage = from
                }
                parkSessionForMonitoredAppSwitch(from)
                handleForegroundChange(target)
            }
        }
    }

    /**
     * 拦截页仍在、UsageStats 仍报同一包名时：探测是否已切到桌面。
     * 拦截层盖住目标 App 时系统常仍报原包名，须靠近期前台事件推断 Home。
     */
    private fun maybeHomeLeaveWhileSamePackage(currentPkg: String?) {
        if (tryDismissIntentGateOnHome(currentPkg)) return

        val target = overlayManager.interceptTargetPackage ?: return
        val kind = overlayManager.interceptKind ?: return
        if (!kind.probesHomeWhileSamePackage) return
        if (overlayManager.isAdPlaying.get()) return
        if (!isScreenInteractive() || screenIsDreaming) return
        if (interceptGateDismissingPkg == target) return
        if (overlayManager.interceptParkedForRecents || interceptRecentsOpen) return
        if (recentsHomeKeyShouldIgnore()) return
        if (overlayManager.interceptWindowHasFocus()) return
        if (isAccountingHomeLeave(currentPkg, target) || accountingHomePackage == target) return

        // 悬浮窗盖着时 UsageStats 常仍报目标包；只要挂稳后出现过桌面进入，就立刻守住
        if (sawFreshLauncherForeground(target) &&
            !shouldSuppressInterceptLeave(target, currentPkg)
        ) {
            Log.d(TAG, "$target 拦截页下检测到桌面进入事件，按 Home 守住")
            completeInterceptGateDismiss(target, pressHomeAfter = true)
            return
        }

        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val detectedFg = usageStatsManager?.let { getForegroundPackage(it) }
        val resolvedPkg = detectedFg ?: currentPkg
        if (!isInterceptLeaveToHome(resolvedPkg, target)) return
        clearFalseInterceptObscuredIfHome(resolvedPkg, target)
        if (shouldSuppressInterceptLeave(target, resolvedPkg)) return
        Log.d(
            TAG,
            "$target 拦截页期间已回桌面（detected=$detectedFg poll=$currentPkg），按 Home 离开"
        )
        beginInterceptHomeLeave(target, currentPkg = resolvedPkg)
    }

    /**
     * 拦截页期间按 Home / 切走。按 [InterceptOverlayKind] 分流。
     */
    private fun beginInterceptHomeLeave(leavingInterceptPkg: String, currentPkg: String?) {
        if (overlayManager.isAdPlaying.get()) {
            Log.d(TAG, "$leavingInterceptPkg 广告播放期间用户按 Home 离开，保留广告页继续播放")
            lastForegroundPackage = currentPkg
            return
        }
        clearFalseInterceptObscuredIfHome(currentPkg, leavingInterceptPkg)
        if (shouldSuppressInterceptLeave(leavingInterceptPkg, currentPkg)) {
            Log.d(
                TAG,
                "$leavingInterceptPkg 拦截页展示保护窗内，忽略疑似离开（poll=$currentPkg）"
            )
            return
        }
        lastForegroundPackage = currentPkg
        if (isGateEnterHoldActive(leavingInterceptPkg) ||
            overlayManager.gateEnteringPackage == leavingInterceptPkg
        ) {
            Log.d(TAG, "$leavingInterceptPkg 拦截确认进入中，不记 Home 离开")
            return
        }
        if (!isScreenInteractive() || screenIsDreaming) {
            markInterceptScreenObscured(
                leavingInterceptPkg,
                if (screenIsDreaming) "屏保假离开" else "UsageStats 假离开"
            )
            return
        }
        if (interceptLeaveJob?.isActive == true &&
            interceptLeavePendingPkg == leavingInterceptPkg
        ) {
            return
        }
        interceptLeaveJob?.cancel()
        interceptLeavePendingPkg = leavingInterceptPkg
        interceptLeaveJob = serviceScope.launch {
            delay(INTERCEPT_LEAVE_DEBOUNCE_MS)
            interceptLeavePendingPkg = null
            if (!overlayManager.isInterceptLayerOnScreen(leavingInterceptPkg)) return@launch
            if (overlayManager.interceptTargetPackage != null &&
                overlayManager.interceptTargetPackage != leavingInterceptPkg
            ) {
                return@launch
            }
            if (overlayManager.isAdPlaying.get()) return@launch
            clearFalseInterceptObscuredIfHome(currentPkg, leavingInterceptPkg)
            if (shouldSuppressInterceptLeave(leavingInterceptPkg, currentPkg)) {
                if (screenOffDuringInterceptPkg == null && isDeviceLockedOrAsleep()) {
                    screenOffDuringInterceptPkg = leavingInterceptPkg
                }
                overlayManager.suppressTransientLeaveFeedback()
                Log.d(
                    TAG,
                    "$leavingInterceptPkg 拦截页期间息屏/锁屏/屏保假离开（防抖确认），保留拦截、不记次数"
                )
                return@launch
            }
            // 守住离开不抢音频：用户从未进门，停的会是其他 App 的音乐
            val confirmedKind = overlayManager.interceptKind
            when {
                confirmedKind == InterceptOverlayKind.SessionLimit -> {
                    Log.d(TAG, "$leavingInterceptPkg 意图时长到点页 Home = 稍后回顾")
                    deferSessionLimitReview(pushHome = false)
                }
                confirmedKind?.dismissesSilentlyOnHome == true -> {
                    Log.d(TAG, "$leavingInterceptPkg 硬墙页 Home 离开，静默关页")
                    silentDismissHardLock(leavingInterceptPkg, confirmedKind)
                }
                else -> {
                    Log.d(TAG, "$leavingInterceptPkg 拦截页期间切走，拉回门口")
                    pullInterceptBack(leavingInterceptPkg, closeOverview = true)
                }
            }
        }
    }

    /** 拦截页切到其他 App：卸层即可，不算守住、不弹横条。 */
    private fun silentDismissInterceptGate(packageName: String) {
        overlayManager.suppressTransientLeaveFeedback()
        endInterceptFocusHost(packageName, reopenTarget = false)
        serviceScope.launch {
            awaitTargetAppLeftForeground(packageName)
            withContext(Dispatchers.Main) {
                overlayManager.dismissInterceptAfterLeave()
            }
        }
    }

    private fun silentDismissHardLock(packageName: String, kind: InterceptOverlayKind) {
        hardLockHomeSuppressedPkg = packageName
        hardLockHomeDismissedAtWall = System.currentTimeMillis()
        overlayManager.suppressTransientLeaveFeedback()
        endInterceptFocusHost(packageName, reopenTarget = false)
        serviceScope.launch {
            awaitTargetAppLeftForeground(packageName)
            withContext(Dispatchers.Main) {
                overlayManager.dismissInterceptAfterLeave()
            }
        }
        if (lastForegroundPackage == packageName) {
            pressHomeButton()
        }
        serviceScope.launch {
            val appName = resolveAppNameSync(packageName)
            analyticsRepository.trackLimitDecision(
                type = when (kind) {
                    InterceptOverlayKind.PeriodLock -> HaEvents.InterceptType.PERIOD
                    InterceptOverlayKind.OpenLimit -> HaEvents.InterceptType.OPEN_LIMIT
                    else -> HaEvents.InterceptType.DAILY_LIMIT
                },
                action = HaEvents.LimitAction.LEAVE,
                app = appName,
                pkg = packageName,
                interceptId = currentInterceptId
            )
        }
    }

    /** Home 静默关硬墙后：没有新的进程级前台事件，就不要立刻重展。 */
    private fun shouldSkipHardLockReshow(packageName: String): Boolean {
        if (hardLockHomeSuppressedPkg != packageName) return false
        if (hasFreshProcessForeground(packageName, hardLockHomeDismissedAtWall)) {
            hardLockHomeSuppressedPkg = null
            return false
        }
        return true
    }

    private fun hasFreshProcessForeground(packageName: String, sinceWallMs: Long): Boolean {
        if (sinceWallMs <= 0L) return true
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(sinceWallMs, now)
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            if (event.timeStamp <= sinceWallMs) continue
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                return true
            }
        }
        return false
    }

    /**
     * 保护窗之后桌面是否真正进入过前台。
     * 开场 / 盖层抢焦点期间的滞后 RESUMED、以及目标被标后台后回落到旧桌面包，都不算。
     */
    private fun interceptHomeEventSinceWall(): Long {
        val shownAt = overlayManager.interceptShownAtWall
        if (shownAt <= 0L) return 0L
        return shownAt + INTERCEPT_HOME_EVENT_SETTLE_MS
    }

    /**
     * 拦截层盖着时，底层 App 可能仍被 UsageStats 报成前台。
     * 只认保护窗之后的桌面进入，避免门刚起来就误判 Home。
     */
    private fun sawFreshLauncherForeground(targetPackage: String): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val since = interceptHomeEventSinceWall()
        if (since <= 0L) return false
        val now = System.currentTimeMillis()
        if (now <= since) return false
        val events = usm.queryEvents(since, now)
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == targetPackage || pkg == packageName) continue
            if (pkg == "com.android.systemui") continue
            if (!isLauncherPackage(pkg)) continue
            if (event.timeStamp < since) continue
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> return true
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> return true
            }
        }
        return false
    }

    /**
     * 拦截层盖住目标 App 时，等待 UsageStats 确认已切走（或超时），再卸层。
     */
    private suspend fun awaitTargetAppLeftForeground(target: String, timeoutMs: Long = 1_200L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            // 焦点已在托管页 / 桌面 / 其他包：目标 App 不再累计前台
            if (DualSpaceGateActivity.isShowing()) return
            if (sawFreshLauncherForeground(target)) return
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val fg = usm?.let { getForegroundPackage(it) }
            if (fg != null && fg != target) return
            delay(40L)
        }
    }

    /**
     * 意图门离开收口：记守住 + 卸拦截层 + 顶部轻条。
     * 与点「不进去了」一致。
     */
    private fun completeInterceptGateDismiss(
        packageName: String,
        pressHomeAfter: Boolean,
        endReason: String = UsageRecordEntity.EndReason.GATE_DISMISS
    ) {
        if (interceptGateDismissingPkg == packageName) return
        interceptGateDismissingPkg = packageName
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        interceptLeavePendingPkg = null
        cancelInterceptRecentsHomeWatch()
        interceptRecentsOpen = false
        overlayManager.clearRecentsPark()
        interceptHomeDebounceJob?.cancel()
        interceptHomeDebounceJob = null
        endInterceptFocusHost(packageName, reopenTarget = false)
        serviceScope.launch {
            try {
                overlayManager.snapshotGateHoldDraftForAnalytics()
                val alreadyHome = isLauncherPackage(peekForegroundPackage()) &&
                    sawFreshLauncherForeground(packageName)
                if (pressHomeAfter) {
                    if (!alreadyHome) {
                        pressHomeButton()
                    }
                    awaitTargetAppLeftForeground(packageName)
                    if (isPackageLikelyForeground(packageName)) {
                        Log.d(TAG, "$packageName 守住收口后仍在前台，再补一次回桌面")
                        pressHomeButton()
                        awaitTargetAppLeftForeground(packageName, timeoutMs = 400L)
                    }
                }
                val limitTheme = overlayManager.interceptLimitTheme
                val resolvedReason = if (
                    overlayManager.interceptKind == InterceptOverlayKind.PeriodLock
                ) {
                    UsageRecordEntity.EndReason.PERIOD_LOCK
                } else {
                    endReason
                }
                withContext(Dispatchers.Main) {
                    overlayManager.dismissInterceptForUserLeave(
                        packageName = packageName,
                        isLimitTheme = limitTheme,
                        onDismissCompleted = {
                            serviceScope.launch {
                                recordInterceptDismiss(packageName, resolvedReason)
                                if (pressHomeAfter && isPackageLikelyForeground(packageName)) {
                                    pressHomeButton()
                                }
                            }
                        }
                    )
                }
            } finally {
                if (interceptGateDismissingPkg == packageName) {
                    interceptGateDismissingPkg = null
                }
            }
        }
    }

    /**
     * 从一个被监控 App 切到另一个时：
     * - **含意图门**：立即收口为 [SWITCHED_AWAY]，回切走拦截页「继续」；
     * - 仅时长锁等：挂起宽限，期内可无缝回切。
     *
     * 前台活会话下的疑似互切须先经 [noteMonitoredSwitchCandidate] 防抖，再调用本方法。
     */
    private suspend fun parkSessionForMonitoredAppSwitch(previousPackage: String) {
        val session = sessionManager.currentSession.value ?: return
        if (session.packageName != previousPackage) return
        cancelBackgroundTimeout()
        cancelScreenOffTimeout()
        clearAwayCountdownState()
        if (screenOffPackage == previousPackage) {
            screenOffPackage = null
            screenOffDeadlineElapsedMs = -1L
        }
        if (lockedWhilePausedPackage == previousPackage) {
            lockedWhilePausedPackage = null
        }
        overlayManager.dismissCapsule()
        stopMusicAwayHold()
        if (session.hasIntentGate) {
            Log.d(TAG, "意图门监控互切：立即收口 [$previousPackage]，回切走拦截继续")
            sessionManager.endSession(UsageRecordEntity.EndReason.SWITCHED_AWAY)
            return
        }
        val graceMs = configuredAwayCountdownSec() * 1000L
        val parked = sessionManager.parkCurrentSessionForSwitch(graceMs)
        if (parked != null) {
            Log.d(
                TAG,
                "监控互切：挂起 [${parked.packageName}] ${graceMs}ms 内可无缝回切"
            )
            startParkedExpiryWatch()
        }
    }

    /** 回切到本包时，拆掉另一个 App 残留的拦截/超限层（不记 GATE_DISMISS） */
    private fun dismissForeignInterceptIfNeeded(packageName: String) {
        val target = overlayManager.interceptTargetPackage ?: return
        if (!overlayManager.isInterceptVisible.get()) return
        if (target == packageName) return
        Log.d(TAG, "回切 [$packageName]，关闭他包拦截层 [$target]")
        overlayManager.dismissIntercept()
    }

    /** 挂起会话到期后按 SWITCHED_AWAY 落库，并提供「继续上次」 */
    private fun startParkedExpiryWatch() {
        if (parkedExpiryJob?.isActive == true) return
        parkedExpiryJob = serviceScope.launch {
            while (isActive) {
                val next = sessionManager.nextParkedExpiryElapsedMs() ?: break
                val waitMs = (next - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                delay(waitMs + 50L)
                val n = sessionManager.finalizeExpiredParkedSessions()
                if (n > 0) {
                    Log.d(TAG, "监控互切挂起超时，已落库 $n 个会话")
                }
            }
            parkedExpiryJob = null
        }
    }

    /**
     * 意图门关闭时：若时长已超限则弹超限页，否则直接开会话并显示胶囊。
     */
    private suspend fun enterWithoutIntentGate(
        packageName: String,
        appNameHint: String,
        choice: SoftDoorChoice? = null
    ) {
        // 误 eager cover（仅时段锁且当前未锁）时立刻拆掉，避免无门进入仍闪盖层
        val doorKind = overlayManager.interceptKind
        val hardDoor = overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage == packageName &&
            (doorKind == InterceptOverlayKind.OpenLimit ||
                doorKind == InterceptOverlayKind.DailyLimit ||
                doorKind == InterceptOverlayKind.PeriodLock)
        if (hardDoor) return
        if (overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage == packageName
        ) {
            overlayManager.dismissIntercept(keepCapsule = true)
        }

        val existing = sessionManager.currentSession.value
        if (existing != null && existing.packageName != packageName) {
            Log.w(TAG, "enterWithoutIntentGate：从 [${existing.packageName}] 切到 [$packageName]，挂起前一会话")
            parkSessionForMonitoredAppSwitch(existing.packageName)
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

        if (isDailyTimeExhausted(limit, packageName)) {
            Log.d(TAG, "$packageName 意图门关且已超限，展示超限页")
            handleLimitExceeded(packageName)
            return
        }
        if (isDailyOpenExhausted(limit, packageName)) {
            showOpenLimitDoor(packageName, appName)
            return
        }

        val written = choice?.text?.takeIf { it.isNotBlank() }
        val session = sessionManager.startSession(
            packageName,
            appName,
            purpose = written,
            intentKind = when {
                choice?.search == true -> com.life.mindfulnessapp.domain.model.IntentKind.SEARCH
                written != null ->
                    com.life.mindfulnessapp.domain.model.NamingKind.resolveIntentKind(written)
                else -> null
            },
            sessionLimitMinutes = choice?.minutes ?: 0,
            // 从这扇门进来，没写意图也是这一次，挂陪伴条，不退回桌面圆球。
            entry = if (choice != null) HaEvents.Entry.GATE else null,
            restartIfExists = choice != null
        )
        if (session != null) {
            lastForegroundPackage = packageName
            if (choice != null) armOpenVisit(packageName)
            overlayManager.showCapsule(session, playEnterAnimation = true)
            if (choice != null && !choice.search) {
                val landId = choice.deepLinkId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: AppDeepLinkCatalog.resolveFromPurpose(packageName, written.orEmpty())?.id
                if (landId != null) {
                    maybeLandSearchDeepLink(
                        packageName = packageName,
                        purpose = written.orEmpty(),
                        landMode = IntentLandMode.DEEPLINK,
                        landDeepLinkId = landId
                    )
                }
            }
            Log.d(TAG, "门口进入，会话已创建并显示陪伴：$packageName purpose=${written ?: "没写"}")
        } else {
            Log.w(TAG, "enterWithoutIntentGate startSession 返回 null：$packageName")
        }
    }

    /** 当前是否应被时段锁硬挡（App 时段锁 ∪ 守计划） */
    private fun isPeriodHardLocked(
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity?,
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (packageName == periodExemptionPackage) return false
        if (PlanBlockPolicy.isPackageLockedNow(
                cachedPlanBlocks,
                packageName,
                cachedMonitoredPackages,
                nowMillis
            )
        ) {
            return true
        }
        if (limit == null || !limit.periodLockEnabled) return false
        val windows = PeriodWindowsCodec.decode(limit.periodWindowsJson)
        return PeriodLockPolicy.isLockedNow(
            enabled = true,
            windows = windows,
            nowMillis = nowMillis
        )
    }

    /**
     * 展示时段锁硬挡。
     * 事实是时间窗；守计划与 App 时段共用这一页。
     * 可有「紧急进入」计次（选时长）；不拆整段锁。
     * @param endActiveSession 会话中跨入窗口时先收口会话
     */
    private suspend fun handlePeriodLock(packageName: String, endActiveSession: Boolean) {
        if (shouldSkipHardLockReshow(packageName)) {
            Log.d(TAG, "$packageName 时段锁刚被 Home 静默关掉，等待真正再打开")
            return
        }
        val limit = appLimitRepository.getAppLimit(packageName)
        val plan = PlanBlockPolicy.activeForPackage(
            cachedPlanBlocks,
            packageName,
            cachedMonitoredPackages
        )
        val appWindows = if (limit?.periodLockEnabled == true) {
            PeriodWindowsCodec.decode(limit.periodWindowsJson)
        } else {
            emptyList()
        }
        val appActive = PeriodLockPolicy.activeWindow(appWindows)
        if (plan == null && appActive == null) return

        val window = plan?.toPeriodWindow() ?: appActive!!
        val appName = limit?.appName ?: runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)

        // 无论是否收口会话，都拆掉活跃/暂停胶囊，避免与硬挡层叠
        overlayManager.dismissCapsule()
        cancelBackgroundTimeout()
        clearAwayCountdownState()
        if (lockedWhilePausedPackage == packageName) {
            lockedWhilePausedPackage = null
        }
        if (screenOffPackage == packageName) {
            screenOffPackage = null
            screenOffDeadlineElapsedMs = -1L
            cancelScreenOffTimeout()
        }

        if (endActiveSession) {
            val session = sessionManager.currentSession.value
            if (session != null && session.packageName == packageName) {
                sessionManager.endSession(UsageRecordEntity.EndReason.PERIOD_LOCK)
            }
        }

        // 若仍挂着别的拦截层，先拆
        if (overlayManager.isInterceptVisible.get() &&
            overlayManager.interceptTargetPackage != null &&
            overlayManager.interceptTargetPackage != packageName
        ) {
            overlayManager.dismissIntercept()
        }

        analyticsRepository.trackInterceptShow(
            type = HaEvents.InterceptType.PERIOD,
            interceptId = beginIntercept(packageName),
            app = appName,
            pkg = packageName,
            capPeriod = true
        )
        analyticsRepository.trackLimitBlock(
            type = HaEvents.InterceptType.PERIOD,
            interceptId = currentInterceptId,
            app = appName,
            pkg = packageName
        )
        overlayManager.prepareInterceptCover(packageName, InterceptOverlayKind.PeriodLock)
        beginInterceptFocusHost(packageName)
        lastForegroundPackage = packageName
        armInterceptShowGuard()
        val exemptionRemaining = appPreferences.periodExemptionRemainingToday(packageName)
        val exemptionLabel = if (exemptionRemaining > 0) {
            PeriodLockPolicy.exemptionEnterLabel(exemptionRemaining)
        } else {
            null
        }
        overlayManager.showPeriodLock(
            packageName = packageName,
            appName = appName,
            window = window,
            scheduleTitle = plan?.title?.trim()?.takeIf { it.isNotEmpty() },
            scheduleWhy = plan?.whyLine(),
            exemptionLabel = exemptionLabel,
            onBreakthrough = if (exemptionRemaining > 0) {
                { minutes ->
                    serviceScope.launch {
                        admitWithPeriodExemption(packageName, appName, minutes)
                    }
                }
            } else {
                null
            },
            onDismiss = {
                analyticsRepository.trackLimitDecision(
                    type = HaEvents.InterceptType.PERIOD,
                    action = HaEvents.LimitAction.LEAVE,
                    app = appName,
                    pkg = packageName,
                    interceptId = currentInterceptId
                )
                endInterceptFocusHost(packageName, reopenTarget = false)
                pressHomeButton(pauseMediaForPackage = packageName)
                serviceScope.launch {
                    recordInterceptDismiss(
                        packageName,
                        endReason = UsageRecordEntity.EndReason.PERIOD_LOCK
                    )
                }
            }
        )
    }

    /**
     * 时段硬门紧急进入：计次，按所选时长开会话，到点收口；不拆整段锁。
     * 仍尊重日限 / 次数硬门。
     */
    private suspend fun admitWithPeriodExemption(
        packageName: String,
        appName: String,
        sessionMinutes: Int
    ) {
        if (appPreferences.hasUsedPeriodExemptionToday(packageName)) {
            Log.w(TAG, "[$packageName] 今日紧急进入已用过，忽略")
            return
        }
        val clampedMinutes = sessionMinutes.coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
        beginGateEnterHold(packageName)
        // 先武装内存旁路，成功进门或改走其他硬门后再落盘计次
        periodExemptionPackage = packageName
        analyticsRepository.trackLimitDecision(
            type = HaEvents.InterceptType.PERIOD,
            action = HaEvents.LimitAction.EXEMPT,
            app = appName,
            pkg = packageName,
            interceptId = currentInterceptId
        )
        endInterceptFocusHost(packageName, reopenTarget = true)

        val limit = appLimitRepository.getAppLimit(packageName)
        if (limit != null && isDailyTimeExhausted(limit, packageName)) {
            appPreferences.markPeriodExemptionUsed(packageName)
            Log.d(TAG, "$packageName 紧急进入后仍触日限，展示超限页")
            handleLimitExceeded(packageName)
            return
        }
        if (limit != null && isDailyOpenExhausted(limit, packageName)) {
            appPreferences.markPeriodExemptionUsed(packageName)
            showOpenLimitDoor(packageName, appName)
            return
        }

        val session = sessionManager.startSession(
            packageName,
            appName,
            purpose = null,
            intentKind = null,
            sessionLimitMinutes = clampedMinutes,
            entry = HaEvents.Entry.GRACE,
            restartIfExists = true
        )
        if (session != null) {
            appPreferences.markPeriodExemptionUsed(packageName)
            lastForegroundPackage = packageName
            armOpenVisit(packageName)
            withContext(Dispatchers.Main) {
                overlayManager.showCapsule(session, playEnterAnimation = false)
            }
            Log.d(TAG, "紧急进入 ${clampedMinutes} 分，会话已创建：$packageName")
        } else {
            periodExemptionPackage = null
            overlayManager.clearGateEnter(packageName)
            gateEnterHoldPackage = null
            Log.w(TAG, "紧急进入 startSession 返回 null：$packageName")
            endInterceptFocusHost(packageName, reopenTarget = false)
        }
    }

    /**
     * 桌面暂停 / 息屏后台态跨入时段锁：静默收口并拆胶囊，不弹硬挡
     *（用户不在被监控 App 内；下次打开再走硬挡）。
     */
    private suspend fun enforcePeriodLockWhileAway(packageName: String) {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName) {
            overlayManager.dismissCapsule()
            return
        }
        Log.d(TAG, "[$packageName] 暂停/后台态跨入时段锁，静默收口并拆胶囊")
        cancelBackgroundTimeout()
        cancelScreenOffTimeout()
        screenOffPackage = null
        screenOffDeadlineElapsedMs = -1L
        lockedWhilePausedPackage = null
        clearAwayCountdownState()
        overlayManager.suppressTransientLeaveFeedback()
        sessionManager.endSession(UsageRecordEntity.EndReason.PERIOD_LOCK)
        overlayManager.dismissCapsule()
    }

    /** 当前包名是否处于时段硬锁 */
    private suspend fun isPackagePeriodHardLocked(packageName: String): Boolean {
        val limit = appLimitRepository.getAppLimit(packageName)
        return isPeriodHardLocked(limit, packageName)
    }

    /** 意图门关时：次数尽 / 日限尽 / 呼吸门口。有门口返回 true；呼吸通过后执行 [onAdmitted]。 */
    private suspend fun showPlanDoor(
        packageName: String,
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity,
        onAdmitted: suspend (SoftDoorChoice) -> Unit
    ): Boolean {
        // 读日限/次数前先盖占位，缩短露脸窗口
        tryEagerInterceptCover(packageName)
        if (isDailyTimeExhausted(limit, packageName)) {
            handleLimitExceeded(packageName, allowUpgradeFromIntentGate = true)
            return true
        }
        if (isDailyOpenExhausted(limit, packageName)) {
            showOpenLimitDoor(packageName, limit.appName)
            return true
        }
        showBreathDoor(packageName, limit.appName, onAdmitted)
        return true
    }

    /**
     * 今日已用：只用心锚会话（含进行中由调用方加成）。
     * 门口盖层期间系统前台会虚高，不采 UsageStats。
     */
    private suspend fun todayCapUsedSeconds(packageName: String): Long {
        val now = System.currentTimeMillis()
        val record = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        return DailyCapFacts.usedSeconds(record)
    }

    private fun effectiveDailyLimitSeconds(
        packageName: String,
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity
    ): Long = appPreferences.effectiveDailyLimitSeconds(
        packageName,
        limit.effectiveDailyLimitMinutes()
    )

    private suspend fun isDailyTimeExhausted(
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity,
        packageName: String
    ): Boolean {
        if (!limit.timeLimitEnabled) return false
        val todayUsed = todayCapUsedSeconds(packageName)
        val weekUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName)
        val dailyLimitSeconds = effectiveDailyLimitSeconds(packageName, limit)
        val weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L
        return DailyCapFacts.exhausted(todayUsed, dailyLimitSeconds) ||
            DailyCapFacts.exhausted(weekUsed, weeklyLimitSeconds)
    }

    private suspend fun isDailyOpenExhausted(
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity,
        packageName: String
    ): Boolean {
        val cap = limit.effectiveDailyOpenLimit()
        if (cap <= 0) return false
        val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName)
        val opens = UsageRecordCounts.openQuotaCount(records)
        return opens >= cap
    }

    private suspend fun doorRemainingLabel(
        packageName: String,
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity
    ): String {
        val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName)
        val opens = UsageRecordCounts.openQuotaCount(records)
        val hasLive = records.any {
            it.endTime <= 0L && UsageRecordCounts.countsTowardOpenQuota(it)
        }
        val usedSeconds = todayCapUsedSeconds(packageName)
        val usedMin = DailyCapFacts.wholeMinutes(usedSeconds)
        val timeCap = DailyCapFacts.wholeMinutes(effectiveDailyLimitSeconds(packageName, limit))
        // 进行中＝还在同一次；已收口再进才 +1。次数只陈述第几次，不挂配额分母。
        val nth = if (hasLive) opens.coerceAtLeast(1) else opens + 1
        return buildList {
            add("今日第 $nth 次")
            when {
                timeCap > 0 -> add("已用 $usedMin/$timeCap 分钟")
                usedMin > 0 -> add("已用 $usedMin 分钟")
            }
        }.joinToString(" · ")
    }

    /** 事务门事实行：次数 + 还剩时长（有日限时）。 */
    private suspend fun doorTaskFactsLabel(
        packageName: String,
        limit: com.life.mindfulnessapp.data.db.entity.AppLimitEntity
    ): String {
        val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName)
        val opens = UsageRecordCounts.openQuotaCount(records)
        val hasLive = records.any {
            it.endTime <= 0L && UsageRecordCounts.countsTowardOpenQuota(it)
        }
        val openCap = limit.effectiveDailyOpenLimit()
        val nth = if (hasLive) opens.coerceAtLeast(1) else opens + 1
        val openPart = if (openCap > 0) {
            "打开第 ${nth.coerceAtMost(openCap)}/$openCap 次"
        } else {
            "打开第 $nth 次"
        }
        val remain = DailyCapFacts.remainingMinutes(
            todayCapUsedSeconds(packageName),
            effectiveDailyLimitSeconds(packageName, limit)
        )
        return if (remain != null && remain > 0) {
            "$openPart · 还剩 $remain 分"
        } else {
            openPart
        }
    }

    private suspend fun showBreathDoor(
        packageName: String,
        appNameHint: String,
        onAdmitted: suspend (SoftDoorChoice) -> Unit
    ) {
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        val limit = appLimitRepository.getAppLimit(packageName)
        val appName = resolveAppNameSync(packageName).ifBlank { appNameHint }
        val profile = com.life.mindfulnessapp.domain.model.IntentGateProfiles.resolve(packageName)
        val isTaskDoor = profile.kind == com.life.mindfulnessapp.domain.model.IntentGateKind.TASK
        overlayManager.prepareInterceptCover(packageName, InterceptOverlayKind.Breath)
        beginInterceptFocusHost(packageName)
        lastForegroundPackage = packageName
        armInterceptShowGuard()
        analyticsRepository.trackInterceptShow(
            type = HaEvents.InterceptType.BREATH,
            interceptId = beginIntercept(packageName),
            app = appName,
            pkg = packageName,
            capTime = limit?.timeLimitEnabled == true
        )
        val onLeave: () -> Unit = {
            serviceScope.launch {
                recordInterceptDismiss(packageName)
                endInterceptFocusHost(packageName, reopenTarget = false)
                pressHomeButton()
                scheduleEnsureLeftForeground(packageName)
            }
            Unit
        }
        if (isTaskDoor) {
            val facts = limit?.let { doorTaskFactsLabel(packageName, it) }.orEmpty()
            overlayManager.showTaskGate(
                packageName = packageName,
                appName = appName,
                factsLabel = facts,
                onAdmit = { choice, done ->
                    serviceScope.launch {
                        beginGateEnterHold(packageName)
                        endInterceptFocusHost(packageName, reopenTarget = true)
                        onAdmitted(choice)
                        done(true)
                    }
                },
                onLeave = onLeave
            )
            return
        }
        val remaining = limit?.let { doorRemainingLabel(packageName, it) } ?: "停一下再决定"
        val entry = SearchDeepLinkCatalog.primaryKeywordEntry(packageName)
        val hasSearchLanding = entry != null &&
            entry.preferSearchLanding &&
            SearchDeepLinkLauncher.canResolve(this, entry, "测试")
        val canSearch = rulePlanStore.searchDirectEnabled(packageName) && hasSearchLanding
        // 随意浏览只对应有可靠搜索深链的刷逛型 App；方案关搜索直达时仍可刷
        val offerBrowse = hasSearchLanding
        val dailyRemain = limit?.let {
            DailyCapFacts.remainingMinutes(
                todayCapUsedSeconds(packageName),
                effectiveDailyLimitSeconds(packageName, it)
            )
        }
        val browsePolicy = appLimitRepository.getBrowseCasualPolicy(packageName)
        val browseLimitMin = browsePolicy.effectiveDailyLimitMinutes()
        val browseUsedSec = usageRecordRepository.getTodayBrowseCasualSeconds(packageName)
        val browseRemain = if (browseLimitMin <= 0) {
            null
        } else {
            DailyCapFacts.remainingMinutes(browseUsedSec, browseLimitMin * 60L)
        }
        val doorRemainForBrowse = when {
            dailyRemain == null && browseRemain == null -> null
            dailyRemain == null -> browseRemain
            browseRemain == null -> dailyRemain
            else -> minOf(dailyRemain, browseRemain)
        }
        val maxSessionMinutes = when {
            doorRemainForBrowse == null -> SessionLimitPolicy.MAX_SESSION_MINUTES
            doorRemainForBrowse <= 0 -> 0
            else -> minOf(SessionLimitPolicy.MAX_SESSION_MINUTES, doorRemainForBrowse)
        }
        val nowMs = System.currentTimeMillis()
        val browseGate = BrowseCasualCooldown.gateSnapshot(
            persisted = appPreferences.getBrowseCasualCooldown(packageName),
            todayKey = appPreferences.browseCasualCooldownTodayKey(),
            nowMs = nowMs
        )
        val intentTags = withContext(Dispatchers.IO) {
            appLimitRepository.getCommonIntents(packageName)
                .let { com.life.mindfulnessapp.domain.model.CommonIntentsCodec.gateItems(it) }
                .map {
                    com.life.mindfulnessapp.domain.model.IntentGateAction(
                        id = it.deepLinkId ?: it.label,
                        label = it.label,
                        deepLinkId = it.deepLinkId
                    )
                }
                .ifEmpty {
                    com.life.mindfulnessapp.domain.model.IntentGateProfiles
                        .quickIntentTags(packageName)
                }
        }
        overlayManager.showBreathGate(
            packageName = packageName,
            appName = appName,
            remainingLabel = remaining,
            canSearch = canSearch,
            offerBrowse = offerBrowse,
            intentTags = intentTags,
            browseUsedMinutes = DailyCapFacts.wholeMinutes(browseUsedSec),
            browseLimitMinutes = browseLimitMin.takeIf { it > 0 },
            maxSessionMinutes = maxSessionMinutes,
            browseCooldownMinutes = browseGate.remainingCooldownMinutes,
            onAdmit = { choice, done ->
                serviceScope.launch {
                    if (isBrowseSoftDoorChoice(choice)) {
                        if (!offerBrowse) {
                            Log.d(TAG, "随意浏览未开放 [$packageName]（无可靠搜索深链）")
                            done(false)
                            return@launch
                        }
                        val gateNow = System.currentTimeMillis()
                        val snap = BrowseCasualCooldown.gateSnapshot(
                            persisted = appPreferences.getBrowseCasualCooldown(packageName),
                            todayKey = appPreferences.browseCasualCooldownTodayKey(),
                            nowMs = gateNow
                        )
                        if (snap.blocked) {
                            Log.d(
                                TAG,
                                "随意浏览冷却中 [$packageName] 约 ${snap.remainingCooldownMinutes} 分"
                            )
                            done(false)
                            return@launch
                        }
                        appPreferences.setBrowseCasualCooldown(
                            packageName,
                            BrowseCasualCooldown.afterSuccessfulEnter(
                                persisted = appPreferences.getBrowseCasualCooldown(packageName),
                                todayKey = appPreferences.browseCasualCooldownTodayKey(),
                                nowMs = gateNow
                            )
                        )
                    }
                    if (choice.search) {
                        val land = entry ?: run {
                            done(false)
                            return@launch
                        }
                        val err = SearchDeepLinkLauncher
                            .launch(this@MonitorForegroundService, land, choice.text)
                        if (err != null) {
                            android.util.Log.w(TAG, "搜索深链未落地 $packageName: $err")
                            done(false)
                            return@launch
                        }
                    }
                    beginGateEnterHold(packageName)
                    endInterceptFocusHost(packageName, reopenTarget = true)
                    onAdmitted(choice)
                    done(true)
                }
            },
            onLeave = onLeave
        )
    }

    private fun isBrowseSoftDoorChoice(choice: SoftDoorChoice): Boolean =
        !choice.search && BrowseCasualIntent.isBrowseLike(choice.text)

    private suspend fun showOpenLimitDoor(packageName: String, appNameHint: String) {
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        val limit = appLimitRepository.getAppLimit(packageName)
        val appName = resolveAppNameSync(packageName).ifBlank { appNameHint }
        val cap = limit?.effectiveDailyOpenLimit() ?: 0
        val used = UsageRecordCounts.openQuotaCount(
            usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName)
        )
        val remainingMinutes = if (limit == null) {
            null
        } else {
            DailyCapFacts.remainingMinutes(
                todayCapUsedSeconds(packageName),
                effectiveDailyLimitSeconds(packageName, limit)
            )
        }
        overlayManager.prepareInterceptCover(packageName, InterceptOverlayKind.OpenLimit)
        overlayManager.dismissCapsule()
        beginInterceptFocusHost(packageName)
        lastForegroundPackage = packageName
        armInterceptShowGuard()
        analyticsRepository.trackInterceptShow(
            type = HaEvents.InterceptType.OPEN_LIMIT,
            interceptId = beginIntercept(packageName),
            app = appName,
            pkg = packageName
        )
        overlayManager.showOpenLimitBlock(
            packageName = packageName,
            appName = appName,
            usedOpens = used,
            openCap = cap,
            remainingMinutes = remainingMinutes,
            onLeave = {
                analyticsRepository.trackLimitDecision(
                    type = HaEvents.InterceptType.OPEN_LIMIT,
                    action = HaEvents.LimitAction.LEAVE,
                    app = appName,
                    pkg = packageName,
                    interceptId = currentInterceptId
                )
                endInterceptFocusHost(packageName, reopenTarget = false)
                pressHomeButton(pauseMediaForPackage = packageName)
                serviceScope.launch {
                    recordInterceptDismiss(packageName)
                    scheduleEnsureLeftForeground(packageName)
                }
            }
        )
    }

    private suspend fun showInterceptOverlay(
        packageName: String,
        countImpulse: Boolean = true
    ) {
        if (packageName == this.packageName) {
            Log.d(TAG, "跳过对心锚自身的拦截")
            overlayManager.dismissIntercept(keepCapsule = true)
            return
        }
        // 新一次展示前作废上一轮 Home 离开防抖，避免误拆掉刚起来的拦截页
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        interceptLeavePendingPkg = null
        interceptGateDismissingPkg = null
        cancelInterceptRecentsHomeWatch()
        // 不在这里清 interceptRecentsOpen：门还没拿到焦点时概览可能仍在。
        // 焦点到手后由 onInterceptGainedFocus 结束进程列表会话。
        overlayManager.clearRecentsPark()
        // 静默重展要续门时钟：先取出再清标记
        val preserveGateShownAt =
            if (!countImpulse && screenOffDuringInterceptShownAt > 0L) {
                screenOffDuringInterceptShownAt
            } else {
                0L
            }
        screenOffDuringInterceptPkg = null
        screenOffDuringInterceptShownAt = 0L
        if (countImpulse) {
            interceptObscuredAccumulatedMs = 0L
            interceptObscuredStartedAtWall = 0L
        }

        if (screenOffForcedExitPkg == packageName) {
            Log.d(TAG, "[$packageName] 息屏超时退出中，不弹拦截")
            pressHomeButton()
            return
        }
        val liveVisit = sessionManager.currentSession.value
        if (liveVisit?.packageName == packageName &&
            (isOpenVisitProtected(packageName) || screenOffPackage == packageName)
        ) {
            Log.d(TAG, "[$packageName] 这一次还在（锁屏/进门未离开），不弹拦截")
            if (!liveVisit.isInBackground) {
                overlayManager.showCapsule(liveVisit, playEnterAnimation = false)
            } else if (screenOffPackage == packageName && !isDeviceLockedOrAsleep()) {
                // 灭屏宽限未续上却又撞到开门：先尝试续会话，勿重弹门
                tryResumeAfterScreenOff(packageName, reason = "gate-skip-screen-off")
            }
            return
        }
        if (overlayManager.hasInterceptUiAttached() &&
            overlayManager.interceptTargetPackage == packageName &&
            overlayManager.isInterceptLayerOnScreen(packageName)
        ) {
            Log.d(TAG, "[$packageName] 意图门已在屏上，跳过重挂")
            lastForegroundPackage = packageName
            return
        }

        // 静默重展（息屏解锁）前先清掉误触发的离开横条，避免叠在拦截页上
        if (!countImpulse) {
            overlayManager.suppressTransientLeaveFeedback()
        }

        val existing = sessionManager.currentSession.value
        if (existing?.packageName == packageName) {
            val sameVisit =
                !existing.isInBackground ||
                    isAwayGraceHolding(packageName) ||
                    isMusicAwayHold(packageName) ||
                    screenOffPackage == packageName ||
                    isOpenVisitProtected(packageName)
            if (sameVisit) {
                Log.d(TAG, "[$packageName] 已有进行中会话，跳过拦截页重展")
                screenOffDuringInterceptPkg = null
                screenOffDuringInterceptShownAt = 0L
                overlayManager.dismissIntercept(keepCapsule = true)
                return
            }
            // 后台会话已过离开宽限：先收口，再继续挂门（勿拆掉刚盖的占位；也不弹回顾条）
            Log.d(TAG, "[$packageName] 后台会话已过宽限，收口后继续开门")
            stopMusicAwayHold()
            clearAwayCountdownState()
            endStaleBackgroundSessionForGate(existing)
        }

        val limit = appLimitRepository.getAppLimit(packageName)
        if (isPeriodHardLocked(limit, packageName)) {
            handlePeriodLock(packageName, endActiveSession = false)
            return
        }
        if (limit == null) {
            overlayManager.dismissIntercept()
            return
        }

        // 日/周额度已触顶：直接走时长锁页，不要先盖意图门再异步改道
        //（否则 eager cover / 标志位会让超限页被跳过，用户一直停在意图门上看「今日已用完」）
        if (isDailyTimeExhausted(limit, packageName)) {
            Log.d(TAG, "$packageName 打开时已超限，展示时长锁页（非意图门）")
            handleLimitExceeded(packageName, allowUpgradeFromIntentGate = true)
            return
        }
        if (isDailyOpenExhausted(limit, packageName)) {
            Log.d(TAG, "$packageName 打开次数已尽，展示次数挡")
            showOpenLimitDoor(packageName, limit.appName)
            return
        }
        if (!limit.requireIntentOnOpen) {
            if (showPlanDoor(packageName, limit) { choice ->
                    enterWithoutIntentGate(packageName, limit.appName, choice)
                }
            ) return
        }

        // 盖层优先于挂起旧会话 / 读限额，缩短「App 先露脸」窗口
        val foreignInterceptPkg = overlayManager.interceptTargetPackage
            ?.takeIf {
                overlayManager.isInterceptVisible.get() && it != packageName
            }
        if (foreignInterceptPkg != null) {
            Log.d(TAG, "关闭旧拦截页 [$foreignInterceptPkg]，准备拦截 [$packageName]（不记守住）")
            overlayManager.suppressTransientLeaveFeedback()
            overlayManager.dismissIntercept()
        }
        overlayManager.prepareInterceptCover(packageName, InterceptOverlayKind.IntentGate)
        if (preserveGateShownAt > 0L) {
            overlayManager.restoreInterceptShownAtWall(preserveGateShownAt)
            Log.d(TAG, "[$packageName] 静默重展续上门时钟 shownAt=$preserveGateShownAt")
        }
        beginInterceptFocusHost(packageName)
        lastForegroundPackage = packageName
        armInterceptShowGuard()

        if (existing != null && existing.packageName != packageName) {
            Log.w(TAG, "showInterceptOverlay：从 [${existing.packageName}] 切到 [$packageName]，挂起前一会话")
            parkSessionForMonitoredAppSwitch(existing.packageName)
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            limit.appName
        }

        analyticsRepository.trackInterceptShow(
            type = HaEvents.InterceptType.INTENT,
            interceptId = beginIntercept(packageName),
            app = appName,
            pkg = packageName,
            capIntent = limit.requireIntentOnOpen,
            capTime = limit.timeLimitEnabled,
            capPeriod = limit.periodLockEnabled,
            capSession = limit.sessionLimitEnabled
        )
        overlayManager.showIntercept(
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = limit.effectiveDailyLimitMinutes(),
            weeklyLimitMinutes = limit.effectiveWeeklyLimitMinutes(),
            countImpulse = countImpulse,
            onEnterAnimating = { beginGateEnterHold(packageName) },
            onContinue = { decision ->
                // onContinue 从主线程（Compose onClick）触发
                serviceScope.launch {
                    val interceptId = currentInterceptId
                    val session = sessionManager.startSession(
                        packageName = packageName,
                        appName = appName,
                        purpose = decision.purpose.trim().takeIf { it.isNotEmpty() },
                        intentKind = decision.intentKind,
                        sessionLimitMinutes = decision.sessionLimitMinutes,
                        entry = HaEvents.Entry.GATE,
                        restartIfExists = true,
                        gateDwellMs = resolveGateDwellMs()
                    )
                    if (session != null) {
                        analyticsRepository.trackGateEnter(
                            interceptId = interceptId,
                            sessionId = session.recordId,
                            app = appName,
                            pkg = packageName,
                            purpose = decision.purpose,
                            intentKind = decision.intentKind.name,
                            sessionLimitMinutes = decision.sessionLimitMinutes
                        )
                        lastForegroundPackage = packageName
                        endInterceptFocusHost(packageName, reopenTarget = true)
                        armOpenVisit(packageName)
                        overlayManager.showCapsule(session, playEnterAnimation = true)
                        maybeLandSearchDeepLink(
                            packageName = packageName,
                            purpose = decision.purpose,
                            landMode = decision.landMode,
                            landDeepLinkId = decision.landDeepLinkId
                        )
                        Log.d(
                            TAG,
                            "会话已创建（kind=${decision.intentKind}, purpose=${decision.purpose}, " +
                                "land=${decision.landMode}, sessionMin=${decision.sessionLimitMinutes}），" +
                                "胶囊已请求显示：$packageName"
                        )
                    } else {
                        overlayManager.isInterceptVisible.set(false)
                        overlayManager.clearGateEnter(packageName)
                        gateEnterHoldPackage = null
                        endInterceptFocusHost(packageName, reopenTarget = false)
                        pressHomeButton()
                        Log.w(TAG, "startSession 返回 null，跳过胶囊显示")
                    }
                }
            },
            onSessionResumed = { session ->
                beginGateEnterHold(session.packageName)
                lastForegroundPackage = session.packageName
                endInterceptFocusHost(session.packageName, reopenTarget = true)
                armOpenVisit(session.packageName)
                serviceScope.launch {
                    analyticsRepository.trackGateEnter(
                        interceptId = currentInterceptId,
                        sessionId = session.recordId,
                        app = session.appName,
                        pkg = session.packageName,
                        purpose = session.purpose,
                        intentKind = session.intentKind?.name,
                        sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt()
                    )
                }
                overlayManager.showCapsule(session, playEnterAnimation = true)
                Log.d(
                    TAG,
                    "沿用上次意图重新进入（新计时），胶囊已显示：${session.packageName}"
                )
            },
            onDismiss = {
                // 用户在拦截页选择离开：写入一条极短的拦截退出记录
                serviceScope.launch {
                    recordInterceptDismiss(packageName)
                    endInterceptFocusHost(packageName, reopenTarget = false)
                    pressHomeButton()
                    scheduleEnsureLeftForeground(packageName)
                }
            },
            onReset = {
                handleResetLimit(packageName)
            },
            onContinueWithGrace = { purpose, graceMinutes ->
                serviceScope.launch {
                    continueWithDailyGrace(
                        packageName = packageName,
                        appName = appName,
                        purpose = purpose,
                        graceMinutes = graceMinutes
                    )
                }
            }
        )
    }

    /** 意图门展示 → 决策的停留毫秒；未展示过则为 0 */
    private fun resolveGateDwellMs(nowMs: Long = System.currentTimeMillis()): Long {
        val shownAt = overlayManager.interceptShownAtWall
        if (shownAt <= 0L) return 0L
        val ongoing = if (interceptObscuredStartedAtWall > 0L) {
            (nowMs - interceptObscuredStartedAtWall).coerceAtLeast(0L)
        } else {
            0L
        }
        return (nowMs - shownAt - interceptObscuredAccumulatedMs - ongoing).coerceAtLeast(0L)
    }

    /**
     * 拦截页离开：写入极短克制记录。
     */
    private suspend fun recordInterceptDismiss(
        packageName: String,
        endReason: String = UsageRecordEntity.EndReason.GATE_DISMISS
    ) {
        val appName = resolveAppNameSync(packageName)
        val hadDraft = overlayManager.consumeInterceptHadDraftPurpose()
        analyticsRepository.trackGateHold(
            interceptId = currentInterceptId.ifBlank { AnalyticsBuckets.newInterceptId() },
            app = appName,
            pkg = packageName,
            hadDraftPurpose = hadDraft
        )
        val now = System.currentTimeMillis()
        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = now,
                durationSeconds = 0L,
                endReason = endReason,
                purpose = null,
                gateDwellMs = resolveGateDwellMs(now)
            )
        )
        android.util.Log.d(
            TAG,
            "拦截退出记录已写入 [id=$recordId, pkg=$packageName hadDraft=$hadDraft]"
        )
    }

    /** 正向出口：记「去做了」 */
    private suspend fun recordPositiveExit(
        packageName: String,
        choice: com.life.mindfulnessapp.domain.model.PositiveExitChoice
    ) {
        val appName = resolveAppNameSync(packageName)
        val hadDraft = overlayManager.consumeInterceptHadDraftPurpose()
        analyticsRepository.trackGateHold(
            interceptId = currentInterceptId.ifBlank { AnalyticsBuckets.newInterceptId() },
            app = appName,
            pkg = packageName,
            hadDraftPurpose = hadDraft
        )
        val now = System.currentTimeMillis()
        val note = choice.why?.trim()?.takeIf { it.isNotEmpty() }?.let { "为了 · $it" }
        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = now,
                durationSeconds = 0L,
                endReason = UsageRecordEntity.EndReason.GATE_POSITIVE_EXIT,
                purpose = choice.title.trim().take(40).ifEmpty { "去做了" },
                intentKind = choice.kind.storageValue,
                note = note,
                gateDwellMs = resolveGateDwellMs(now)
            )
        )
        android.util.Log.d(
            TAG,
            "正向出口已写入 [id=$recordId, pkg=$packageName kind=${choice.kind} title=${choice.title}]"
        )
    }

    /**
     * 本次会话时长到点：离开 / 续时一次（Home = 稍后）。
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
                val baseMinutes = (session.sessionLimitSeconds / 60L).toInt()
                val remainingBrowse = if (session.isBrowseCompanion) {
                    val browseLimitMin = appLimitRepository
                        .getBrowseCasualPolicy(packageName)
                        .effectiveDailyLimitMinutes()
                    if (browseLimitMin <= 0) {
                        Int.MAX_VALUE
                    } else {
                        val closedSec = usageRecordRepository.getTodayBrowseCasualSeconds(packageName)
                        val usedSec = closedSec + session.currentSessionSeconds.coerceAtLeast(0L)
                        DailyCapFacts.remainingMinutes(usedSec, browseLimitMin * 60L) ?: 0
                    }
                } else {
                    null
                }
                val maxExtendMinutes = SessionLimitPolicy.maxLimitReachedExtensionMinutes(
                    sessionLimitMinutes = baseMinutes,
                    remainingBrowseMinutes = remainingBrowse
                )
                val canExtend = session.canOfferLimitReachedExtension && maxExtendMinutes > 0
                sessionLimitEndingRecordId = endingRecordId
                sessionLimitEndingPkg = packageName
                sessionLimitEndingAppName = appName
                sessionLimitEndingPurpose = session.purpose

                analyticsRepository.trackInterceptShow(
                    type = HaEvents.InterceptType.SESSION_LIMIT,
                    interceptId = beginIntercept(packageName),
                    app = appName,
                    pkg = packageName,
                    capSession = true
                )
                analyticsRepository.trackLimitBlock(
                    type = HaEvents.InterceptType.SESSION_LIMIT,
                    interceptId = currentInterceptId,
                    app = appName,
                    pkg = packageName
                )
                val durationSeconds = session.currentSessionSeconds
                beginInterceptFocusHost(packageName)
                lastForegroundPackage = packageName
                armInterceptShowGuard()
                overlayManager.showSessionLimitReached(
                    packageName = packageName,
                    appName = appName,
                    purpose = session.purpose,
                    committedMinutes = committedMinutes,
                    durationSeconds = durationSeconds,
                    canExtend = canExtend,
                    maxExtendMinutes = maxExtendMinutes,
                    intentKind = session.intentKind,
                    compareEnabled = session.compareEnabled,
                    compareMinMinutes = session.compareMinMinutes,
                    onConfirm = { mindfulnessLevel, note, driftSeconds ->
                        serviceScope.launch {
                            analyticsRepository.trackLimitDecision(
                                type = HaEvents.InterceptType.SESSION_LIMIT,
                                action = HaEvents.LimitAction.COMPARE_AND_EXIT,
                                app = appName,
                                pkg = packageName,
                                interceptId = currentInterceptId,
                                sessionId = endingRecordId
                            )
                            sessionManager.endSession(
                                reason = UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED,
                                note = note,
                                mindfulnessLevel = mindfulnessLevel,
                                driftSeconds = driftSeconds
                            )
                            clearSessionLimitEnding()
                            if (mindfulnessLevel == null && !session.purpose.isNullOrBlank()) {
                                scheduleCompareReminderIfNeeded(
                                    recordId = endingRecordId,
                                    packageName = packageName,
                                    appName = appName,
                                    purpose = session.purpose
                                )
                            }
                            Log.d(
                                TAG,
                                "[$packageName] 单次时长到点 → 回心锚定位 recordId=$endingRecordId" +
                                    " level=$mindfulnessLevel"
                            )
                            leaveTargetThenOpenOwnApp { openMainActivityForRecord(endingRecordId) }
                        }
                    },
                    onExtend = { extraMinutes, namedPurpose ->
                        serviceScope.launch {
                            continueSessionAfterLimit(
                                packageName = packageName,
                                appName = appName,
                                extendMinutes = extraMinutes,
                                namedPurpose = namedPurpose
                            )
                        }
                    },
                    onReviewLater = {
                        deferSessionLimitReview(pushHome = true)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "handleSessionLimitReached 异常", e)
                endInterceptFocusHost(packageName, reopenTarget = false)
                overlayManager.isInterceptVisible.set(false)
                clearSessionLimitEnding()
            }
        }
    }

    private fun clearSessionLimitEnding() {
        sessionLimitEndingRecordId = -1L
        sessionLimitEndingPkg = null
        sessionLimitEndingAppName = ""
        sessionLimitEndingPurpose = null
    }

    /**
     * 到点续时：与胶囊共用一次额度。
     * [namedPurpose] 非空 = 随意浏览半门命名后转意图续段。
     */
    private suspend fun continueSessionAfterLimit(
        packageName: String,
        appName: String,
        extendMinutes: Int,
        namedPurpose: String? = null,
    ) {
        val ok = if (namedPurpose != null) {
            sessionManager.extendBrowseSessionAsIntent(extendMinutes, namedPurpose)
        } else {
            sessionManager.extendSessionOnce(extendMinutes)
        }
        if (!ok) {
            Log.w(
                TAG,
                "[$packageName] 到点续时失败 extra=$extendMinutes named=${namedPurpose != null}"
            )
            overlayManager.isInterceptVisible.set(false)
            endInterceptFocusHost(packageName, reopenTarget = false)
            overlayManager.dismissIntercept()
            return
        }
        analyticsRepository.trackLimitDecision(
            type = HaEvents.InterceptType.SESSION_LIMIT,
            action = HaEvents.LimitAction.EXTEND,
            app = appName,
            pkg = packageName,
            interceptId = currentInterceptId
        )
        clearSessionLimitEnding()
        // 拦截层由 showSessionLimitReached.onExtend 延迟卸下；此处勿立刻 dismissIntercept，
        // 否则 Button ripple 动画未结束就拆 View → IllegalStateException(detached view)
        endInterceptFocusHost(packageName, reopenTarget = true)
        sessionManager.onAppReturnToForeground()
        val live = sessionManager.currentSession.value ?: return
        delay(320)
        overlayManager.showCapsule(live, playEnterAnimation = false)
        Log.d(
            TAG,
            "[$packageName] 到点续时 +${extendMinutes} 分" +
                (namedPurpose?.let { " →意图「$it」" } ?: "") +
                "，胶囊已恢复 fg=${lastForegroundPackage == packageName}"
        )
    }

    private fun scheduleCompareReminderIfNeeded(
        recordId: Long,
        packageName: String,
        appName: String?,
        purpose: String?
    ) {
        if (!appPreferences.isAwarenessPracticeEnabled()) return
        SessionCompareReminderWorker.scheduleIfUnreviewedIntent(
            context = this,
            recordId = recordId,
            packageName = packageName,
            appName = appName?.ifBlank { null } ?: resolveAppNameSync(packageName),
            purpose = purpose
        )
    }

    /**
     * 稍后回顾：结束这次会话（不强制对照），关页，约 8 分钟后发系统通知。
     * [pushHome] 为按钮离开时把底层 App 推回桌面；Home 键已经在桌面则不必再按。
     */
    private fun deferSessionLimitReview(pushHome: Boolean) {
        val recordId = sessionLimitEndingRecordId
        val packageName = sessionLimitEndingPkg
        val appName = sessionLimitEndingAppName
        val purpose = sessionLimitEndingPurpose
        interceptLeaveJob?.cancel()
        interceptLeaveJob = null
        overlayManager.suppressTransientLeaveFeedback()
        if (packageName != null) {
            endInterceptFocusHost(packageName, reopenTarget = false)
        }
        overlayManager.dismissIntercept()
        serviceScope.launch {
            if (packageName != null) {
                analyticsRepository.trackLimitDecision(
                    type = HaEvents.InterceptType.SESSION_LIMIT,
                    action = HaEvents.LimitAction.REVIEW_LATER,
                    app = appName.ifBlank { resolveAppNameSync(packageName) },
                    pkg = packageName,
                    interceptId = currentInterceptId,
                    sessionId = recordId.takeIf { it > 0L }
                )
                val stillOpen = sessionManager.currentSession.value?.recordId == recordId
                if (stillOpen) {
                    sessionManager.endSession(
                        reason = UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED
                    )
                }
            }
            clearSessionLimitEnding()
            if (pushHome && packageName != null &&
                (lastForegroundPackage == packageName || DualSpaceGateActivity.isShowing())
            ) {
                pressHomeButton(pauseMediaForPackage = packageName)
            }
            if (recordId > 0L && packageName != null) {
                scheduleCompareReminderIfNeeded(
                    recordId = recordId,
                    packageName = packageName,
                    appName = appName.ifBlank { resolveAppNameSync(packageName) },
                    purpose = purpose
                )
            }
        }
    }

    /** 日限触顶页：写意图 + 选时长后继续（与拦截入口共用） */
    private suspend fun continueWithDailyGrace(
        packageName: String,
        appName: String,
        purpose: String,
        graceMinutes: Int
    ) {
        beginGateEnterHold(packageName)
        if (appPreferences.hasUsedDailyGraceToday(packageName)) {
            Log.w(TAG, "[$packageName] 今日延长已用过，忽略")
            overlayManager.clearGateEnter(packageName)
            gateEnterHoldPackage = null
            return
        }
        analyticsRepository.trackLimitDecision(
            type = HaEvents.InterceptType.DAILY_LIMIT,
            action = HaEvents.LimitAction.GRACE,
            app = appName,
            pkg = packageName,
            interceptId = currentInterceptId
        )
        val overSession = sessionManager.startOverLimitSession(
            packageName = packageName,
            appName = appName,
            purpose = purpose,
            intentKind = null,
            graceMinutes = graceMinutes
        )
        if (overSession != null) {
            lastForegroundPackage = packageName
            endInterceptFocusHost(packageName, reopenTarget = true)
            withContext(Dispatchers.Main) {
                overlayManager.showCapsule(overSession, playEnterAnimation = false)
            }
            Log.d(
                TAG,
                "[$packageName] 日限延长 ${graceMinutes} 分钟已开启" +
                    " purpose=$purpose [id=${overSession.recordId}]"
            )
        } else {
            overlayManager.clearGateEnter(packageName)
            gateEnterHoldPackage = null
            endInterceptFocusHost(packageName, reopenTarget = false)
            Log.w(TAG, "[$packageName] startOverLimitSession 返回 null")
        }
    }

    private fun handleLimitExceeded(
        packageName: String,
        allowUpgradeFromIntentGate: Boolean = false
    ) {
        if (isGateEnterHoldActive(packageName)) {
            Log.d(TAG, "[$packageName] 进门保护中，跳过时长锁重弹")
            return
        }
        if (overlayManager.gateEnteringPackage == packageName) {
            Log.d(TAG, "[$packageName] 正在确认进入，跳过时长锁重弹")
            return
        }
        if (shouldSkipHardLockReshow(packageName)) {
            Log.d(TAG, "$packageName 时长锁刚被 Home 静默关掉，等待真正再打开")
            return
        }
        // 防止监控循环每秒重复触发：用 isInterceptVisible 作为叠加层针。
        // 打开路径可能已挂了意图门占位：允许升级为时长锁页，避免卡在「今日已用完」意图门。
        val alreadyShowing = overlayManager.isInterceptVisible.getAndSet(true)
        if (alreadyShowing) {
            val sameTarget = overlayManager.interceptTargetPackage == packageName
            val kind = overlayManager.interceptKind
            val alreadyDailyLimitUi =
                (kind == InterceptOverlayKind.DailyLimit ||
                    kind == InterceptOverlayKind.OpenLimit) &&
                    overlayManager.hasInterceptUiAttached()
            if (alreadyDailyLimitUi) return
            val upgradingFromGate = allowUpgradeFromIntentGate &&
                sameTarget &&
                (kind == null ||
                    kind == InterceptOverlayKind.IntentGate ||
                    kind == InterceptOverlayKind.Breath ||
                    !overlayManager.hasInterceptUiAttached())
            if (!upgradingFromGate) return
            Log.d(TAG, "[$packageName] 由意图门占位升级为时长锁页")
        }
        serviceScope.launch {
            try {
                // 触顶后结束当前会话；延长进入时由用户重新写意图
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

                analyticsRepository.trackInterceptShow(
                    type = HaEvents.InterceptType.DAILY_LIMIT,
                    interceptId = beginIntercept(packageName),
                    app = appName,
                    pkg = packageName,
                    capTime = true
                )
                analyticsRepository.trackLimitBlock(
                    type = HaEvents.InterceptType.DAILY_LIMIT,
                    interceptId = currentInterceptId,
                    app = appName,
                    pkg = packageName
                )
                beginInterceptFocusHost(packageName)
                lastForegroundPackage = packageName
                armInterceptShowGuard()
                overlayManager.showLimitReached(
                    packageName = packageName,
                    onDismiss = {
                        analyticsRepository.trackLimitDecision(
                            type = HaEvents.InterceptType.DAILY_LIMIT,
                            action = HaEvents.LimitAction.LEAVE,
                            app = appName,
                            pkg = packageName,
                            interceptId = currentInterceptId
                        )
                        // 只有当被监控的 App 仍在前台时，才需要按 Home 键把用户"推出去"。
                        // 如果用户已经切换到其他 App，直接关闭弹框即可，不应强制跳回桌面。
                        // isInterceptVisible 在 OverlayManager.showLimitReached 的 onDismiss 包装里会被正确清除
                        endInterceptFocusHost(packageName, reopenTarget = false)
                        if (lastForegroundPackage == packageName || DualSpaceGateActivity.isShowing()) {
                            pressHomeButton(pauseMediaForPackage = packageName)
                        }
                    },
                    onReset = {
                        handleResetLimit(packageName)
                    },
                    onContinueWithGrace = { purpose, graceMinutes ->
                        serviceScope.launch {
                            continueWithDailyGrace(
                                packageName = packageName,
                                appName = appName,
                                purpose = purpose,
                                graceMinutes = graceMinutes
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "handleLimitExceeded 出现异常", e)
                endInterceptFocusHost(packageName, reopenTarget = false)
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
            // 先离开被监控 App，再打开设置页，避免返回时掉回原 App
            leaveTargetThenOpenOwnApp {
                val intent = Intent(this@MonitorForegroundService, MainActivity::class.java).apply {
                    action = ACTION_OPEN_APP_LIMIT_EDIT
                    putExtra(EXTRA_APP_PACKAGE_NAME, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.d(TAG, "[Reset] Intent 已发送，等待用户在设置页修改限制")
            }
        }
    }

    /**
     * App 确认离开前台后的分流：
     * - 含意图门且仍在播：收起陪伴条，桌面只留圆球；停播后再静默离开倒计时
     * - 含意图门：桌面可见暂停胶囊 + 离开倒计时，归零弹回顾横条（锁屏期间倒计时继续流逝）；
     *   若进程已死（划掉最近任务 / 强停）：立刻 [APP_CLOSED] 收口，短延迟后再弹横条
     * - 仅时长锁：立刻藏胶囊（仅前台显示）；会话暂停，超时后静默收口；
     *   进程已死则立刻 [APP_CLOSED] 收口
     *
     * @param alreadyInBackground 调用前是否已执行过 onAppGoBackground
     */
    private fun handleAppWentBackground(
        packageName: String,
        alreadyInBackground: Boolean = false
    ) {
        if (isOpenVisitProtected(packageName)) {
            Log.d(TAG, "$packageName 这一次还在（进门未确认或锁屏/屏保），不按离开收口")
            return
        }
        clearOpenVisit(packageName)
        val sessionBefore = sessionManager.currentSession.value
        if (sessionBefore == null || sessionBefore.packageName != packageName) return
        if (!alreadyInBackground && !sessionBefore.isInBackground) {
            sessionManager.onAppGoBackground()
            markSessionBackgroundNow()
        } else if (sessionBackgroundAtElapsed <= 0L) {
            markSessionBackgroundNow()
        }
        if (isLauncherPackage(lastForegroundPackage)) {
            leftToLauncherWhileAway = true
        }
        val session = sessionManager.currentSession.value ?: return
        if (session.packageName != packageName) return

        cancelBackgroundTimeout()

        val processGone = PackageProcessLiveness.isProcessGone(this, packageName)
        if (processGone) {
            Log.d(TAG, "$packageName 离开前台时进程已死：立刻 APP_CLOSED，稍后出横条")
            serviceScope.launch { closeSessionForProcessGone(packageName) }
            return
        }
        if (session.hasIntentGate) {
            if (shouldHoldCompanionOffForMusic(session)) {
                beginMusicAwayHold(packageName)
                return
            }
            stopMusicAwayHold()
            val remain =
                if (awayCountdownRemainingSec > 0L) awayCountdownRemainingSec
                else configuredAwayCountdownSec()
            armAwayCountdownDeadline(remain)
            Log.d(TAG, "$packageName 含意图门：展示暂停胶囊 + ${remain}s 离开倒计时")
            overlayManager.showPausedCapsule(
                session = session,
                returnToAppAction = { launchApp(packageName) },
                awayCountdownSeconds = remain,
                awayCountdownTotalSeconds = configuredAwayCountdownSec()
            )
            startAwayCountdown(packageName)
        } else {
            stopMusicAwayHold()
            Log.d(TAG, "$packageName 仅时长锁：藏起胶囊，静默等待收口")
            clearAwayCountdownState()
            overlayManager.dismissCapsule()
            startSilentBackgroundEnd(packageName)
        }
    }

    /**
     * 监控中的 App 进程已不存在时的统一入口。
     *
     * 立刻 [APP_CLOSED] 收口并拆胶囊；含意图门时再短延迟弹离开横条。
     * 这样「划掉 → 马上重开」会走新进入（拦截页），而不会无缝 resume。
     */
    private suspend fun onMonitoredProcessGone(packageName: String) {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName) {
            overlayManager.dismissCapsule()
            return
        }
        cancelBackgroundDebounce()
        cancelMonitoredSwitchDebounce()
        if (!session.isInBackground) {
            sessionManager.onAppGoBackground()
        }
        if (screenOffPackage == packageName) {
            screenOffPackage = null
            screenOffDeadlineElapsedMs = -1L
            cancelScreenOffTimeout()
        }
        Log.d(
            TAG,
            "$packageName 进程已死：立刻 APP_CLOSED" +
                if (session.hasIntentGate) {
                    "，${PROCESS_GONE_AWAY_COUNTDOWN_SEC}s 后出横条"
                } else {
                    ""
                }
        )
        closeSessionForProcessGone(packageName)
    }

    /**
     * 进程已死：立刻收口为 [APP_CLOSED]，拆胶囊；
     * 含意图门时延迟弹出离开横条（用户若已重开该 App 则不再弹）。
     */
    private suspend fun closeSessionForProcessGone(packageName: String) {
        cancelBackgroundTimeout()
        clearAwayCountdownState()
        stopMusicAwayHold()
        if (lockedWhilePausedPackage == packageName) {
            lockedWhilePausedPackage = null
        }
        if (screenOffPackage == packageName) {
            screenOffPackage = null
            screenOffDeadlineElapsedMs = -1L
            cancelScreenOffTimeout()
        }
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName) {
            overlayManager.dismissCapsule()
            return
        }
        val recordId = session.recordId
        val appName = session.appName
        val purpose = session.purpose
        val durationSeconds = session.currentSessionSeconds
        val showBar = session.hasIntentGate
        Log.d(TAG, "$packageName 进程已死，APP_CLOSED 收口并拆胶囊")
        sessionManager.endSession(UsageRecordEntity.EndReason.APP_CLOSED)
        overlayManager.dismissCapsule()
        if (showBar) {
            scheduleProcessGoneEndedBar(
                packageName = packageName,
                appName = appName,
                recordId = recordId,
                purpose = purpose,
                durationSeconds = durationSeconds
            )
        }
    }

    /** 兼容旧调用名；统一走 [closeSessionForProcessGone] */
    private suspend fun endSessionForProcessDeath(packageName: String) {
        closeSessionForProcessGone(packageName)
    }

    private fun scheduleProcessGoneEndedBar(
        packageName: String,
        appName: String,
        recordId: Long,
        purpose: String?,
        durationSeconds: Long
    ) {
        processGoneEndedBarJob?.cancel()
        processGoneEndedBarJob = serviceScope.launch {
            delay(PROCESS_GONE_AWAY_COUNTDOWN_SEC * 1000L)
            if (shouldSuppressProcessGoneEndedBar(packageName)) {
                Log.d(TAG, "$packageName 进程死后已重开/在拦，跳过离开横条")
                return@launch
            }
            presentAwayEndedBar(
                packageName = packageName,
                appName = appName,
                recordId = recordId,
                purpose = purpose,
                endedAt = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                endReason = UsageRecordEntity.EndReason.APP_CLOSED
            )
        }
    }

    /** 用户已回到该 App（拦截中或新会话）则不再弹进程死离开横条 */
    private fun shouldSuppressProcessGoneEndedBar(packageName: String): Boolean {
        val pending = pendingAwayEndedBar
        if (pending != null && pending.packageName == packageName) {
            return shouldSuppressPendingAwayEndedBar(pending)
        }
        val current = sessionManager.currentSession.value
        return current?.packageName == packageName && !current.isInBackground
    }

    /**
     * 含意图门：离开倒计时（有暂停胶囊时同步更新）。
     * 暂停态锁屏期间倒计时同样流逝；归零后弹出回顾横条。
     * 倒计时期间若进程已死：立刻 [closeSessionForProcessGone]，不再假装「还可点回」。
     */
    private fun startAwayCountdown(packageName: String) {
        cancelBackgroundTimeout()
        syncAwayCountdownRemainingFromDeadline()
        when {
            isAwayCountdownExpired() ||
                (awayCountdownDeadlineElapsedMs > 0L && awayCountdownRemainingSec == 0L) -> {
                overlayManager.updateAwayCountdown(0L)
                serviceScope.launch {
                    val session = sessionManager.currentSession.value
                    if (session == null || session.packageName != packageName) return@launch
                    // 倒计时已归零：即使被假回报标成前台，也要收口，勿继续转圈计时
                    if (!session.isInBackground) {
                        Log.d(TAG, "$packageName 离开倒计时已归零但会话误标前台，强制收口")
                        sessionManager.onAppGoBackground()
                    }
                    val ending = sessionManager.currentSession.value ?: session
                    if (ending.packageName == packageName) {
                        finishAwayCountdown(ending)
                    }
                }
                return
            }
            awayCountdownDeadlineElapsedMs <= 0L -> {
                val remain = if (awayCountdownRemainingSec > 0L) {
                    awayCountdownRemainingSec
                } else {
                    configuredAwayCountdownSec()
                }
                armAwayCountdownDeadline(remain)
            }
            else -> syncAwayCountdownRemainingFromDeadline()
        }
        overlayManager.updateAwayCountdown(awayCountdownRemainingSec)
        backgroundTimeoutJob = serviceScope.launch {
            Log.d(TAG, "$packageName 离开倒计时开始 remaining=${awayCountdownRemainingSec}s")
            while (isActive) {
                delay(250L)
                val session = sessionManager.currentSession.value
                if (session == null || session.packageName != packageName) {
                    Log.d(TAG, "$packageName 离开倒计时取消（会话结束）")
                    return@launch
                }
                if (!session.isInBackground) {
                    // 假回报会清掉 deadline；若 deadline 仍在且已到期，强制收口
                    syncAwayCountdownRemainingFromDeadline()
                    if (isAwayCountdownExpired() ||
                        (awayCountdownDeadlineElapsedMs > 0L && awayCountdownRemainingSec == 0L)
                    ) {
                        Log.d(TAG, "$packageName 离开倒计时归零（会话误标前台），强制收口")
                        sessionManager.onAppGoBackground()
                        val ending = sessionManager.currentSession.value ?: session
                        finishAwayCountdown(ending)
                    } else {
                        Log.d(TAG, "$packageName 离开倒计时取消（已回前台）")
                    }
                    return@launch
                }
                if (PackageProcessLiveness.isProcessGone(this@MonitorForegroundService, packageName)) {
                    Log.d(TAG, "$packageName 离开倒计时期间进程已死，立刻 APP_CLOSED")
                    overlayManager.dismissCapsule()
                    closeSessionForProcessGone(packageName)
                    return@launch
                }
                syncAwayCountdownRemainingFromDeadline()
                if (awayCountdownRemainingSec > 0L) {
                    overlayManager.updateAwayCountdown(awayCountdownRemainingSec)
                    continue
                }
                break
            }
            val session = sessionManager.currentSession.value
            if (session == null || session.packageName != packageName) {
                return@launch
            }
            if (!session.isInBackground) {
                syncAwayCountdownRemainingFromDeadline()
                if (!isAwayCountdownExpired() && awayCountdownRemainingSec > 0L) return@launch
                sessionManager.onAppGoBackground()
            } else if (!isAwayCountdownExpired() && awayCountdownRemainingSec > 0L) {
                return@launch
            }
            val ending = sessionManager.currentSession.value ?: session
            Log.d(TAG, "$packageName 离开倒计时归零，按中断收口并弹回顾横条")
            finishAwayCountdown(ending)
        }
    }

    /** 离开倒计时归零：收口会话、拆胶囊；亮屏弹横条，锁屏/息屏发通知。 */
    private suspend fun finishAwayCountdown(session: UsageSession) {
        val packageName = session.packageName
        val endingRecordId = session.recordId
        val endingAppName = session.appName
        val durationSeconds = session.currentSessionSeconds
        val endedAt = System.currentTimeMillis()
        overlayManager.capsuleAppPackageName.value = packageName
        sessionManager.endSession(UsageRecordEntity.EndReason.AWAY_COUNTDOWN)
        clearAwayCountdownState()
        stopMusicAwayHold()
        if (lockedWhilePausedPackage == packageName) {
            lockedWhilePausedPackage = null
        }
        overlayManager.dismissCapsule()
        presentAwayEndedBar(
            packageName = packageName,
            appName = endingAppName,
            recordId = endingRecordId,
            purpose = session.purpose,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            endReason = UsageRecordEntity.EndReason.AWAY_COUNTDOWN
        )
        if (pendingAwayEndedBar != null && !overlayManager.isAwayEndedBarShowing()) {
            scheduleFlushPendingAwayEndedBar(forceAfterFirst = true)
        }
    }

    /**
     * 用户已再次点开 App、马上要过意图门：只静默收口旧后台会话，
     * 不弹离开回顾横条（避免叠在门口上）。
     */
    private suspend fun endStaleBackgroundSessionForGate(session: UsageSession) {
        val packageName = session.packageName
        overlayManager.suppressTransientLeaveFeedback()
        sessionManager.endSession(UsageRecordEntity.EndReason.AWAY_COUNTDOWN)
        clearAwayCountdownState()
        stopMusicAwayHold()
        if (lockedWhilePausedPackage == packageName) {
            lockedWhilePausedPackage = null
        }
        overlayManager.dismissCapsule()
        Log.d(TAG, "$packageName 过期后台会话已静默收口，准备过门")
    }

    /** 仅时长锁：无桌面胶囊，超时后静默结束（不写中断确认） */
    private fun startSilentBackgroundEnd(packageName: String) {
        cancelBackgroundTimeout()
        backgroundTimeoutJob = serviceScope.launch {
            val waitMs = configuredAwayCountdownSec() * 1000L
            Log.d(TAG, "$packageName 纯时长锁静默收口计时 ${waitMs}ms")
            val deadline = SystemClock.elapsedRealtime() + waitMs
            while (isActive && SystemClock.elapsedRealtime() < deadline) {
                delay(500L)
                val session = sessionManager.currentSession.value
                if (session == null || session.packageName != packageName || !session.isInBackground) {
                    return@launch
                }
                if (PackageProcessLiveness.isProcessGone(this@MonitorForegroundService, packageName)) {
                    Log.d(TAG, "$packageName 纯时长锁等待期间进程已死")
                    endSessionForProcessDeath(packageName)
                    return@launch
                }
            }
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
     * 灭屏后回到这个 App：先看现在是否落在时段锁里。
     * 在锁里就收口并硬挡；不在锁里则把陪伴条续上，时间从停住的地方继续。
     */
    private suspend fun tryResumeAfterScreenOff(packageName: String, reason: String): Boolean {
        if (screenOffPackage != packageName) return false
        sessionManager.rollToNewDayIfNeeded()
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName) {
            Log.w(TAG, "[$packageName] 灭屏恢复失败（无会话），reason=$reason")
            screenOffPackage = null
            cancelScreenOffTimeout()
            return false
        }
        if (!session.isInBackground) {
            cancelScreenOffTimeout()
            screenOffPackage = null
            return true
        }

        if (screenIsOff || isDeviceLockedOrAsleep()) return false

        if (isPackagePeriodHardLocked(packageName)) {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentFg = getForegroundPackage(usageStatsManager)
            if (currentFg != packageName) {
                Log.d(TAG, "[$packageName] 亮屏已在时段锁内，但还没回到 App（$currentFg），先停着")
                return false
            }
            Log.d(TAG, "[$packageName] 亮屏回到 App 时已在时段锁内，reason=$reason")
            handlePeriodLock(packageName, endActiveSession = true)
            return true
        }

        if (reason == "USER_PRESENT" || reason == "DREAMING_STOPPED") {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentFg = getForegroundPackage(usageStatsManager)
            if (currentFg != packageName) {
                Log.d(TAG, "[$packageName] $reason 后尚未回到 App（当前前台=$currentFg），保持停住")
                return false
            }
            lastForegroundPackage = packageName
        }

        resumeBackgroundSession(packageName, session)
        Log.d(TAG, "[$packageName] 灭屏后续上这一次，reason=$reason")
        return true
    }

    /** 从墙钟 deadline 解析离开倒计时剩余秒；无 deadline 时回退配置值 */
    private fun resolveAwayCountdownRemainSec(): Long {
        syncAwayCountdownRemainingFromDeadline()
        if (awayCountdownRemainingSec > 0L) return awayCountdownRemainingSec
        if (awayCountdownDeadlineElapsedMs > 0L) {
            syncAwayCountdownRemainingFromDeadline()
            return awayCountdownRemainingSec.coerceAtLeast(0L)
        }
        return configuredAwayCountdownSec()
    }

    /**
     * 桌面暂停态：在【暂停恢复时长】内保持胶囊可见，离开倒计时墙钟不停。
     * 锁屏后系统可能拆掉悬浮窗，亮屏时强制重挂。
     */
    private suspend fun ensureIntentPauseCapsuleAlive(packageName: String) {
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName || !session.isInBackground) {
            if (lockedWhilePausedPackage == packageName) {
                lockedWhilePausedPackage = null
            }
            maybeSendAwayEndedNotificationIfNeeded(packageName)
            return
        }
        if (!session.hasIntentGate) return
        if (isMusicAwayHold(packageName)) {
            overlayManager.dismissCapsule()
            if (isAwayCountdownExpired() ||
                (awayCountdownDeadlineElapsedMs > 0L && awayCountdownRemainingSec == 0L)
            ) {
                lockedWhilePausedPackage = null
                finishAwayCountdown(session)
            }
            return
        }
        if (PackageProcessLiveness.isProcessGone(this, packageName)) {
            onMonitoredProcessGone(packageName)
            return
        }
        syncAwayCountdownRemainingFromDeadline()
        if (isAwayCountdownExpired() ||
            (awayCountdownDeadlineElapsedMs > 0L && awayCountdownRemainingSec == 0L)
        ) {
            Log.d(TAG, "[$packageName] 离开倒计时已归零，收口并提示对照")
            lockedWhilePausedPackage = null
            finishAwayCountdown(session)
            return
        }
        if (awayCountdownDeadlineElapsedMs <= 0L) {
            armAwayCountdownDeadline(resolveAwayCountdownRemainSec().coerceAtLeast(1L))
        }
        if (isPackagePeriodHardLocked(packageName)) {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentFg = getForegroundPackage(usageStatsManager)
            if (currentFg == packageName) {
                handlePeriodLock(packageName, endActiveSession = true)
            } else {
                enforcePeriodLockWhileAway(packageName)
            }
            lockedWhilePausedPackage = null
            return
        }
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentFg = getForegroundPackage(usageStatsManager)
        if (currentFg == packageName) {
            if (isSpuriousAppForegroundWhileAway(packageName)) {
                Log.d(TAG, "[$packageName] UsageStats 误报回前台，保持桌面暂停")
            } else {
                lastForegroundPackage = packageName
                resumeBackgroundSession(packageName, session)
                return
            }
        }
        if (backgroundTimeoutJob?.isActive != true) {
            startAwayCountdown(packageName)
        }
        val remain = awayCountdownRemainingSec.coerceAtLeast(0L)
        overlayManager.updateAwayCountdown(remain)
        if (!overlayManager.isCapsuleAttached() || !overlayManager.capsuleIsPaused.value) {
            Log.d(TAG, "[$packageName] 重挂暂停胶囊 remaining=${remain}s")
            showPausedCapsule(session)
        }
    }

    /**
     * 展示切走后的暂停态：
     * - 含意图门：暂停胶囊 + 剩余离开倒计时；进程已死则立刻 APP_CLOSED
     * - 仅时长锁：保持藏起
     */
    private fun showPausedCapsule(session: UsageSession) {
        val packageName = session.packageName
        if (shouldHoldCompanionOffForMusic(session)) {
            beginMusicAwayHold(packageName)
            return
        }
        stopMusicAwayHold()
        if (!session.hasIntentGate) {
            overlayManager.dismissCapsule()
            if (backgroundTimeoutJob?.isActive != true) {
                startSilentBackgroundEnd(packageName)
            }
            return
        }
        if (PackageProcessLiveness.isProcessGone(this, packageName)) {
            Log.d(TAG, "[$packageName] 还原暂停态时进程已死：立刻 APP_CLOSED")
            serviceScope.launch { closeSessionForProcessGone(packageName) }
            return
        }
        val remain = resolveAwayCountdownRemainSec().coerceAtLeast(1L)
        if (awayCountdownDeadlineElapsedMs <= 0L) {
            armAwayCountdownDeadline(remain)
        } else {
            awayCountdownRemainingSec = remain
        }
        overlayManager.showPausedCapsule(
            session = session,
            returnToAppAction = { launchApp(packageName) },
            awayCountdownSeconds = remain,
            awayCountdownTotalSeconds = configuredAwayCountdownSec()
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
        if (overlayManager.interceptTargetPackage == packageName &&
            overlayManager.isInterceptVisible.get()
        ) {
            overlayManager.dismissIntercept(keepCapsule = true)
        }
        dismissForeignInterceptIfNeeded(packageName)
        stopMusicAwayHold()
        sessionManager.onAppReturnToForeground()
        clearSessionBackgroundMark()
        cancelBackgroundTimeout()
        cancelScreenOffTimeout()
        screenOffPackage = null
        screenOffDeadlineElapsedMs = -1L
        lockedWhilePausedPackage = null
        clearAwayCountdownState()
        val restoredSession = sessionManager.currentSession.value ?: existingSession
        // 同包已挂悬浮层时不拆建，避免计时数字来回跳动
        overlayManager.resumeOrShowCapsule(restoredSession)
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
     * 启动锁屏宽限超时协程（墙钟驱动，锁屏/Doze 下仍准确）。
     */
    private fun startScreenOffTimeout(packageName: String) {
        cancelScreenOffTimeout()
        val graceMs = configuredScreenOffGraceMs()
        val graceSec = configuredAwayCountdownSec()
        if (screenOffDeadlineElapsedMs <= 0L) {
            screenOffDeadlineElapsedMs = SystemClock.elapsedRealtime() + graceMs
        }
        screenOffTimeoutJob = serviceScope.launch {
            val waitMs =
                (screenOffDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            Log.d(TAG, "[$packageName] 锁屏宽限计时启动，${graceSec} 秒后超时")
            delay(waitMs)
            finalizeScreenOffTimeoutIfNeeded(packageName)
        }
    }

    /**
     * 息屏宽限到期：静默结束会话并写入续接快照。
     * @return true 表示本次确实执行了收口
     */
    private suspend fun finalizeScreenOffTimeoutIfNeeded(packageName: String): Boolean {
        if (screenOffPackage != packageName && !isScreenOffGraceExpired()) return false
        val session = sessionManager.currentSession.value
        if (session == null || session.packageName != packageName || !session.isInBackground) {
            if (screenOffPackage == packageName) {
                screenOffPackage = null
            }
            screenOffDeadlineElapsedMs = -1L
            cancelScreenOffTimeout()
            return false
        }
        if (!isScreenOffGraceExpired()) return false
        Log.d(TAG, "[$packageName] 锁屏宽限期超时，结束会话并准备退出")
        sessionManager.rollToNewDayIfNeeded()
        val ending = sessionManager.currentSession.value ?: session
        val recordId = ending.recordId
        val appName = ending.appName
        val purpose = ending.purpose
        sessionManager.endSession(UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT)
        clearOpenVisit(packageName)
        screenOffForcedExitPkg = packageName
        overlayManager.dismissAll()
        scheduleCompareReminderIfNeeded(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            purpose = purpose
        )
        if (screenOffPackage == packageName) {
            screenOffPackage = null
        }
        if (lockedWhilePausedPackage == packageName) {
            lockedWhilePausedPackage = null
        }
        screenOffDeadlineElapsedMs = -1L
        return true
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

    /** 意图门进入后落地：搜索深链或固定页深链；失败则静默（App 已在前台） */
    private suspend fun maybeLandSearchDeepLink(
        packageName: String,
        purpose: String,
        landMode: IntentLandMode,
        landDeepLinkId: String? = null
    ) {
        when (landMode) {
            IntentLandMode.SEARCH -> {
                val query = purpose.trim()
                if (query.isEmpty()) return
                val entry = SearchDeepLinkCatalog.primaryKeywordEntry(packageName) ?: return
                delay(280)
                val err = SearchDeepLinkLauncher.launch(this, entry, query)
                if (err != null) {
                    Log.w(TAG, "搜索深链未落地 $packageName: $err")
                }
            }
            IntentLandMode.DEEPLINK -> {
                val entry = landDeepLinkId
                    ?.let { AppDeepLinkCatalog.findById(it) }
                    ?.takeIf { it.packageName == packageName }
                    ?: AppDeepLinkCatalog.resolveFromPurpose(packageName, purpose)
                if (entry == null) {
                    Log.w(TAG, "固定页深链未找到 $packageName purpose=$purpose id=$landDeepLinkId")
                    return
                }
                delay(280)
                val err = SearchDeepLinkLauncher.launch(this, entry)
                if (err != null) {
                    Log.w(TAG, "固定页深链未落地 $packageName (${entry.id}): $err")
                }
            }
            IntentLandMode.NORMAL -> Unit
        }
    }

    private fun peekForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return lastForegroundPackage
        return getForegroundPackage(usm) ?: lastForegroundPackage
    }

    private fun isPackageLikelyForeground(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return peekForegroundPackage() == packageName
    }

    private fun markRecentsHint() {
        interceptRecentsOpen = true
        lastRecentsHintElapsed = SystemClock.elapsedRealtime()
        cancelInterceptRecentsHomeWatch()
    }

    /** 进程列表刚打开：UsageStats 仍报目标 App，不能把门盖回去，更不能按 Home。 */
    private fun shouldHoldRecentsPark(): Boolean {
        if (!overlayManager.interceptParkedForRecents && !interceptRecentsOpen) return false
        val now = SystemClock.elapsedRealtime()
        if (now - recentsParkedAtElapsed in 0 until RECENTS_PARK_HOLD_MS) return true
        if (now - lastRecentsHintElapsed in 0 until RECENTS_PARK_HOLD_MS) return true
        return false
    }

    private fun recentsHomeKeyShouldIgnore(): Boolean {
        if (!overlayManager.interceptParkedForRecents && !interceptRecentsOpen) return false
        val now = SystemClock.elapsedRealtime()
        return now - lastRecentsHintElapsed < RECENTS_HOME_KEY_IGNORE_MS ||
            now - recentsParkedAtElapsed < RECENTS_HOME_KEY_IGNORE_MS
    }

    /**
     * 拦截层失焦。通知栏 / 输入法也会失焦，不能一上来就卸层。
     * 进程列表：卸层露出概览，绝不能按 Home / 守住离开。
     */
    private fun onInterceptWindowLostFocus() {
        if (isDeviceLockedOrAsleep()) return
        if (overlayManager.interceptParkedForRecents) return
        val target = overlayManager.interceptTargetPackage ?: return
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) return
        if (isGateEnterHoldActive(target) || overlayManager.gateEnteringPackage == target) return
        if (interceptGateDismissingPkg == target) return

        serviceScope.launch {
            delay(80L)
            if (isDeviceLockedOrAsleep()) return@launch
            if (overlayManager.interceptParkedForRecents) return@launch
            if (interceptGateDismissingPkg == target) return@launch
            if (!overlayManager.isInterceptLayerOnScreen(target)) return@launch
            if (overlayManager.interceptWindowHasFocus()) return@launch

            val recentsHintFresh =
                SystemClock.elapsedRealtime() - lastRecentsHintElapsed < 1_500L
            if (interceptRecentsOpen || recentsHintFresh) {
                Log.d(TAG, "$target 失焦像进程列表，拉回门口")
                pullInterceptBack(target, closeOverview = true)
                return@launch
            }
            val fg = peekForegroundPackage()
            val shownAt = overlayManager.interceptShownAtWall
            val withinShowGuard = shownAt > 0L &&
                System.currentTimeMillis() - shownAt < INTERCEPT_SHOW_GUARD_MS
            if (withinShowGuard) {
                overlayManager.requestInterceptFocus()
                return@launch
            }
            if (isLauncherPackage(fg)) {
                Log.d(TAG, "$target 失焦且桌面在前，不守住（概览宿主），拉回焦点")
                overlayManager.requestInterceptFocus()
                return@launch
            }
            Log.d(TAG, "$target 失焦但前台=$fg，不卸层（可能是通知栏/输入法/开场抢焦）")
            overlayManager.requestInterceptFocus()
        }
    }

    /**
     * 无障碍观察到系统 Home：立刻卸拦截页并守住。
     * TYPE_APPLICATION_OVERLAY 收不到 KEYCODE_HOME，必须靠这条路径。
     */
    fun onSystemHomePressedDuringIntercept() {
        val target = overlayManager.interceptTargetPackage ?: return
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) {
            deferSessionLimitReview(pushHome = true)
            return
        }
        if (interceptGateDismissingPkg == target) return
        Log.d(TAG, "$target 拦截页期间 Home，拉回门口")
        pullInterceptBack(target, closeOverview = false)
    }

    /** 无障碍观察到系统返回：与拦截页返回键一致。 */
    fun onSystemBackPressedDuringIntercept() {
        val target = overlayManager.interceptTargetPackage ?: return
        if (overlayManager.interceptParkedForRecents) return
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) {
            deferSessionLimitReview(pushHome = true)
            return
        }
        if (interceptGateDismissingPkg == target) return
        Log.d(TAG, "$target 无障碍观察到返回键，按守住离开")
        completeInterceptGateDismiss(target, pressHomeAfter = true)
    }

    fun onRecentsOpenedFromAccessibility() {
        val target = overlayManager.interceptTargetPackage ?: return
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) return
        if (isGateEnterHoldActive(target) || overlayManager.gateEnteringPackage == target) return
        if (interceptGateDismissingPkg == target) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecentsSuppressElapsed < 700L) return
        lastRecentsSuppressElapsed = now
        Log.d(TAG, "$target 拦截期间进程列表被打开，拉回拦截页")
        pullInterceptBack(target, closeOverview = true)
    }

    /** 拦截期间不卸层。进程列表先退回，门不在就补挂。 */
    private fun pullInterceptBack(target: String, closeOverview: Boolean) {
        overlayManager.clearRecentsPark()
        interceptRecentsOpen = false
        if (closeOverview) KeepAliveAccessibilityService.pressBack()
        if (!overlayManager.isInterceptLayerOnScreen(target)) {
            serviceScope.launch { showInterceptOverlay(target, countImpulse = false) }
        } else {
            overlayManager.requestInterceptFocus()
        }
    }

    private fun parkInterceptForRecentsAndOpenOverview(
        reason: String,
        openOverview: Boolean = true
    ) {
        val target = overlayManager.interceptTargetPackage ?: return
        if (overlayManager.interceptKind == InterceptOverlayKind.SessionLimit) return
        markRecentsHint()
        recentsParkedAtElapsed = SystemClock.elapsedRealtime()
        interceptHomeDebounceJob?.cancel()
        interceptHomeDebounceJob = null
        overlayManager.suppressTransientLeaveFeedback()
        overlayManager.parkInterceptForRecents()
        cancelInterceptRecentsHomeWatch()
        Log.d(TAG, "$target 拦截页进入最近任务（$reason），卸层露出进程列表")
        if (openOverview) {
            mainHandlerOrImmediate {
                KeepAliveAccessibilityService.openRecents()
            }
        }
    }

    private fun mainHandlerOrImmediate(block: () -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            main.post(block)
        }
    }

    private fun cancelInterceptRecentsHomeWatch() {
        interceptRecentsHomeJob?.cancel()
        interceptRecentsHomeJob = null
    }

    private fun isSystemUiPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return pkg == "com.android.systemui" || pkg.endsWith(".systemui")
    }

    /** 音量条 / 电源菜单 / 部分厂商控制中心：短暂盖上不算离开。 */
    private fun isTransientSystemOverlayPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (isSystemUiPackage(pkg)) return true
        val p = pkg.lowercase()
        return p.contains("systemui") ||
            p.contains("volume") ||
            p.contains("flashlight") ||
            p.endsWith(".ops.systemui") ||
            p.contains("controlcenter") ||
            p.contains("miui.systemui") ||
            p.contains("screenshot") ||
            p.contains("globalactions") ||
            p.contains("floattouch") ||
            p.contains("touchassistant")
    }

    /**
     * 从最近任务回来：同 App → 重挂拦截页；其他 App → 静默关页。
     * 概览宿主常是桌面：停在进程列表里时 UsageStats 仍报目标 App 或 launcher，
     * 绝不能立刻重挂门，更不能按 Home 把用户送回桌面。
     */
    private fun handleReturnFromRecents(
        currentPkg: String?,
        fromAccessibility: Boolean
    ): Boolean {
        if (!overlayManager.interceptParkedForRecents) return false
        val target = overlayManager.interceptTargetPackage ?: run {
            overlayManager.clearRecentsPark()
            interceptRecentsOpen = false
            cancelInterceptRecentsHomeWatch()
            return false
        }
        if (isSystemUiPackage(currentPkg)) {
            return true
        }
        if (isLauncherPackage(currentPkg) || currentPkg.isNullOrBlank()) {
            // 用户正在进程列表（桌面宿主）里滑：保持卸层，不守住、不按 Home、
            // 也不刷新 recents hint（否则桌面上残留 park 会把下一次点开吞掉）。
            return true
        }
        if (currentPkg == target) {
            if (shouldHoldRecentsPark()) {
                Log.d(TAG, "$target 进程列表刚打开，保持卸层")
                return true
            }
            if (!fromAccessibility) {
                val recentsStale =
                    SystemClock.elapsedRealtime() - lastRecentsHintElapsed > 2_000L
                if (!recentsStale) {
                    Log.d(TAG, "$target 进程列表期间 UsageStats 仍报本包，保持卸层")
                    return true
                }
            }
            Log.d(TAG, "$target 从最近任务/桌面回到本包，重挂拦截页 a11y=$fromAccessibility")
            overlayManager.clearRecentsPark()
            cancelInterceptRecentsHomeWatch()
            lastForegroundPackage = target
            serviceScope.launch {
                showInterceptOverlay(target, countImpulse = false)
            }
            return true
        }
        Log.d(TAG, "$target 最近任务切到其他 App（$currentPkg），静默关页")
        overlayManager.clearRecentsPark()
        interceptRecentsOpen = false
        cancelInterceptRecentsHomeWatch()
        silentDismissInterceptGate(target)
        return true
    }

    /**
     * 盖层抢走焦点后，UsageStats 可能仍报目标 App。
     * 只有「真回桌面 / 切到其他 App」才启动拦截页离开，避免门自己消失。
     */
    private fun shouldBeginInterceptLeave(
        currentPkg: String?,
        targetPackage: String,
        fromAccessibility: Boolean
    ): Boolean {
        if (currentPkg.isNullOrBlank()) return false
        if (isAccountingHomeLeave(currentPkg, targetPackage)) return false
        if (currentPkg == targetPackage || currentPkg == packageName) return false
        if (isInterceptLeaveToHome(currentPkg, targetPackage, fromAccessibility)) return true
        if (isLauncherPackage(currentPkg)) return false
        return true
    }

    /**
     * @param pauseMediaForPackage 非空且该包正在播放时才停媒体。
     * 守住离开（从未进门）勿传，否则会把其他 App 的音乐停掉。
     */
    private fun pressHomeButton(pauseMediaForPackage: String? = null) {
        if (!pauseMediaForPackage.isNullOrBlank()) {
            BackgroundMediaPauser.pauseActivePlayback(this, pauseMediaForPackage)
        }
        // 华为等 ROM 常拦截后台 Service 的 HOME Intent；无障碍模拟 Home 更稳
        val a11yOk = KeepAliveAccessibilityService.goHome()
        if (a11yOk) {
            Log.d(TAG, "已通过无障碍回桌面")
        }
        // 无障碍返回 true 时部分 ROM 仍不切任务；HOME Intent 再补一拍
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
            Log.d(TAG, "已通过 HOME Intent 回桌面")
        } catch (e: Exception) {
            if (!a11yOk) {
                Log.e(TAG, "回桌面失败：请开启无障碍保活，或允许后台弹出界面", e)
            }
        }
    }

    /** 主动结束 / 守住后若目标仍在前台，补按 Home */
    private fun scheduleEnsureLeftForeground(packageName: String?) {
        val target = packageName?.takeIf { it.isNotBlank() } ?: return
        serviceScope.launch {
            delay(220L)
            if (!isPackageLikelyForeground(target)) return@launch
            Log.d(TAG, "$target 结束后仍在前台，再补一次回桌面")
            pressHomeButton(pauseMediaForPackage = target)
            delay(280L)
            if (isPackageLikelyForeground(target)) {
                pressHomeButton(pauseMediaForPackage = target)
            }
        }
    }

    /**
     * 先把被监控 App 推回桌面，再打开心锚相关页。
     * 避免退出心锚时系统恢复被拦 App 的 task，再次触发拦截。
     * 若已在心锚内则直接执行 [open]。
     */
    private fun leaveTargetThenOpenOwnApp(open: () -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            val interceptPkg = overlayManager.interceptTargetPackage
            if (interceptPkg != null) {
                endInterceptFocusHost(interceptPkg, reopenTarget = false)
            } else {
                DualSpaceGateActivity.finishIfShowing()
            }
            if (lastForegroundPackage == packageName) {
                open()
                return@post
            }
            val target = overlayManager.capsuleAppPackageName.value
                .takeIf { it.isNotBlank() }
                ?: overlayManager.interceptTargetPackage
            pressHomeButton(pauseMediaForPackage = target)
            main.postDelayed({ open() }, 180L)
        }
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

    /** 打开某 App 的监控详情；[capability] 非空则直达该能力配置页 */
    private fun openMainActivityForAppLimitEdit(
        targetPackage: String,
        capability: String? = null
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP_LIMIT_EDIT
            putExtra(EXTRA_APP_PACKAGE_NAME, targetPackage)
            if (!capability.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_CAPABILITY, capability)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun openMainActivityForQuickIntentTags(targetPackage: String, appName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_QUICK_INTENT_TAGS
            putExtra(EXTRA_APP_PACKAGE_NAME, targetPackage)
            if (appName.isNotBlank()) putExtra(EXTRA_APP_NAME, appName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /** 打开某 App 的使用记录页（离开轻条「详细」） */
    private fun openMainActivityForAppHistory(targetPackage: String, recordId: Long = -1L) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP_HISTORY
            putExtra(EXTRA_APP_PACKAGE_NAME, targetPackage)
            if (recordId >= 0L) putExtra(EXTRA_NOTE_RECORD_ID, recordId)
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

    /** 打开心锚并自动弹出对照（离开超时横条「对照一下」） */
    private fun openMainActivityForCompare(recordId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTE
            putExtra(EXTRA_NOTE_RECORD_ID, recordId)
            putExtra(EXTRA_SESSION_REVIEWED, false)
            putExtra(EXTRA_OPEN_COMPARE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * 暂停胶囊离开超时、且当时锁屏/息屏：发通知栏对照提醒（解锁不再弹横条）。
     * 亮屏超时仍走悬浮横条。
     */
    private fun sendAwayEndedCompareNotification(
        packageName: String,
        appName: String,
        recordId: Long,
        purpose: String?
    ) {
        if (recordId <= 0L) return
        if (awayEndedNotifiedRecordIds.contains(recordId)) return
        awayEndedNotifiedRecordIds.add(recordId)

        val displayApp = appName.trim().ifBlank {
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (_: Exception) {
                packageName.substringAfterLast(".")
            }
        }
        val intentLine = purpose?.trim()?.takeIf { it.isNotEmpty() }
        val contentText = when {
            intentLine != null -> "「$intentLine」· 点一下对照"
            else -> "$displayApp · 点一下对照"
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (SESSION_END_NOTIFICATION_ID + recordId.toInt()).and(0x7FFFFFFF),
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_RECORD_ID, recordId)
                putExtra(EXTRA_SESSION_REVIEWED, false)
                putExtra(EXTRA_OPEN_COMPARE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setContentTitle("暂停已结束")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            (SESSION_END_NOTIFICATION_ID + recordId.toInt()).and(0x7FFFFFFF),
            notification
        )
    }

    /**
     * 发送「会话已结束」轻量通知。
     * 仅用于无意图的手动结束：用通知作回看入口，有意图时改由对照提醒承接。
     */
    private fun sendSessionEndNotification(
        endedPackage: String,
        recordId: Long
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
                putExtra(EXTRA_SESSION_REVIEWED, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setContentTitle("计时已结束 ✓")
            .setContentText("$appName · 点此回看")
            .setSmallIcon(R.drawable.ic_stat_anchor)
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
            description = "计时结束、离开超时等待对照等提醒"
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
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun beginIntercept(packageName: String): String {
        currentInterceptId = AnalyticsBuckets.newInterceptId()
        return currentInterceptId
    }

    private fun resolveAppNameSync(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').ifBlank { packageName }
        }
    }
}
