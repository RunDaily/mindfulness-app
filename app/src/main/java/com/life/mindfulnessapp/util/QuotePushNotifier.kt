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
import com.life.mindfulnessapp.data.repository.DisplayQuote

/**
 * 定时格言推送：系统通知；点击打开精美格言页。
 */
object QuotePushNotifier {

    const val CHANNEL_ID = "scheduled_quote_push"
    private const val NOTIFICATION_ID_BASE = 7103
    private const val REQUEST_CODE_BASE = 7103

    const val EXTRA_OPEN_QUOTE_MOMENT = "open_quote_moment"
    const val EXTRA_QUOTE_ID = "quote_id"
    const val EXTRA_QUOTE_CONTENT = "quote_content"
    const val EXTRA_QUOTE_AUTHOR = "quote_author"
    const val EXTRA_QUOTE_AUTHOR_ID = "quote_author_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "格言推送",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "在设定时段按间隔推送一句你喜欢的格言"
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notify(context: Context, slotMinute: Int, quote: DisplayQuote) {
        if (quote.content.isBlank()) return
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val authorLine = quote.author.trim().removePrefix("—").trim()
        val title = if (authorLine.isNotBlank()) "— $authorLine" else "心锚 · 格言"
        val body = quote.content.trim()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_QUOTE_MOMENT, true)
            putExtra(EXTRA_QUOTE_ID, quote.id)
            putExtra(EXTRA_QUOTE_CONTENT, body)
            putExtra(EXTRA_QUOTE_AUTHOR, authorLine)
            putExtra(EXTRA_QUOTE_AUTHOR_ID, quote.authorId)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + (slotMinute % 1000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + (slotMinute % 100),
                builder.build()
            )
        } catch (_: SecurityException) {
            /* 通知权限未开 */
        }
    }
}
