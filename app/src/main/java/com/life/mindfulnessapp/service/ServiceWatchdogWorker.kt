package com.life.mindfulnessapp.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 服务守护 Worker：由 WorkManager 周期性调度，检测 MonitorForegroundService 是否仍在运行，
 * 若已被系统杀死则自动重新拉起，实现三层保活体系中的第一层（最高可靠性兜底）。
 *
 * WorkManager 保证：
 *   - 即使进程被杀，WorkManager 任务也会在下次调度时间到来时被系统重新拉起执行
 *   - 最小周期 15 分钟（Android 系统限制）
 *   - 设备重启后自动恢复调度（WorkManager 内置）
 *
 * 与 BootReceiver 的分工：
 *   - BootReceiver：负责开机/亮屏/解锁时的即时响应（事件驱动，秒级延迟）
 *   - ServiceWatchdogWorker：负责兜底保障（周期调度，最坏情况 15 分钟发现被杀）
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceWatchdog"

        /** WorkManager 中的唯一任务名，用于 ExistingPeriodicWorkPolicy.KEEP 去重 */
        const val WORK_NAME = "service_watchdog"

        /** 调度周期：15 分钟（Android 允许的最小值） */
        private const val INTERVAL_MINUTES = 15L

        /**
         * 注册/更新 ServiceWatchdogWorker 的周期任务。
         * 使用 KEEP 策略：如果任务已存在且未取消，不重新入队（避免重复）。
         * 在 MonitorForegroundService.onCreate() 和 BootReceiver.onReceive() 中调用。
         */
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
            if (!isMonitorServiceRunning()) {
                Log.w(TAG, "⚠️ 检测到 MonitorForegroundService 未运行，尝试重启...")
                MonitorForegroundService.start(context)
                Log.i(TAG, "✅ MonitorForegroundService 重启指令已发送")
            } else {
                Log.d(TAG, "✅ MonitorForegroundService 正在运行，无需处理")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog 检测/重启服务时出错", e)
            // 返回 retry，WorkManager 会在合适时机重试
            Result.retry()
        }
    }

    /**
     * 检测 MonitorForegroundService 是否正在运行。
     *
     * 注意：Android 8+ 起 getRunningServices() 对第三方应用只能查看自身进程的服务，
     * 这里正是检测我们自己的服务，所以完全可靠。
     */
    @Suppress("DEPRECATION")
    private fun isMonitorServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceName = MonitorForegroundService::class.java.name
        return manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == serviceName } == true
    }
}
