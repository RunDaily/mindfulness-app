package com.life.mindfulnessapp.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.analytics.FeedbackThread
import com.life.mindfulnessapp.service.MonitorForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 开发者回复的本地通知（独立渠道，与监控常驻通知分离）。
 * [setNumber] + 渠道 [setShowBadge]：在支持的启动器上体现桌面角标。
 */
@Singleton
class FeedbackReplyNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "开发者回复",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "意见反馈收到开发者回复时提醒"
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyNewReplies(threads: List<FeedbackThread>, unreadTotal: Int = threads.size) {
        if (threads.isEmpty()) return
        ensureChannel()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val primary = threads.maxByOrNull { it.repliedAtMs } ?: return
        val title = if (threads.size == 1) {
            "开发者回复了你的反馈"
        } else {
            "开发者回复了 ${threads.size} 条反馈"
        }
        val preview = primary.reply.trim().replace('\n', ' ').take(80)
        val text = if (preview.isBlank()) "点此查看" else preview
        val badge = unreadTotal.coerceAtLeast(threads.size).coerceAtLeast(1)

        postNotification(
            title = title,
            text = text,
            feedbackId = primary.id,
            badgeNumber = badge
        )
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun postNotification(
        title: String,
        text: String,
        feedbackId: Long,
        badgeNumber: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MonitorForegroundService.ACTION_OPEN_FEEDBACK_REPLY
            if (feedbackId > 0L) {
                putExtra(MonitorForegroundService.EXTRA_FEEDBACK_ID, feedbackId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setNumber(badgeNumber)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授权时忽略
        }
    }

    companion object {
        const val CHANNEL_ID = "feedback_reply"
        const val NOTIFICATION_ID = 4101
    }
}
