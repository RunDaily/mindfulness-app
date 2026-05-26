package com.life.mindfulnessapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        const val POLL_INTERVAL_MS = 1000L
        const val BACKGROUND_TIMEOUT_MS = 3 * 60 * 1000L  // 3 分钟
        const val BACKGROUND_TIMEOUT_MINUTES = 3           // 与上方保持一致，用于提示文案
        /**
         * 后台切换防抖延迟（毫秒）。
         * 用于过滤通知栏下拉、系统弹框等导致的短暂"离开前台"误判。
         * 设置为 1500ms：通知栏操作通常 < 1s，真正切到桌面/其他 App 则持续较长时间。
         */
        const val BACKGROUND_DEBOUNCE_MS = 1500L

        // ── 每小时提醒通知相关常量 ───────────────────────────────────────────
        /** 每小时使用提醒通知渠道 ID */
        const val HOURLY_REMINDER_CHANNEL_ID = "hourly_usage_reminder"
        /** 每小时提醒通知 ID（使用 2000 + 小时数，避免覆盖前台服务通知） */
        const val HOURLY_REMINDER_NOTIFICATION_BASE_ID = 2000
        /** 提醒时间范围：10:00 ～ 22:00（含） */
        const val HOURLY_REMINDER_START_HOUR = 10
        const val HOURLY_REMINDER_END_HOUR = 22
        /** 每小时提醒专用协程的检测间隔：60 秒 */
        const val HOURLY_REMINDER_POLL_INTERVAL_MS = 60_000L

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var backgroundTimeoutJob: Job? = null
    /** 防抖：检测到被监控 App 离开前台后，延迟确认是否真正进入后台的协程 */
    private var backgroundDebounceJob: Job? = null
    /** 每小时提醒专用协程（独立于主监控循环，60 秒检测一次） */
    private var hourlyReminderJob: Job? = null
    /** 每小时提醒：记录上次发送通知的小时数，避免同一小时重复发送 */
    private var lastReminderHour: Int = -1

    private var enabledPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createHourlyReminderChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 用户点击胶囊"结束"按钮时，按 Home 键退回桌面
        overlayManager.onManualEndSession = { pressHomeButton() }

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

        // 每小时提醒独立运行，不依赖监控列表是否为空
        startHourlyReminderJob()
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
     * 启动每小时提醒专用协程，每 60 秒检测一次是否需要发送通知。
     * 与主监控循环（1 秒轮询）完全隔离，互不影响。
     */
    private fun startHourlyReminderJob() {
        hourlyReminderJob?.cancel()
        hourlyReminderJob = serviceScope.launch {
            Log.d(TAG, "[HourlyReminder] 专用协程已启动")
            while (true) {
                try {
                    checkAndSendHourlyReminder()
                } catch (e: Exception) {
                    Log.e(TAG, "[HourlyReminder] 检测出错", e)
                }
                delay(HOURLY_REMINDER_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 检查当前时间是否应发送每小时使用提醒通知。
     *
     * 触发条件：
     *   1. 用户已开启每小时提醒开关
     *   2. 当前时间在 10:00 ～ 22:00（含）之间
     *   3. 当前小时与上次发送通知的小时不同（避免同一小时内重复发送）
     *
     * ⚠️ 不使用 minute == 0 的精确匹配：
     *   由于协程 delay(1000) 并不精确，加上 getForegroundPackage 本身有耗时，
     *   实际执行间隔可能偏移，极易跳过整点那 1 秒。
     *   改用"已进入新的小时 && 本小时尚未发过"逻辑，只要进入该小时任意时刻即可触发，
     *   最终效果等同于整点提醒（误差在秒级）。
     */
    private fun checkAndSendHourlyReminder() {
        val reminderEnabled = appPreferences.isHourlyReminderEnabled()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        Log.d(TAG, "[HourlyReminder] 检测: enabled=$reminderEnabled, 当前=${hour}:${minute.toString().padStart(2,'0')}, lastHour=$lastReminderHour")

        if (!reminderEnabled) {
            Log.d(TAG, "[HourlyReminder] 提醒开关已关闭，跳过")
            return
        }

        // 只在 10:00 ～ 22:00 触发
        if (hour < HOURLY_REMINDER_START_HOUR || hour > HOURLY_REMINDER_END_HOUR) {
            Log.d(TAG, "[HourlyReminder] 当前 $hour 时不在提醒范围（$HOURLY_REMINDER_START_HOUR～$HOURLY_REMINDER_END_HOUR），跳过")
            return
        }
        // 本小时已发送过，跳过
        if (hour == lastReminderHour) {
            Log.d(TAG, "[HourlyReminder] $hour 时已发送过，跳过")
            return
        }

        lastReminderHour = hour
        Log.d(TAG, "[HourlyReminder] 触发！当前 ${hour}:${minute.toString().padStart(2,'0')}，准备发送通知")

        serviceScope.launch {
            sendHourlyReminderNotification(hour)
        }
    }

    /**
     * 汇总当日所有受监控 App 的使用时长，并发送通知。
     *
     * @param hour 当前小时，用于计算通知 ID（避免与前台服务通知冲突）
     */
    private suspend fun sendHourlyReminderNotification(hour: Int) {
        try {
            val now = System.currentTimeMillis()
            val (dayStart, dayEnd) = UsageRecordRepository.getDayRange(now)
            Log.d(TAG, "[HourlyReminder] 查询今日使用记录，dayStart=$dayStart, dayEnd=$dayEnd")

            // 获取今日所有 App 的使用时长汇总
            val usageList = usageRecordRepository.getAppTotalByPeriod(dayStart, dayEnd)
            Log.d(TAG, "[HourlyReminder] 查询结果：${usageList.size} 个 App，列表=$usageList")

            // 计算今日总使用时长（秒），无记录时显示 0
            val totalSeconds = usageList.sumOf { it.totalSeconds }
            val totalText = if (usageList.isEmpty()) "暂无记录" else formatDuration(totalSeconds)

            // 构建通知文案
            val title = "📱 今日使用提醒"
            val content = if (usageList.isEmpty()) {
                "今日暂无使用记录"
            } else {
                buildReminderContent(usageList, totalSeconds)
            }

            val notificationId = HOURLY_REMINDER_NOTIFICATION_BASE_ID + hour
            Log.d(TAG, "[HourlyReminder] 准备发送通知 id=$notificationId, content=$content")
            showReminderNotification(notificationId, title, content, totalText)
            Log.d(TAG, "[HourlyReminder] 通知已发送！总计 $totalText，共 ${usageList.size} 个 App")
        } catch (e: Exception) {
            Log.e(TAG, "[HourlyReminder] 发送通知出错", e)
        }
    }

    /**
     * 构建通知正文：展示今日总时长，以及时长最长的前 3 个 App。
     */
    private fun buildReminderContent(
        usageList: List<com.life.mindfulnessapp.data.db.dao.AppTotalUsage>,
        totalSeconds: Long
    ): String {
        val totalText = formatDuration(totalSeconds)
        val sorted = usageList.sortedByDescending { it.totalSeconds }

        return buildString {
            append("今日已使用 $totalText")
            if (sorted.isNotEmpty()) {
                append("\n")
                val topApps = sorted.take(3)
                topApps.forEachIndexed { index, app ->
                    val appName = getAppName(app.packageName)
                    val appTime = formatDuration(app.totalSeconds)
                    append("${index + 1}. $appName $appTime")
                    if (index < topApps.size - 1) append("\n")
                }
            }
        }
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
     * 发布每小时提醒通知。
     */
    private fun showReminderNotification(
        notificationId: Int,
        title: String,
        content: String,
        totalText: String
    ) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, HOURLY_REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("今日已使用 $totalText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
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
            val session = sessionManager.currentSession.value
            if (session != null && !session.isInBackground) {
                if (session.isDailyLimitExceeded || session.isWeeklyLimitExceeded) {
                    handleLimitExceeded(session.packageName)
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
                    sessionManager.onAppReturnToForeground()
                    cancelBackgroundTimeout()
                    overlayManager.resumeCapsule()  // 用户已回来，恢复胶囊到活跃状态
                    // 必须读取 onAppReturnToForeground() 更新后的最新 session
                    // （isInBackground=false，startTime 已重置为当前时刻）
                    val restoredSession = sessionManager.currentSession.value ?: existingSession
                    overlayManager.showCapsule(restoredSession, playEnterAnimation = false)
                    Log.d(TAG, "$currentPkg 从后台回来，继续计时，accumulated=${restoredSession.accumulatedActiveSeconds}s")
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

        // 特殊情况：拦截页正在展示时，用户按 Home 键离开（此时 session 为 null，无活跃会话）。
        // 被监控 App 已经不在前台，拦截覆盖层却还留着覆盖在桌面上，需要立即关闭它。
        if (prevPkg != null && enabledPackages.contains(prevPkg) && overlayManager.isInterceptVisible.get()) {
            Log.d(TAG, "$prevPkg 拦截页展示期间用户按 Home 离开，立即关闭拦截页")
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
                        Log.d(TAG, "$debouncePackage 确认进入后台，触发后台逻辑")

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
                pressHomeButton()
            },
            onReset = { newDailyMinutes, newWeeklyMinutes ->
                handleResetLimit(packageName, newDailyMinutes, newWeeklyMinutes)
            }
        )
    }

    private fun handleLimitExceeded(packageName: String) {
        serviceScope.launch {
            sessionManager.endSession(UsageRecordEntity.EndReason.LIMIT_REACHED)
            overlayManager.dismissCapsule()
            overlayManager.showLimitReached(
                packageName = packageName,
                onDismiss = {
                    // 只有当被监控的 App 仍在前台时，才需要按 Home 键把用户"推出去"。
                    // 如果用户已经切换到其他 App，直接关闭弹框即可，不应强制跳回桌面。
                    if (lastForegroundPackage == packageName) {
                        pressHomeButton()
                    }
                },
                onReset = { newDailyMinutes, newWeeklyMinutes ->
                    handleResetLimit(packageName, newDailyMinutes, newWeeklyMinutes)
                }
            )
        }
    }

    /**
     * 用户在超限界面选择重新设定目标后的处理：
     * 1. 持久化新限制（消耗今日一次修改机会）
     * 2. 以新限制重新开启会话
     * 3. 显示胶囊，让用户继续使用剩余时间
     */
    private fun handleResetLimit(packageName: String, newDailyMinutes: Int, newWeeklyMinutes: Int) {
        serviceScope.launch {
            val oldLimit = appLimitRepository.getAppLimit(packageName)
            val success = appLimitRepository.resetAppLimit(packageName, newDailyMinutes, newWeeklyMinutes)
            if (!success) {
                Log.w(TAG, "resetAppLimit 失败：今日次数已用完 [$packageName]")
                pressHomeButton()
                return@launch
            }
            Log.d(TAG, "[$packageName] 限制已更新为每日 ${newDailyMinutes} 分钟，重新开始会话")

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) {
                appLimitRepository.getAppLimit(packageName)?.appName ?: packageName
            }

            // 写入「重新设定限额」事件记录，供首页时间轴特殊标注
            limitResetRepository.insert(
                LimitResetEntity(
                    packageName = packageName,
                    appName = appName,
                    resetTime = System.currentTimeMillis(),
                    oldDailyLimitMinutes = oldLimit?.dailyLimitMinutes ?: newDailyMinutes,
                    newDailyLimitMinutes = newDailyMinutes,
                    oldWeeklyLimitMinutes = oldLimit?.weeklyLimitMinutes ?: 0,
                    newWeeklyLimitMinutes = newWeeklyMinutes
                )
            )

            // 重新开启会话：SessionManager 读最新 limit，基于新上限计算剩余时间
            val session = sessionManager.startSession(packageName, appName)
            if (session != null) {
                overlayManager.showCapsule(session)
                Log.d(TAG, "[$packageName] 重新设定成功，胶囊已显示，剩余 ${session.dailyRemainingSeconds} 秒")
            } else {
                Log.w(TAG, "[$packageName] 重新设定后 startSession 返回 null")
                pressHomeButton()
            }
        }
    }

    private fun startBackgroundTimeout(packageName: String) {
        cancelBackgroundTimeout()
        backgroundTimeoutJob = serviceScope.launch {
            Log.d(TAG, "$packageName 进入后台，启动${BACKGROUND_TIMEOUT_MINUTES}分钟计时")
            delay(BACKGROUND_TIMEOUT_MS)
            val session = sessionManager.currentSession.value
            if (session != null && session.packageName == packageName && session.isInBackground) {
                Log.d(TAG, "$packageName 后台超过${BACKGROUND_TIMEOUT_MINUTES}分钟，弹出结束确认弹窗")
                val confirmed = overlayManager.triggerBackgroundTimeoutConfirm()
                if (!confirmed) {
                    // 胶囊已不存在（用户可能已手动关闭），静默结束会话
                    Log.d(TAG, "$packageName 胶囊不存在，静默结束会话")
                    sessionManager.endSession(UsageRecordEntity.EndReason.AUTO_TIMEOUT)
                    overlayManager.dismissCapsule()
                }
                // 若弹窗已弹出，结束会话的操作将由用户在弹窗中确认后触发
            }
        }
    }

    private fun cancelBackgroundTimeout() {
        backgroundTimeoutJob?.cancel()
        backgroundTimeoutJob = null
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
     * 创建每小时使用提醒的通知渠道。
     * 与前台服务渠道分离，使用默认重要性（会弹出提示音），支持用户独立关闭。
     */
    private fun createHourlyReminderChannel() {
        val channel = NotificationChannel(
            HOURLY_REMINDER_CHANNEL_ID,
            "每小时使用提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每小时整点推送当日 App 使用时长汇总（10:00～22:00）"
            setShowBadge(true)
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
