package com.life.mindfulnessapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.life.mindfulnessapp.MainActivity
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 带着意图用过、却还没对照：延后发出一条低打扰系统通知，点进心锚对照该次。
 * 若已对照、已继续上次（记录重新打开）、或没有意图，则静默跳过。
 */
@HiltWorker
class SessionCompareReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val usageRecordRepository: UsageRecordRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CompareRemind"
        const val DELAY_MINUTES = 8L
        const val CHANNEL_ID = "session_compare_remind"
        const val NOTIFICATION_ID_BASE = 3002
        private const val KEY_RECORD_ID = "record_id"
        private const val KEY_PKG = "pkg"
        private const val KEY_APP_NAME = "app_name"
        private const val KEY_PURPOSE = "purpose"

        private fun workName(recordId: Long) = "session_compare_reminder_$recordId"

        fun schedule(
            context: Context,
            recordId: Long,
            packageName: String,
            appName: String,
            purpose: String?
        ) {
            scheduleIfUnreviewedIntent(context, recordId, packageName, appName, purpose)
        }

        /**
         * 仅当这次确实写过意图时预约。无目的 / 门外守住等不会发。
         */
        fun scheduleIfUnreviewedIntent(
            context: Context,
            recordId: Long,
            packageName: String,
            appName: String,
            purpose: String?
        ) {
            if (recordId <= 0L) return
            if (purpose.isNullOrBlank()) return
            val request = OneTimeWorkRequestBuilder<SessionCompareReminderWorker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_RECORD_ID to recordId,
                        KEY_PKG to packageName,
                        KEY_APP_NAME to appName,
                        KEY_PURPOSE to purpose.trim()
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(recordId),
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "已预约对照提醒 recordId=$recordId delay=${DELAY_MINUTES}m")
        }

        fun cancel(context: Context, recordId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(recordId))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID_BASE)
        }
    }

    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, -1L)
        if (recordId <= 0L) return Result.success()
        val record = usageRecordRepository.getRecordById(recordId)
        if (record == null) {
            Log.d(TAG, "记录已不存在，跳过对照提醒 recordId=$recordId")
            return Result.success()
        }
        if (record.endTime <= 0L) {
            Log.d(TAG, "记录已重新打开，跳过对照提醒 recordId=$recordId")
            return Result.success()
        }
        if (record.purpose.isNullOrBlank()) {
            Log.d(TAG, "没有意图，跳过对照提醒 recordId=$recordId")
            return Result.success()
        }
        if (UsageRecordEntity.MindfulnessLevel.isValid(record.mindfulnessLevel)) {
            Log.d(TAG, "已对照过，跳过提醒 recordId=$recordId")
            return Result.success()
        }
        val appName = inputData.getString(KEY_APP_NAME)
            ?.trim()
            .orEmpty()
            .ifBlank { record.packageName.substringAfterLast('.') }
        val purpose = inputData.getString(KEY_PURPOSE)?.trim().orEmpty()
            .ifBlank { record.purpose?.trim().orEmpty() }
        postReminder(recordId, appName, purpose)
        return Result.success()
    }

    private fun postReminder(recordId: Long, appName: String, purpose: String) {
        ensureChannel()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d(TAG, "通知关闭，跳过对照提醒")
            return
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (recordId and 0x7FFFFFFF).toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = MonitorForegroundService.ACTION_OPEN_NOTE
                putExtra(MonitorForegroundService.EXTRA_NOTE_RECORD_ID, recordId)
                putExtra(MonitorForegroundService.EXTRA_SESSION_REVIEWED, false)
                putExtra(MonitorForegroundService.EXTRA_OPEN_COMPARE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = if (purpose.isNotEmpty()) {
            "$appName · ${purpose.take(18)}"
        } else {
            appName
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("刚才那次，还没对照")
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_BASE, notification)
        Log.d(TAG, "已发出对照提醒 recordId=$recordId")
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "对照提醒",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "带着意图用过却还没对照时的提醒"
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
