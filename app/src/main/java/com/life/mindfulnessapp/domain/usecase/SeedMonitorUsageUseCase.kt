package com.life.mindfulnessapp.domain.usecase

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import javax.inject.Inject

/**
 * 新加入（或重新启用）监控时，用系统 UsageStats 把「加入前」已用时长写入本地记录。
 *
 * 策略：只补缺口 gap = max(0, 系统用量 − 本地已有)，避免重复累加；
 * - 今日缺口 → 一条 [SEED_FROM_SYSTEM]，计入今日正念时长与限额；
 * - 本周其余缺口（不含今日）→ 另写一条落在周初，只进周统计。
 */
class SeedMonitorUsageUseCase @Inject constructor(
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository
) {
    suspend operator fun invoke(packageName: String) {
        val now = System.currentTimeMillis()
        val (dayStart, _) = getDayRange(now)
        val (weekStart, _) = getWeekRange(now)

        val systemToday = systemUsageRepository.getTodayUsageSeconds(packageName)
        val systemWeek = systemUsageRepository.getWeekUsageSeconds(packageName)
        val dbToday = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val dbWeek = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)

        val todayGap = (systemToday - dbToday).coerceAtLeast(0L)
        // 周缺口先扣除即将写入的今日种子，避免周统计重复计入今日
        val weekGap = (systemWeek - dbWeek - todayGap).coerceAtLeast(0L)

        if (todayGap > 0L) {
            usageRecordRepository.insertRecord(
                UsageRecordEntity(
                    packageName = packageName,
                    startTime = dayStart,
                    endTime = now.coerceAtLeast(dayStart + 1L),
                    durationSeconds = todayGap,
                    endReason = UsageRecordEntity.EndReason.SEED_FROM_SYSTEM
                )
            )
        }

        // 周初种子不得落在「今天」，否则会进日统计
        if (weekGap > 0L && weekStart < dayStart) {
            val weekSeedEnd = (weekStart + weekGap * 1000L).coerceAtMost(dayStart - 1L)
            usageRecordRepository.insertRecord(
                UsageRecordEntity(
                    packageName = packageName,
                    startTime = weekStart,
                    endTime = weekSeedEnd.coerceAtLeast(weekStart + 1L),
                    durationSeconds = weekGap,
                    endReason = UsageRecordEntity.EndReason.SEED_FROM_SYSTEM
                )
            )
        }
    }
}
