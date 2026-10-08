package com.life.mindfulnessapp.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.life.mindfulnessapp.util.KeepAliveRestarter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 服务守护 Worker：由 WorkManager 周期性调度，检测并拉起 Monitor。
 *
 * WorkManager 保证：
 *   - 即使进程被杀，任务也会在下次调度时间到来时被系统重新拉起执行
 *   - 最小周期 15 分钟（Android 系统限制）
 *   - 设备重启后自动恢复调度（WorkManager 内置）
 *
 * 与 BootReceiver / 无障碍保活的分工：
 *   - BootReceiver：开机 / 更新后即时拉起
 *   - KeepAliveAccessibilityService：系统绑定 + 窗口切换节流自愈
 *   - KeepAliveAlarmScheduler：约 8 分钟 Alarm 链（比本 Worker 更密）
 *   - ServiceWatchdogWorker：15 分钟兜底
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceWatchdog"

        const val WORK_NAME = "service_watchdog"

        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Watchdog 任务已调度，周期 $INTERVAL_MINUTES 分钟")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Watchdog 开始检测服务状态...")
        return try {
            val monitorAlive = KeepAliveRestarter.isServiceRunning(
                context,
                MonitorForegroundService::class.java
            )

            if (!monitorAlive) {
                Log.w(TAG, "MonitorForegroundService 未运行，统一拉起")
                KeepAliveRestarter.ensureRunning(context)
            } else {
                Log.d(TAG, "MonitorForegroundService 正常")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog 检测/重启服务时出错", e)
            Result.retry()
        }
    }
}
