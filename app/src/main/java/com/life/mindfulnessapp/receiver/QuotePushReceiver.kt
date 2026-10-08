package com.life.mindfulnessapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.life.mindfulnessapp.util.QuotePushScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 定时格言推送闹钟接收器。
 */
class QuotePushReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "QuotePushReceiver"
        const val ACTION_QUOTE_PUSH = "com.life.mindfulnessapp.action.QUOTE_PUSH"
        const val EXTRA_SLOT_MINUTE = "slot_minute"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_QUOTE_PUSH) return
        val slotMinute = intent.getIntExtra(EXTRA_SLOT_MINUTE, -1)
        if (slotMinute !in 0..1439) {
            Log.w(TAG, "无效 slot=$slotMinute")
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                QuotePushScheduler.fireSlot(context, slotMinute)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
