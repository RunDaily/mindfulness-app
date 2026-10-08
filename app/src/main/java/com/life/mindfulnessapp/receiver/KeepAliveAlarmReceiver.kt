package com.life.mindfulnessapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.life.mindfulnessapp.util.KeepAliveAlarmScheduler
import com.life.mindfulnessapp.util.KeepAliveRestarter
import com.life.mindfulnessapp.util.QuotePushScheduler

/**
 * AlarmManager 保活唤醒：拉起 Monitor，并续约下一轮周期闹钟。
 */
class KeepAliveAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KeepAliveAlarm"
        const val ACTION_PING = "com.life.mindfulnessapp.action.KEEP_ALIVE_PING"
        const val ACTION_RESTART_NOW = "com.life.mindfulnessapp.action.KEEP_ALIVE_RESTART_NOW"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_PING && action != ACTION_RESTART_NOW) return
        Log.d(TAG, "收到 $action，统一拉起保活服务")
        KeepAliveRestarter.ensureRunning(context)
        KeepAliveAlarmScheduler.schedulePeriodic(context)
        QuotePushScheduler.checkDueSlots(context)
    }
}
