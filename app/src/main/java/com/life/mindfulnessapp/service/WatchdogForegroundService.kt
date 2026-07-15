package com.life.mindfulnessapp.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 守护前台服务（加强保活）：独立于 MonitorForegroundService 运行的第二个前台服务。
 *
 * 核心思路：
 *   两个前台服务运行在同一进程中。系统杀死进程时两者同时消失，但只要系统
 *   给任意一个服务发送重启信号（START_STICKY），另一个也会被带起来。
 *   更重要的是：两个独立的前台通知让系统对本进程的"用户感知"评估更高，
 *   使得在 OOM Killer 的优先级队列中更靠前，被杀概率更低。
 *
 *   此服务还承担「主动守护」职责：
 *   每 [WATCHDOG_INTERVAL_MS]（5 秒）检查一次 MonitorForegroundService 是否运行，
 *   若已停止则立即重启。这应对了主服务在某些 ROM 上 START_STICKY 不生效的情况。
 *
 * 与 ServiceWatchdogWorker 的区别：
 *   - WatchdogForegroundService：实时运行，5 秒内响应，优先级高，需用户主动开启
 *   - ServiceWatchdogWorker：WorkManager 调度，15 分钟周期，无需额外权限，始终启用
 *
 * 用户体验：
 *   通知以极低优先级（IMPORTANCE_MIN）展示，在通知栏折叠区显示，不打扰用户。
 */
@AndroidEntryPoint
class WatchdogForegroundService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val CHANNEL_ID = "watchdog_channel"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "ACTION_WATCHDOG_STOP"

        /** 检测 MonitorForegroundService 存活状态的轮询间隔 */
        private const val WATCHDOG_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, WatchdogForegroundService::class.java)
            context.startForegroundService(intent)
            Log.d(TAG, "WatchdogForegroundService 启动指令已发送")
        }

        fun stop(context: Context) {
            val intent = Intent(context, WatchdogForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            Log.d(TAG, "WatchdogForegroundService 停止指令已发送")
        }

        @Suppress("DEPRECATION")
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Int.MAX_VALUE)
                ?.any { it.service.className == WatchdogForegroundService::class.java.name } == true
        }
    }

    @Inject
    lateinit var appPreferences: AppPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "WatchdogForegroundService 已创建并进入前台")
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "收到停止指令，自行退出")
            stopSelf()
            return START_NOT_STICKY
        }
        // 确保守护协程在运行
        if (watchdogJob?.isActive != true) {
            startWatchdog()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "WatchdogForegroundService 已销毁")
        // 若加强保活仍然开启，在销毁时立即请求重启自己
        // （对抗某些 ROM 主动 kill 但不触发 START_STICKY 的情况）
        if (appPreferences.isEnhancedKeepAliveEnabled()) {
            Log.w(TAG, "加强保活开启中，服务被销毁，尝试立即自重启")
            start(applicationContext)
        }
    }

    /**
     * 启动守护协程：每 [WATCHDOG_INTERVAL_MS] 秒检测 MonitorForegroundService 是否活着。
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            Log.d(TAG, "守护协程启动，检测间隔 ${WATCHDOG_INTERVAL_MS}ms")
            while (true) {
                try {
                    checkAndRestoreMonitorService()
                } catch (e: Exception) {
                    Log.e(TAG, "守护检测出错", e)
                }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    /**
     * 检测 MonitorForegroundService 是否运行，若已停止则立即重启。
     */
    @Suppress("DEPRECATION")
    private fun checkAndRestoreMonitorService() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val monitorServiceName = MonitorForegroundService::class.java.name
        val isMonitorRunning = manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == monitorServiceName } == true

        if (!isMonitorRunning) {
            Log.w(TAG, "⚠️ 检测到 MonitorForegroundService 未运行，立即重启！")
            MonitorForegroundService.start(applicationContext)
        }
    }

    // ── 通知相关 ─────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用守护服务",
            NotificationManager.IMPORTANCE_MIN  // 最低优先级：不发声、不弹出、折叠显示
        ).apply {
            description = "加强保活：确保时间守护服务持续运行"
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
            .setContentTitle("守护服务运行中")
            .setContentText("正在确保时间守护的稳定性")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // 折叠显示，不打扰
            .build()
    }
}
