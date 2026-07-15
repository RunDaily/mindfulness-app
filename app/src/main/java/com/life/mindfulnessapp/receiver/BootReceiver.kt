package com.life.mindfulnessapp.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.service.ServiceWatchdogWorker
import com.life.mindfulnessapp.service.WatchdogForegroundService

/**
 * 保活广播接收器：监听系统事件，在服务被杀时快速重启，构成三层保活的第二层（事件驱动）。
 *
 * 监听的广播：
 *   - ACTION_BOOT_COMPLETED      开机完成后启动服务（主要保活路径）
 *   - ACTION_MY_PACKAGE_REPLACED 自身 App 更新后重启服务
 *
 * 注意：ACTION_SCREEN_ON / ACTION_USER_PRESENT 无法在 Manifest 中静态注册，
 * 必须在 MonitorForegroundService.onCreate() 中动态注册（已有实现）。
 *
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED 是静态注册的，由系统直接唤起此 Receiver。
 * 在开机/更新后，服务还未运行，此时需要重新调度 WorkManager 任务作为兜底。
 * 若用户开启了「加强保活」，同时拉起 WatchdogForegroundService。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        /** 与 AppPreferences 保持一致的 SharedPreferences key */
        private const val PREFS_NAME = "mindfulness_prefs"
        private const val KEY_ENHANCED_KEEP_ALIVE = "enhanced_keep_alive"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "收到 ${intent.action}，启动监控服务并调度 Watchdog")
                startMonitorServiceIfNeeded(context)
                // 若「加强保活」已开启，同步启动守护前台服务
                if (isEnhancedKeepAliveEnabled(context)) {
                    startWatchdogServiceIfNeeded(context)
                    Log.d(TAG, "加强保活已开启，同步启动 WatchdogForegroundService")
                }
                // 开机/更新后重新调度 WorkManager 守护任务（兜底）
                ServiceWatchdogWorker.schedule(context)
            }
        }
    }

    /**
     * 直接读取 SharedPreferences 判断「加强保活」是否开启。
     * BroadcastReceiver 中不使用 Hilt 注入，避免初始化开销。
     */
    private fun isEnhancedKeepAliveEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENHANCED_KEEP_ALIVE, false)
    }

    /**
     * 检测 MonitorForegroundService 是否在运行，若未运行则启动。
     */
    @Suppress("DEPRECATION")
    private fun startMonitorServiceIfNeeded(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceName = MonitorForegroundService::class.java.name
        val isRunning = manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == serviceName } == true

        if (!isRunning) {
            Log.d(TAG, "MonitorForegroundService 未运行，发起启动")
            MonitorForegroundService.start(context)
        } else {
            Log.d(TAG, "MonitorForegroundService 已在运行，无需重启")
        }
    }

    /**
     * 检测 WatchdogForegroundService 是否在运行，若未运行则启动。
     */
    @Suppress("DEPRECATION")
    private fun startWatchdogServiceIfNeeded(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceName = WatchdogForegroundService::class.java.name
        val isRunning = manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == serviceName } == true

        if (!isRunning) {
            Log.d(TAG, "WatchdogForegroundService 未运行，发起启动")
            WatchdogForegroundService.start(context)
        } else {
            Log.d(TAG, "WatchdogForegroundService 已在运行，无需重启")
        }
    }
}
