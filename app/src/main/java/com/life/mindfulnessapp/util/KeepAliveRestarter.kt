package com.life.mindfulnessapp.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.life.mindfulnessapp.service.MonitorForegroundService

/**
 * 统一保活拉起入口：Monitor 始终尝试恢复，并续约 Alarm 链。
 * BootReceiver / WorkManager / Alarm / 无障碍服务共用，避免各处默认值与逻辑分叉。
 */
object KeepAliveRestarter {

    private const val TAG = "KeepAliveRestarter"
    private const val PREFS_NAME = "mindfulness_prefs"
    private const val KEY_ENHANCED_KEEP_ALIVE = "enhanced_keep_alive"

    /** 与 [com.life.mindfulnessapp.data.AppPreferences] 默认值保持一致 */
    private const val DEFAULT_ENHANCED_KEEP_ALIVE = true

    fun ensureRunning(context: Context) {
        val appContext = context.applicationContext
        startMonitorIfNeeded(appContext)
        // 每次拉起时续约 Alarm 链，避免进程死后无人再调度
        KeepAliveAlarmScheduler.schedulePeriodic(appContext)
    }

    fun isEnhancedKeepAliveEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENHANCED_KEEP_ALIVE, DEFAULT_ENHANCED_KEEP_ALIVE)
    }

    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceName = serviceClass.name
        return manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == serviceName } == true
    }

    private fun startMonitorIfNeeded(context: Context) {
        if (!isServiceRunning(context, MonitorForegroundService::class.java)) {
            Log.d(TAG, "MonitorForegroundService 未运行，发起启动")
            MonitorForegroundService.start(context)
        }
    }
}
