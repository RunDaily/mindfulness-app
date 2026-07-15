package com.life.mindfulnessapp.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
        /** 打开 AppList 页并自动弹出指定 App 编辑对话框的 Intent Action */
        const val ACTION_OPEN_APP_LIMIT_EDIT = "ACTION_OPEN_APP_LIMIT_EDIT"
        /** Intent extra key：要编辑限制的 App 包名 */
        const val EXTRA_APP_PACKAGE_NAME = "extra_app_package_name"
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
        /** 未锁屏后台超时：2 分钟后弹出确认弹窗 */
        const val BACKGROUND_TIMEOUT_MS = 2 * 60 * 1000L
        const val BACKGROUND_TIMEOUT_MINUTES = 2
        /** 确认弹窗的最长等待时间：超过 1 分钟未操作则自动结束会话 */
        const val CONFIRM_DIALOG_TIMEOUT_MS = 60 * 1000L
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
         * 超过 3 分钟未回到该 App，则静默结束会话。
         */
        const val SCREEN_OFF_GRACE_MS = 3 * 60 * 1000L
        const val SCREEN_OFF_GRACE_MINUTES = 3

        // ── 每日简报推送相关常量 ────────────────────────────────────────────────
        /** 每日简报推送通知渠道 ID */
        const val DAILY_BRIEF_CHANNEL_ID = "daily_brief_notification"
        /** 每日简报推送通知 ID */
        const val DAILY_BRIEF_NOTIFICATION_ID = 2001
        /** 每日简报检测帧间隔：30 秒检测一次，判断是否到了小时:分钟 */
        const val DAILY_BRIEF_POLL_INTERVAL_MS = 30_000L

        // ── 格言推送相关常量 ───────────────────────────────────────────────────
        /** 格言推送通知渠道 ID */
        const val QUOTE_REMINDER_CHANNEL_ID = "quote_reminder_notification"
        /** 格言推送通知 ID */
        const val QUOTE_REMINDER_NOTIFICATION_ID = 4001
        /** 格言推送协程检测间隔：60 秒检测一次 */
        const val QUOTE_REMINDER_POLL_INTERVAL_MS = 60_000L
        /** 格言推送的结束时间（固定 22:00，不打扰深夜）*/
        const val QUOTE_REMINDER_END_HOUR = 22
        /** 开启格言推送所需最低收藏数 */
        const val QUOTE_REMINDER_MIN_FAVORITES = 3

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
    @Inject lateinit var quoteRepository: QuoteRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var backgroundTimeoutJob: Job? = null
    /** 防抖：检测到被监控 App 离开前台后，延迟确认是否真正进入后台的协程 */
    private var backgroundDebounceJob: Job? = null
    /** 每日简报专用协程（独立于主监控循环，30 秒检测一次） */
    private var dailyBriefJob: Job? = null
    /** 每日简报：记录已发送简报的日期（yyyy-MM-dd），避免同一天重复发送 */
    private var lastBriefDate: String = ""
    /** 格言推送专用协程（每 60 秒检测一次是否到达下次推送时间） */
    private var quoteReminderJob: Job? = null
    /** 格言推送：上次发送时间戳（毫秒），用于计算间隔 */
    private var lastQuoteReminderTimeMs: Long = 0L
    /** 常驻通知刷新协程（每分钟刷新一次，展示今日使用汇总） */
    private var notificationRefreshJob: Job? = null

    /**
     * 锁屏超时协程：息屏后启动，3 分钟内如果用户未回到被监控 App，静默结束会话。
     * 亮屏并回到 App 时取消。
     */
    private var screenOffTimeoutJob: Job? = null

    /**
     * 息屏时被监控 App 的包名（用于亮屏后判断是否需要恢复会话）。
     * 仅在息屏时有会话存在时才会被赋值，亮屏后清除。
     */
    private var screenOffPackage: String? = null

    private var enabledPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null

    /**
     * 锁屏 / 亮屏广播接收器：
     *
     * - ACTION_SCREEN_OFF（灭屏/锁屏）：
     *     • 若当前有活跃会话（App 在前台）→ 冻结计时（onAppGoBackground）+ 启动 3 分钟宽限计时
     *       宽限期内用户亮屏回到该 App → 恢复会话，不重新拦截
     *       宽限期超时 → 静默结束会话
     *     • 若会话已在后台（isInBackground=true）→ 直接结束会话（用户已离开过 App 再锁屏）
     *     • 无会话 → 不做任何处理
     *
     * - ACTION_USER_PRESENT（解锁回到桌面）：
     *     • 若在宽限期内（screenOffPackage != null）→ 取消锁屏超时，等待用户回到 App
     *       （用户回到 App 时由 handleForegroundChange 走 onAppReturnToForeground 恢复）
     *     • 若已超出宽限期（screenOffPackage == null）→ 无需处理，会话已结束
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val session = sessionManager.currentSession.value
                    if (session != null) {
                        cancelBackgroundTimeout()
                        cancelBackgroundDebounce()
                        if (session.isInBackground) {
                            // Case 3：息屏前 App 已在后台 → 直接结束会话
                            Log.d(TAG, "屏幕关闭，会话已在后台，直接结束 [${session.packageName}]")
                            serviceScope.launch {
                                sessionManager.endSession(UsageRecordEntity.EndReason.AUTO_TIMEOUT)
                            }
                            overlayManager.dismissAll()
                            screenOffPackage = null
                        } else {
                            // Case 1 / Case 2：息屏前 App 在前台 → 冻结计时，进入宽限期
                            Log.d(TAG, "屏幕关闭，App 在前台，冻结计时进入宽限期 [${session.packageName}]，宽限 ${SCREEN_OFF_GRACE_MINUTES} 分钟")
                            sessionManager.onAppGoBackground()
                            overlayManager.dismissAll()  // 关闭胶囊和所有浮窗
                            screenOffPackage = session.packageName
                            startScreenOffTimeout(session.packageName)
                        }
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    val pkg = screenOffPackage
                    if (pkg != null) {
                        // 宽限期内解锁 → 等待用户主动回到 App，计时已冻结，不需要额外操作
                        // handleForegroundChange 检测到 App 回到前台时会走 onAppReturnToForeground
                        Log.d(TAG, "屏幕解锁，仍在宽限期，等待用户回到 App [$pkg]")
                    }
                    // 若 screenOffPackage == null（宽限期已过），会话已结束，无需处理
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createDailyBriefChannel()
        createQuoteReminderChannel()
        createSessionEndChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // ── 保活：注册 WorkManager 守护任务（第一层保活兜底）───────────────────
        // 每 15 分钟检测一次服务是否存活，若已被杀则自动重启。
        // 使用 KEEP 策略，多次调用不会重复入队。
        ServiceWatchdogWorker.schedule(this)

        // 用户点击胶囊"结束"按钮后，根据当前所在 App 执行不同操作：
        //
        //  1. 用户在被监控 App 内结束 → 直接打开 Anchor App（引导回来，替代原来的按 Home 键）
        //  2. 用户在 Anchor App（本应用）内结束 → 发 LocalBroadcast，由 MainActivity 显示 Snackbar
        //  3. 用户在其他 App / 系统界面内结束 → 发通知轻提示，不打扰当前使用
        overlayManager.onManualEndSession = {
            val endedPackage = overlayManager.capsuleAppPackageName.value
            when {
                // 情景 1：在被监控 App 内结束，打开 Anchor App
                endedPackage.isNotEmpty() && lastForegroundPackage == endedPackage -> {
                    Log.d(TAG, "[ManualEnd] 在被监控App内结束，跳转到 Anchor App")
                    openMainActivity()
                }
                // 情景 2：在 Anchor App 内结束，发 LocalBroadcast 触发 Snackbar
                lastForegroundPackage == packageName -> {
                    Log.d(TAG, "[ManualEnd] 在 Anchor App 内结束，发 LocalBroadcast")
                    LocalBroadcastManager.getInstance(this@MonitorForegroundService)
                        .sendBroadcast(Intent(ACTION_SESSION_ENDED_IN_APP))
                }
                // 情景 3：在其他 App 或系统界面内结束，发通知轻提示
                else -> {
                    Log.d(TAG, "[ManualEnd] 在其他App内结束，发通知提示")
                    sendSessionEndNotification(endedPackage)
                }
            }
        }

        // 有目的使用手动结束后，打开 App 引导用户记录感受
        overlayManager.onManualEndWithPurpose = { recordId ->
            openMainActivityForNote(recordId)
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

        // 每日简报独立运行，不依赖监控列表是否为空
        startDailyBriefJob()
        // 格言推送独立运行
        startQuoteReminderJob()

        // 常驻通知刷新：每分钟更新一次今日使用汇总
        startNotificationRefreshJob()

        // 注册锁屏/亮屏广播
        // ACTION_SCREEN_OFF / ACTION_USER_PRESENT 只能动态注册，无法在 AndroidManifest 声明
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
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
            summaryLine = "今日已使用 $totalText"
            bigText = buildString {
                append("今日已使用 $totalText\n")
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

    /**
     * 启动每日简报专用协程，每 30 秒检测一次是否到达用户设定的推送时间。
     * 与主监控循环（1 秒轮询）完全隔离，互不影响。
     */
    private fun startDailyBriefJob() {
        dailyBriefJob?.cancel()
        dailyBriefJob = serviceScope.launch {
            Log.d(TAG, "[DailyBrief] 专用协程已启动")
            while (true) {
                try {
                    checkAndSendDailyBrief()
                } catch (e: Exception) {
                    Log.e(TAG, "[DailyBrief] 检测出错", e)
                }
                delay(DAILY_BRIEF_POLL_INTERVAL_MS)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  格言推送
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 启动格言推送专用协程，每 60 秒检测一次。
     * 触发条件：
     *   1. 格言推送开关开启
     *   2. 当前时间在用户设定的活跃时段内（startHour ~ 22:00）
     *   3. 距离上次推送已超过用户设定的间隔时长
     */
    private fun startQuoteReminderJob() {
        quoteReminderJob?.cancel()
        quoteReminderJob = serviceScope.launch {
            Log.d(TAG, "[QuoteReminder] 专用协程已启动")
            while (true) {
                try {
                    checkAndSendQuoteReminder()
                } catch (e: Exception) {
                    Log.e(TAG, "[QuoteReminder] 检测出错", e)
                }
                delay(QUOTE_REMINDER_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkAndSendQuoteReminder() {
        if (!appPreferences.isQuoteReminderEnabled()) return

        val cal = Calendar.getInstance()
        val curHour = cal.get(Calendar.HOUR_OF_DAY)
        val startHour = appPreferences.getQuoteReminderStartHour()
        // 不在活跃时段内，跳过
        if (curHour < startHour || curHour >= QUOTE_REMINDER_END_HOUR) return

        val intervalMs = appPreferences.getQuoteReminderIntervalHours() * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        // 还没到下次推送时间，跳过
        if (now - lastQuoteReminderTimeMs < intervalMs) return

        // 从收藏中随机取一条
        val favorites = quoteRepository.getAllFavorites().first()
        if (favorites.size < QUOTE_REMINDER_MIN_FAVORITES) return

        val quote = favorites.random()
        lastQuoteReminderTimeMs = now
        Log.d(TAG, "[QuoteReminder] 触发推送：${quote.content.take(20)}...")
        showQuoteReminderNotification(quote.content, quote.author)
    }

    private fun showQuoteReminderNotification(content: String, author: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            QUOTE_REMINDER_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val displayText = if (author.isBlank()) content else "$content\n$author"
        val notification = NotificationCompat.Builder(this, QUOTE_REMINDER_CHANNEL_ID)
            .setContentTitle("💬 来自你的收藏")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(QUOTE_REMINDER_NOTIFICATION_ID, notification)
    }

    // ════════════════════════════════════════════════════════════════════════

    /**
     * 检查当前时间是否应发送每日简报。
     *
     * 触发条件：
     *   1. 用户已开启每日简报开关
     *   2. 当前小时和分钟与用户设定的推送时间匹配
     *   3. 今日还未发送过简报（以日期字符串判断）
     */
    private fun checkAndSendDailyBrief() {
        val briefEnabled = appPreferences.isDailyBriefEnabled()
        val cal = Calendar.getInstance()
        val curHour   = cal.get(Calendar.HOUR_OF_DAY)
        val curMinute = cal.get(Calendar.MINUTE)
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)

        if (!briefEnabled) return

        val targetHour   = appPreferences.getDailyBriefHour()
        val targetMinute = appPreferences.getDailyBriefMinute()

        // 小时和分钟均匹配才触发
        if (curHour != targetHour || curMinute != targetMinute) return
        // 今天已发送过，跳过
        if (todayDate == lastBriefDate) {
            Log.d(TAG, "[DailyBrief] 今天已发送过简报，跳过")
            return
        }

        lastBriefDate = todayDate
        Log.d(TAG, "[DailyBrief] 触发！${targetHour}:${targetMinute.toString().padStart(2,'0')}，准备发送今日简报")

        serviceScope.launch {
            sendDailyBriefNotification()
        }
    }

    /**
     * 汇总当日使用记录，生成大白话形式的简报文案，并发送通知。
     */
    private suspend fun sendDailyBriefNotification() {
        try {
            val now = System.currentTimeMillis()
            val (dayStart, dayEnd) = UsageRecordRepository.getDayRange(now)
            Log.d(TAG, "[DailyBrief] 查询今日使用记录，dayStart=$dayStart, dayEnd=$dayEnd")

            val usageList = usageRecordRepository.getAppTotalByPeriod(dayStart, dayEnd)
            // 克制次数：在拦截页选择"还是算了"退出的次数
            val restrainCount = usageRecordRepository.getDayDismissCount(now)

            Log.d(TAG, "[DailyBrief] 查询结果：${usageList.size} 个 App，克制次数=$restrainCount")

            val totalSeconds = usageList.sumOf { it.totalSeconds }
            val (title, content) = buildDailyBriefText(usageList, totalSeconds, restrainCount)

            showDailyBriefNotification(title, content)
            Log.d(TAG, "[DailyBrief] 通知已发送！")
        } catch (e: Exception) {
            Log.e(TAG, "[DailyBrief] 发送通知出错", e)
        }
    }

    /**
     * 生成每日简报的通知标题和正文。
     * 风格：大白话、轻松、三言两语。
     */
    private fun buildDailyBriefText(
        usageList: List<com.life.mindfulnessapp.data.db.dao.AppTotalUsage>,
        totalSeconds: Long,
        restrainCount: Int
    ): Pair<String, String> {
        // 完全零使用
        if (usageList.isEmpty() && restrainCount == 0) {
            return "🌿 今日简报" to "今天没有触碰任何受监控的 App，天天当个自律人就这么容易！"
        }

        val sorted = usageList.sortedByDescending { it.totalSeconds }
        val totalText = formatDuration(totalSeconds)
        val title = "📊 今日小结"

        val content = buildString {
            // 总时长
            if (totalSeconds > 0) {
                append("今天手机消耗了 $totalText")
            } else {
                append("今天和手机的缘分不多")
            }

            // 最常用 App
            if (sorted.isNotEmpty()) {
                val top = sorted.first()
                val topName = getAppName(top.packageName)
                val topTime = formatDuration(top.totalSeconds)
                append("，最吃时间的是 $topName（$topTime）")
                if (sorted.size >= 2) {
                    val second = sorted[1]
                    val secName = getAppName(second.packageName)
                    val secTime = formatDuration(second.totalSeconds)
                    append("，其次是 $secName（$secTime）")
                }
                append("。")
            } else {
                append("。")
            }

            // 克制情况
            when {
                restrainCount >= 5 -> append("克制了 $restrainCount 次想打开却强行收手，今天赢得漂亮！🎉")
                restrainCount >= 2 -> append("中途克制了 $restrainCount 次冲动，安全感拉满！")
                restrainCount == 1 -> append("还克制了 1 次小冲动，慢慢来。")
                totalSeconds in 1..1800 -> append("今天用得超少，少就是多！🌱")
                totalSeconds > 7200 -> append("不过记得适时休息一下眼睛，明天再装！")
                else -> append("总体还行，继续加油👊")
            }
        }

        return title to content
    }

    /**
     * 获取 App 名称，若获取失败则返回包名最后一段作为兜底。
     */
    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // 兜底：取包名最后一段
            packageName.substringAfterLast(".")
        }
    }

    /**
     * 将秒数格式化为人类可读的时长字符串。
     * 示例：90秒 → "1分30秒"，3600秒 → "1小时"，3690秒 → "1小时1分"
     */
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
     * 发布每日简报通知。
     */
    private fun showDailyBriefNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            DAILY_BRIEF_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, DAILY_BRIEF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(DAILY_BRIEF_NOTIFICATION_ID, notification)
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
        val prevPkg = lastForegroundPackage

        if (currentPkg == prevPkg) {
            // 拦截页/超限页正在展示期间，跳过超限检查，避免重复触发
            if (overlayManager.isInterceptVisible.get()) return
            val session = sessionManager.currentSession.value
            if (session != null && !session.isInBackground) {
                if (session.isDailyLimitExceeded || session.isWeeklyLimitExceeded) {
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
                overlayManager.isInterceptVisible.get() -> {
                    Log.d(TAG, "$currentPkg 拦截弹窗正在展示中，跳过本轮")
                }
                existingSession?.packageName == currentPkg && existingSession.isInBackground -> {
                    val isFromScreenOff = screenOffPackage == currentPkg
                    sessionManager.onAppReturnToForeground()
                    cancelBackgroundTimeout()
                    cancelScreenOffTimeout()
                    screenOffPackage = null
                    overlayManager.resumeCapsule()  // 用户已回来，恢复胶囊到活跃状态
                    // 必须读取 onAppReturnToForeground() 更新后的最新 session
                    // （isInBackground=false，startTime 已重置为当前时刻）
                    val restoredSession = sessionManager.currentSession.value ?: existingSession
                    overlayManager.showCapsule(restoredSession, playEnterAnimation = false)
                    if (isFromScreenOff) {
                        Log.d(TAG, "$currentPkg 锁屏后回来（宽限期内），恢复计时，accumulated=${restoredSession.accumulatedActiveSeconds}s")
                    } else {
                        Log.d(TAG, "$currentPkg 从后台回来，继续计时，accumulated=${restoredSession.accumulatedActiveSeconds}s")
                    }
                }
                existingSession?.packageName == currentPkg && !existingSession.isInBackground -> {
                    Log.d(TAG, "$currentPkg 已在前台运行中，跳过")
                }
                else -> {
                    Log.d(TAG, "显示拦截浮窗: $currentPkg，existingSession=$existingSession")
                    showInterceptOverlay(currentPkg)
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
            Log.d(TAG, "$interceptTarget 拦截页展示期间用户按 Home 离开，立即关闭拦截页")
            overlayManager.dismissIntercept()
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
                        // ── 检查 App 进程是否还存活 ────────────────────────────────
                        // 若进程已被系统/用户杀掉，立即结束会话并关闭胶囊，
                        // 不进入"暂停等待"流程（用户无法再"回到"一个已死亡的 App）
                        if (!isAppProcessAlive(debouncePackage)) {
                            Log.d(TAG, "$debouncePackage 进程已消亡（被杀），立即结束会话并关闭胶囊")
                            sessionManager.endSession(UsageRecordEntity.EndReason.APP_CLOSED)
                            cancelBackgroundTimeout()
                            overlayManager.dismissAll()
                            return@launch
                        }

                        Log.d(TAG, "$debouncePackage 确认进入后台（进程存活），触发后台逻辑")

                        sessionManager.onAppGoBackground()

                        // App 进入后台，胶囊统一切换为暂停状态
                        Log.d(TAG, "$debouncePackage 进入后台，胶囊切换为暂停状态")
                        overlayManager.pauseCapsule(returnToAppAction = {
                            // 点击暂停胶囊时：直接拉起该App回到前台
                            launchApp(debouncePackage)
                        })
                        startBackgroundTimeout(debouncePackage)
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
     * 检查指定包名的 App 进程是否仍然存活。
     *
     * 使用 ActivityManager.getRunningAppProcesses() 枚举所有运行中的进程，
     * 如果找到该包名对应的进程条目，说明进程仍然存在（App 只是切到后台）。
     * 若找不到，说明进程已被用户/系统强制终止（Force Stop 或滑掉最近任务）。
     *
     * ⚠️ 注意：从 Android 11（API 30）起，此 API 只能看到本应用自己的进程，
     * 但对于"被监控的第三方 App 是否还活着"这个场景依然有效——
     * 即便返回列表不完整，若列表里没有该包名，说明其进程已被杀掉。
     * 这与我们"宁可误判、不可遗漏"的策略一致：宁可提前结束会话，也不让胶囊悬空。
     */
    private fun isAppProcessAlive(packageName: String): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = try {
            am.runningAppProcesses ?: emptyList()
        } catch (_: Exception) {
            return true  // 获取失败时保守处理，认为进程存活，不立即结束
        }
        return processes.any { proc ->
            proc.pkgList?.contains(packageName) == true
        }
    }

    private suspend fun showInterceptOverlay(packageName: String) {
        val existing = sessionManager.currentSession.value
        if (existing != null && existing.packageName != packageName) {
            Log.w(TAG, "showInterceptOverlay 发现残留会话 [${existing.packageName}]，强制结束并清理关联资源")
            sessionManager.endSession(UsageRecordEntity.EndReason.APP_CLOSED)
            // 同步取消该残留会话对应的后台超时计时，避免孤悬协程在 3 分钟后误触发
            cancelBackgroundTimeout()
            // 移除旧 App 的胶囊（如果还在屏幕上）
            overlayManager.dismissCapsule()
        }

        val limit = appLimitRepository.getAppLimit(packageName) ?: return

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
            dailyLimitMinutes = limit.dailyLimitMinutes,
            weeklyLimitMinutes = limit.weeklyLimitMinutes,
            onContinue = { purpose ->
                // 若用户填写了目的，自动复制到剪贴板，方便在 App 搜索框直接粘贴
                if (!purpose.isNullOrBlank()) {
                    copyPurposeToClipboard(purpose)
                }
                // onContinue 从主线程（Compose onClick）触发
                serviceScope.launch {
                    val session = sessionManager.startSession(packageName, appName, purpose)
                    if (session != null) {
                        // 检查：用户在拦截页期间是否已按 Home 键离开（App 不在前台）
                        // 若 lastForegroundPackage 已不是被监控的 App，说明用户已经回到桌面，
                        // 此时不应显示胶囊（或立刻进入后台模式），避免胶囊一闪即灭
                        if (lastForegroundPackage == packageName) {
                            overlayManager.showCapsule(session)
                            Log.d(TAG, "会话已创建（purpose=$purpose），胶囊已请求显示：$packageName")
                        } else {
                            // 用户已在拦截页按 Home 离开：将会话立即标记为后台状态，
                            // 启动后台超时计时，显示暂停提示气泡
                            Log.d(TAG, "用户在拦截页按 Home 离开后确认意图，App 不在前台，直接进入后台等待：$packageName")
                            overlayManager.isInterceptVisible.set(false)
                            sessionManager.onAppGoBackground()
                            // 显示胶囊并立即切换为暂停状态
                            val bgSession = sessionManager.currentSession.value
                            if (bgSession != null) {
                                overlayManager.showCapsule(bgSession, playEnterAnimation = false)
                            }
                            overlayManager.pauseCapsule(returnToAppAction = {
                                launchApp(packageName)
                            })
                            startBackgroundTimeout(packageName)
                        }
                    } else {
                        overlayManager.isInterceptVisible.set(false)
                        Log.w(TAG, "startSession 返回 null，跳过胶囊显示")
                    }
                }
            },
            onDismiss = {
                // 用户在拦截页选择「还是算了」退出：写入一条极短的拦截退出记录，
                // 供首页时间轴展示「克制住了」条目
                serviceScope.launch {
                    val now = System.currentTimeMillis()
                    val recordId = usageRecordRepository.insertRecord(
                        UsageRecordEntity(
                            packageName = packageName,
                            startTime = now,
                            endTime = now,
                            durationSeconds = 0L,
                            endReason = UsageRecordEntity.EndReason.APP_CLOSED,
                            purpose = null
                        )
                    )
                    android.util.Log.d(TAG, "拦截退出记录已写入 [id=$recordId, pkg=$packageName]")
                }
                pressHomeButton()
            },
            onReset = {
                handleResetLimit(packageName)
            }
        )
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
                    onReset = {
                        handleResetLimit(packageName)
                    },
                    onContinueOverLimit = {
                        // 用户明确点击「我知道超了，继续使用」：
                        // 开启超限续记 session，后续时长照常记录，不再弹超限页。
                        serviceScope.launch {
                            val overSession = sessionManager.startOverLimitSession(packageName, appName)
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
     * 3. 发 Intent 打开 MainActivity，导航到 AppList 页并自动弹出该 App 的编辑对话框
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

    private fun startBackgroundTimeout(packageName: String) {
        cancelBackgroundTimeout()
        backgroundTimeoutJob = serviceScope.launch {
            // ── 阶段 1：等待 2 分钟（未锁屏后台超时阈值）────────────────────────────
            // 注意：进入后台时 SessionManager.onAppGoBackground() 已将 isInBackground = true，
            // currentSessionSeconds 从此刻起冻结，后台等待的时间不会计入使用时长。
            Log.d(TAG, "$packageName 进入后台，启动 ${BACKGROUND_TIMEOUT_MINUTES} 分钟未锁屏超时计时")
            delay(BACKGROUND_TIMEOUT_MS)

            val session = sessionManager.currentSession.value
            if (session == null || session.packageName != packageName || !session.isInBackground) return@launch

            // ── 阶段 2：弹出确认弹窗 ────────────────────────────────────────────────
            Log.d(TAG, "$packageName 后台超过 ${BACKGROUND_TIMEOUT_MINUTES} 分钟，弹出结束确认弹窗")
            val confirmed = overlayManager.triggerBackgroundTimeoutConfirm()
            if (!confirmed) {
                // 胶囊已不存在（用户可能已手动关闭），静默结束会话
                Log.d(TAG, "$packageName 胶囊不存在，静默结束会话")
                sessionManager.endSession(UsageRecordEntity.EndReason.AUTO_TIMEOUT)
                overlayManager.dismissCapsule()
                return@launch
            }

            // ── 阶段 3：弹窗 1 分钟无操作倒计时 ────────────────────────────────────
            // 弹窗期间 isInBackground = true，计时已冻结，不会额外计入时长。
            // 1 分钟后如果用户仍未操作，强制结束会话。
            delay(CONFIRM_DIALOG_TIMEOUT_MS)
            val sessionAfterWait = sessionManager.currentSession.value
            if (sessionAfterWait != null && sessionAfterWait.packageName == packageName && sessionAfterWait.isInBackground) {
                Log.d(TAG, "$packageName 确认弹窗超过 1 分钟无操作，自动结束会话")
                sessionManager.endSession(UsageRecordEntity.EndReason.AUTO_TIMEOUT)
                overlayManager.dismissCapsule()
            }
        }
    }

    private fun cancelBackgroundTimeout() {
        backgroundTimeoutJob?.cancel()
        backgroundTimeoutJob = null
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
                sessionManager.endSession(UsageRecordEntity.EndReason.AUTO_TIMEOUT)
                overlayManager.dismissAll()
            }
            screenOffPackage = null
        }
    }

    private fun cancelScreenOffTimeout() {
        screenOffTimeoutJob?.cancel()
        screenOffTimeoutJob = null
    }

    /**
     * 拉起指定包名的App到前台。
     * 暂停状态下用户点击胶囊时调用，直接跳回被监控的App。
     *
     * @param packageName 目标App包名
     */
    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launchIntent)
                Log.d(TAG, "成功拉起App: $packageName")
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

    /**
     * 将用户输入的使用目的复制到系统剪贴板，并弹出 Toast 提示。
     * 用户进入目标 App 后可直接粘贴到搜索框，实现「带着意图进入」的完整体验。
     *
     * 注意：Toast 必须在主线程弹出，此方法已在主线程（Compose onClick 回调）中被调用，
     * 但为保险起见统一通过 mainHandler.post 确保安全。
     */
    private fun copyPurposeToClipboard(purpose: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("使用目的", purpose)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "[Clipboard] 已复制目的到剪贴板：$purpose")
            // Toast 必须在主线程，用 mainHandler 确保
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "「$purpose」已复制，可直接粘贴到搜索框 📋",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Clipboard] 复制失败", e)
        }
    }

    /**
     * 打开主 App 并传入 recordId，触发首页备注弹窗。
     * 延迟 400ms 等待桌面动画完成再打开，避免视觉突兀。
     */
    private fun openMainActivityForNote(recordId: Long) {
        serviceScope.launch {
            kotlinx.coroutines.delay(400)
            val intent = Intent(this@MonitorForegroundService, MainActivity::class.java).apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_RECORD_ID, recordId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    /**
     * 直接打开 Anchor App（不携带额外参数）。
     * 用于用户在被监控 App 内手动结束会话时，将用户引导回 Anchor App。
     * 延迟 200ms，让胶囊消失动画先完成。
     */
    private fun openMainActivity() {
        serviceScope.launch {
            kotlinx.coroutines.delay(200)
            val intent = Intent(this@MonitorForegroundService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    /**
     * 发送「会话已结束」轻量通知。
     * 用于用户在第三方 App 内手动结束会话时，通过通知栏给出静默确认。
     *
     * @param endedPackage 被结束会话的 App 包名，用于获取 App 名称
     */
    private fun sendSessionEndNotification(endedPackage: String) {
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setContentTitle("计时已结束 ✓")
            .setContentText("$appName 的使用记录已保存")
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
     * 创建每日简报通知渠道。
     * 与前台服务渠道分离，使用默认重要性（会弹出提示音），支持用户独立关闭。
     */
    private fun createDailyBriefChannel() {
        val channel = NotificationChannel(
            DAILY_BRIEF_CHANNEL_ID,
            "每日简报",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每天在指定时间推送今日 App 使用情况简报"
            setShowBadge(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * 创建格言推送通知渠道。
     */
    private fun createQuoteReminderChannel() {
        val channel = NotificationChannel(
            QUOTE_REMINDER_CHANNEL_ID,
            "格言推送",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "定时推送你收藏的格言，在想刷手机前给你一点提醒"
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
            description = "在其他应用内结束计时时，发出静默确认通知"
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
