package com.life.mindfulnessapp.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.receiver.QuotePushReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 定时格言推送：按时段 + 间隔展开时刻，精确闹钟链式预约；保活 ping 兜底补发。
 */
object QuotePushScheduler {

    private const val TAG = "QuotePushScheduler"
    private const val REQ_QUOTE_PUSH = 7200
    /** 保活 ping 时允许补发的窗口（分钟） */
    private const val FALLBACK_WINDOW_MINUTES = 12

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun appPreferences(): AppPreferences
        fun quoteRepository(): QuoteRepository
    }

    private fun deps(context: Context): Entry =
        EntryPointAccessors.fromApplication(context.applicationContext, Entry::class.java)

    fun reschedule(context: Context) {
        // 定时推送已下线：只取消残留闹钟，不再预约
        cancel(context)
    }

    fun cancel(context: Context) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context.applicationContext, REQ_QUOTE_PUSH))
        Log.d(TAG, "已取消格言推送闹钟")
    }

    /** 保活 ping 兜底：功能已移除，空操作。 */
    fun checkDueSlots(context: Context) {
        // no-op
    }

    suspend fun fireSlot(context: Context, slotMinute: Int) {
        val app = context.applicationContext
        val entry = deps(app)
        val prefs = entry.appPreferences()
        if (!prefs.isScheduledQuotePushEnabled()) return
        val allowed = prefs.expandScheduledQuotePushSlots()
        if (slotMinute !in allowed) return
        if (prefs.wasScheduledQuoteSlotFiredToday(slotMinute)) {
            Log.d(TAG, "今日 $slotMinute 已推送，跳过")
            return
        }
        try {
            val quote = entry.quoteRepository().getPushQuote(slotMinute)
            prefs.saveLastPushedQuote(
                id = quote.id,
                content = quote.content,
                author = quote.author,
                authorId = quote.authorId
            )
            QuotePushNotifier.notify(app, slotMinute, quote)
            prefs.markScheduledQuoteSlotFired(slotMinute)
            Log.i(TAG, "已推送格言 slot=$slotMinute")
        } catch (e: Exception) {
            Log.w(TAG, "格言推送失败 slot=$slotMinute: ${e.message}")
            return
        }
        reschedule(app)
    }

    private data class NextSlot(val slotMinute: Int, val triggerAtMillis: Long)

    private fun computeNextSlot(times: List<Int>): NextSlot? {
        if (times.isEmpty()) return null
        val nowMs = System.currentTimeMillis()
        val todaySlots = times.map { minute ->
            val slotCal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
            }
            minute to slotCal.timeInMillis
        }
        val upcoming = todaySlots.firstOrNull { (_, trigger) -> trigger > nowMs }
        if (upcoming != null) {
            return NextSlot(upcoming.first, upcoming.second)
        }
        val firstTomorrow = times.minOrNull() ?: return null
        val tomorrowCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, firstTomorrow / 60)
            set(Calendar.MINUTE, firstTomorrow % 60)
        }
        return NextSlot(firstTomorrow, tomorrowCal.timeInMillis)
    }

    private fun scheduleAt(context: Context, triggerAtMillis: Long, slotMinute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, REQ_QUOTE_PUSH, slotMinute)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms() -> {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                else -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            }
            Log.d(TAG, "已调度格言推送 slot=$slotMinute at=$triggerAtMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "精确闹钟不可用，回退 inexact", e)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "调度格言推送失败", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "调度格言推送失败", e)
        }
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        slotMinute: Int = -1
    ): PendingIntent {
        val intent = Intent(context, QuotePushReceiver::class.java)
            .setAction(QuotePushReceiver.ACTION_QUOTE_PUSH)
            .putExtra(QuotePushReceiver.EXTRA_SLOT_MINUTE, slotMinute)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun currentMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
