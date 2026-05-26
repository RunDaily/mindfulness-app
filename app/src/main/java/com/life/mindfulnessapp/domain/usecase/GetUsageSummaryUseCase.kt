package com.life.mindfulnessapp.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetUsageSummaryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val systemUsageRepository: SystemUsageRepository
) {
    suspend operator fun invoke(): List<AppUsageSummary> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val limits = appLimitRepository.getEnabledAppLimits().first()

        limits.map { limit ->
            // 从系统 UsageStatsManager 获取真实使用时长
            val todaySeconds = systemUsageRepository.getTodayUsageSeconds(limit.packageName)
            val weekSeconds = systemUsageRepository.getWeekUsageSeconds(limit.packageName)

            val appName = try {
                pm.getApplicationLabel(
                    pm.getApplicationInfo(limit.packageName, 0)
                ).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                limit.appName
            }

            AppUsageSummary(
                packageName = limit.packageName,
                appName = appName,
                todaySeconds = todaySeconds,
                weekSeconds = weekSeconds,
                dailyLimitSeconds = limit.dailyLimitMinutes * 60L,
                weeklyLimitSeconds = limit.weeklyLimitMinutes * 60L,
                dailyLimitMinutes = limit.dailyLimitMinutes,
                weeklyLimitMinutes = limit.weeklyLimitMinutes
            )
        }.sortedByDescending { it.todaySeconds }
    }
}
