package com.life.mindfulnessapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.life.mindfulnessapp.service.ServiceWatchdogWorker
import com.life.mindfulnessapp.util.KeepAliveRestarter
import com.life.mindfulnessapp.util.QuotePushScheduler

/**
 * 保活广播接收器：开机 / 自身更新后拉起监控服务，并重新调度 WorkManager。
 *
 * 注意：ACTION_SCREEN_ON / ACTION_USER_PRESENT 无法在 Manifest 静态注册（Android 8+）。
 * 亮屏后的秒级自愈改由可选 [com.life.mindfulnessapp.service.KeepAliveAccessibilityService]
 * （系统绑定时窗口切换事件）与前台服务内动态广播（会话逻辑）承担。
 *
 * QUICKBOOT_POWERON：部分国产 ROM（含 vivo）快速开机路径。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "收到 ${intent.action}，统一拉起保活服务并调度 Watchdog Worker")
                KeepAliveRestarter.ensureRunning(context)
                ServiceWatchdogWorker.schedule(context)
                QuotePushScheduler.reschedule(context)
            }
        }
    }
}
