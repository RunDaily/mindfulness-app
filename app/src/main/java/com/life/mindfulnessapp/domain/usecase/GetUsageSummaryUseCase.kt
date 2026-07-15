package com.life.mindfulnessapp.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.service.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetUsageSummaryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): List<AppUsageSummary> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val limits = appLimitRepository.getEnabledAppLimits().first()
        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = getDayRange(now)
        val (weekStart, weekEnd) = getWeekRange(now)

        // 当前进行中的 session（用于将实时时长叠加到对应 App 的今日统计上）
        val activeSession = sessionManager.currentSession.value

        limits.map { limit ->
            // 从本地数据库读取今日 / 本周已完成记录的时长合计
            val dbTodaySeconds = usageRecordRepository.getDailyUsageSeconds(limit.packageName, now)
            val dbWeekSeconds  = usageRecordRepository.getWeeklyUsageSeconds(limit.packageName, now)

            // 若当前有该 App 的活跃 session，将其实时有效时长叠加进去
            // currentSessionSeconds 已排除后台等待时间，与数据库已完成记录口径一致
            val sessionExtra = if (activeSession?.packageName == limit.packageName) {
                activeSession.currentSessionSeconds
            } else 0L

            val todaySeconds = dbTodaySeconds + sessionExtra
            val weekSeconds  = dbWeekSeconds  + sessionExtra

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
