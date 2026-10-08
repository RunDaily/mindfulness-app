package com.life.mindfulnessapp.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.life.mindfulnessapp.receiver.KeepAliveAlarmReceiver

/**
 * AlarmManager 保活兜底：比 WorkManager（最少 15 分钟）更密，
 * 且在部分国产 ROM 上进程被杀后仍可能唤醒（前提是未被「强行停止」、且自启动白名单已开）。
 *
 * 采用一次性闹钟链式续约，避免 setRepeating 在 Doze / 厂商上被吞。
 */
object KeepAliveAlarmScheduler {

    private const val TAG = "KeepAliveAlarm"
    private const val REQ_PERIODIC = 7101
    private const val REQ_IMMEDIATE = 7102

    /** 默认周期（三星等相对温和 ROM） */
    private const val INTERVAL_MS = 8 * 60 * 1000L

    /** 国产严苛 ROM：更密的链式闹钟，提高死后被唤醒概率 */
    private const val INTERVAL_HARSH_MS = 3 * 60 * 1000L

    /** 从最近任务划掉后的短延迟重启（部分 ROM 立即 startService 会失败） */
    private const val IMMEDIATE_DELAY_MS = 1_500L

    /** 二次短延迟：部分 ROM 第一次 startForegroundService 会被吞 */
    private const val IMMEDIATE_RETRY_DELAY_MS = 5_000L
    private const val REQ_IMMEDIATE_RETRY = 7103

    fun schedulePeriodic(context: Context) {
        val harsh = RecentsHider.defaultHideOnThisDevice()
        schedule(
            context = context.applicationContext,
            requestCode = REQ_PERIODIC,
            action = KeepAliveAlarmReceiver.ACTION_PING,
            delayMs = if (harsh) INTERVAL_HARSH_MS else INTERVAL_MS
        )
    }

    fun scheduleImmediateRestart(context: Context) {
        val app = context.applicationContext
        schedule(
            context = app,
            requestCode = REQ_IMMEDIATE,
            action = KeepAliveAlarmReceiver.ACTION_RESTART_NOW,
            delayMs = IMMEDIATE_DELAY_MS
        )
        if (RecentsHider.defaultHideOnThisDevice()) {
            schedule(
                context = app,
                requestCode = REQ_IMMEDIATE_RETRY,
                action = KeepAliveAlarmReceiver.ACTION_RESTART_NOW,
                delayMs = IMMEDIATE_RETRY_DELAY_MS
            )
        }
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(app, REQ_PERIODIC, KeepAliveAlarmReceiver.ACTION_PING))
        am.cancel(pendingIntent(app, REQ_IMMEDIATE, KeepAliveAlarmReceiver.ACTION_RESTART_NOW))
        am.cancel(pendingIntent(app, REQ_IMMEDIATE_RETRY, KeepAliveAlarmReceiver.ACTION_RESTART_NOW))
        Log.d(TAG, "已取消全部保活闹钟")
    }

    private fun schedule(context: Context, requestCode: Int, action: String, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, requestCode, action)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms() -> {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
                else -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            }
            Log.d(TAG, "已调度 $action，delay=${delayMs}ms")
        } catch (e: SecurityException) {
            Log.w(TAG, "精确闹钟不可用，回退 inexact", e)
            try {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "调度保活闹钟失败", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "调度保活闹钟失败", e)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, KeepAliveAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
