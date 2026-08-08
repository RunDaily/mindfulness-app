package com.life.mindfulnessapp.domain.usecase

import android.content.Context
import com.life.mindfulnessapp.data.ImpulseStore
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getLastWeekRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.WeeklyAppStat
import com.life.mindfulnessapp.domain.model.WeeklyReportData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 组装「本周觉察」：周一 0 点至今的跨 App 觉察汇总。
 */
class GetWeekAwarenessUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val impulseStore: ImpulseStore
) {
    suspend operator fun invoke(): WeeklyReportData {
        val now = System.currentTimeMillis()
        val (weekStart, weekEnd) = getWeekRange(now)
        val (dayStart, dayEnd) = getDayRange(now)
        val (lastWeekStart, lastWeekEnd) = getLastWeekRange(now)

        val weekRecords = usageRecordRepository.getWeekRecords(weekStart, weekEnd).first()
            .filter { !it.isSeed }
        val lastWeekRecords = usageRecordRepository.getWeekRecords(lastWeekStart, lastWeekEnd).first()
            .filter { !it.isSeed }

        val enterCount = UsageRecordCounts.enterCount(weekRecords)
        val mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(weekRecords)
        val dismissCount = UsageRecordCounts.dismissCount(weekRecords)
        val mindfulRatio = UsageRecordCounts.mindfulRatio(weekRecords)
        val alignmentRate = UsageRecordCounts.alignmentRate(weekRecords)
        val reviewedCount = UsageRecordCounts.reviewedEnterCount(weekRecords)
        val alignedCount = UsageRecordCounts.alignedEnterCount(weekRecords)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateKeys = (0..6).map { i ->
            sdf.format(Date(weekStart + i * 24L * 60 * 60 * 1000))
        }
        val impulseCount = impulseStore.sumImpulseCounts(dateKeys)
        val todayImpulseCount = impulseStore.sumImpulseCounts(listOf(sdf.format(Date(now))))

        val dailyDismissCounts = (0..6).map { i ->
            val dayStartMs = weekStart + i * 24L * 60 * 60 * 1000
            val dayEndMs = dayStartMs + 24L * 60 * 60 * 1000
            val dayRecs = weekRecords.filter { it.startTime in dayStartMs until dayEndMs }
            UsageRecordCounts.dismissCount(dayRecs)
        }

        val allLimits = appLimitRepository.getAllLimitsOnce()
        val limitNameMap = allLimits.associate { it.packageName to it.appName }
        val pm = context.packageManager
        val recordsByPkg = weekRecords.groupBy { it.packageName }

        fun resolveAppName(pkg: String): String =
            limitNameMap[pkg]
                ?: try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg.substringAfterLast(".")
                }

        val appSummaries = allLimits.map { limit ->
            val pkg = limit.packageName
            val recs = recordsByPkg[pkg].orEmpty()
            val todaySec = usageRecordRepository.getAppTotalSeconds(pkg, dayStart, dayEnd)
            val weekSec = usageRecordRepository.getAppTotalSeconds(pkg, weekStart, weekEnd)
            val dailyLimitSec = limit.effectiveDailyLimitMinutes() * 60L
            val weeklyLimitSec = limit.effectiveWeeklyLimitMinutes() * 60L
            WeeklyAppStat(
                packageName = pkg,
                appName = resolveAppName(pkg),
                enterCount = UsageRecordCounts.enterCount(recs),
                dismissCount = UsageRecordCounts.dismissCount(recs),
                mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(recs),
                totalSeconds = weekSec,
                todayLimitReached = dailyLimitSec > 0 && todaySec >= dailyLimitSec,
                weekLimitReached = weeklyLimitSec > 0 && weekSec >= weeklyLimitSec,
                requireIntentOnOpen = limit.requireIntentOnOpen
            )
        }.sortedWith(
            compareByDescending<WeeklyAppStat> { it.dismissCount + it.enterCount }
                .thenByDescending { it.dismissCount }
                .thenByDescending { it.mindfulRatio ?: 0f }
                .thenBy { it.appName }
        )

        val totalSeconds = weekRecords
            .filter { UsageRecordCounts.isEnter(it) }
            .sumOf { it.durationSeconds }
        val prevWeekTotal = lastWeekRecords
            .filter { UsageRecordCounts.isEnter(it) }
            .sumOf { it.durationSeconds }

        val hasIntentGateApps = allLimits.any { it.requireIntentOnOpen }
        val heroText = when {
            allLimits.isEmpty() -> "系上第一只锚，留下觉察痕迹"
            enterCount == 0 && dismissCount == 0 -> "本周开始记下每一次选择"
            todayImpulseCount == 0 && (enterCount > 0 || dismissCount > 0) ->
                "今天水面还静着"
            dismissCount >= 5 -> "这周守住了 $dismissCount 次"
            dismissCount > 0 && dismissCount >= enterCount -> "这周守住了 $dismissCount 次"
            mindfulRatio != null && mindfulRatio >= 0.7f ->
                "这周多数进入，都带着意图"
            mindfulRatio != null && mindfulRatio >= 0.5f ->
                "这周 ${(mindfulRatio * 100).toInt()}% 的进入带着意图"
            alignmentRate != null ->
                "写过意图的回顾里，有 ${(alignmentRate * 100).toInt()}% 与目的对齐"
            else -> "有意识地使用，比少用更重要"
        }

        return WeeklyReportData(
            weekStartMs = weekStart,
            weekEndMs = weekEnd - 1,
            heroText = heroText,
            enterCount = enterCount,
            mindfulEnterCount = mindfulEnterCount,
            dismissCount = dismissCount,
            impulseCount = impulseCount,
            mindfulRatio = mindfulRatio,
            alignmentRate = alignmentRate,
            reviewedCount = reviewedCount,
            alignedCount = alignedCount,
            totalSeconds = totalSeconds,
            prevWeekTotalSeconds = prevWeekTotal,
            appSummaries = appSummaries,
            dailyDismissCounts = dailyDismissCounts,
            hasIntentGateApps = hasIntentGateApps,
            hasMonitoredApps = allLimits.isNotEmpty()
        )
    }
}
