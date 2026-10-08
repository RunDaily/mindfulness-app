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
import com.life.mindfulnessapp.data.network.RemoteAuthorNotice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订阅名人有更新时的本地通知（启动拉取未读 notices 后触发）。
 */
@Singleton
class AuthorUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "名人名言更新",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "你订阅的名人有了新名言时提醒"
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyUpdates(notices: List<RemoteAuthorNotice>) {
        if (notices.isEmpty()) return
        ensureChannel()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val primary = notices.first()
        val title = if (notices.size == 1) {
            primary.message.ifBlank { "「${primary.author_name}」有更新" }
        } else {
            "你订阅的 ${notices.size} 位名人有更新"
        }
        val text = if (notices.size == 1) {
            "打开设置 → 名人目录查看"
        } else {
            notices.take(3).joinToString("、") { it.author_name } +
                if (notices.size > 3) "…" else ""
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AUTHOR_CATALOG, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setNumber(notices.size)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            /* 通知权限未开 */
        }
    }

    companion object {
        const val CHANNEL_ID = "author_quote_updates"
        const val NOTIFICATION_ID = 7102
        const val REQUEST_CODE = 7102
        const val EXTRA_OPEN_AUTHOR_CATALOG = "open_author_catalog"
    }
}
