package com.life.mindfulnessapp.domain.usecase

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

/**
 * 从系统 UsageStatsManager 获取某个 App 过去 N 天的日均使用时长（分钟）。
 *
 * 用途：在用户重新设定时间限制时，展示历史使用习惯作为参考，
 * 帮助用户设定一个"跳一跳够得到"的合理目标，而不是随手瞎填。
 */
class GetAppHistoryUsageUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class HistoryUsageResult(
        /** 过去 [lookbackDays] 天内有记录的天数 */
        val validDays: Int,
        /** 日均使用时长（分钟），只统计有使用记录的天 */
        val avgDailyMinutes: Int,
        /** 过去 [lookbackDays] 天每天的使用分钟数（按日期升序，0 表示当天没有记录） */
        val dailyMinutes: List<Int>,
        /** 推荐目标下限（分钟）：日均的 60% */
        val recommendedMinLow: Int,
        /** 推荐目标上限（分钟）：日均的 80% */
        val recommendedMinHigh: Int
    )

    /**
     * @param packageName 要查询的 App 包名
     * @param lookbackDays 回溯天数，默认 7 天
     * @return 历史使用结果，若无数据或无权限则返回全零结果
     */
    suspend operator fun invoke(
        packageName: String,
        lookbackDays: Int = 7
    ): HistoryUsageResult = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val now = System.currentTimeMillis()
            // 查询起点：lookbackDays 天前的零点
            val startCal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -lookbackDays)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = startCal.timeInMillis

            // 按天查询，每天单独统计
            val dailyMinutes = mutableListOf<Int>()
            for (dayOffset in lookbackDays downTo 1) {
                val dayCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = dayCal.timeInMillis
                val dayEnd = dayStart + 24 * 60 * 60 * 1000L

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStart,
                    dayEnd
                )
                val totalMs = stats
                    ?.filter { it.packageName == packageName }
                    ?.sumOf { it.totalTimeInForeground }
                    ?: 0L
                dailyMinutes.add((totalMs / 1000 / 60).toInt())
            }

            // 只统计有使用记录的天（> 0 分钟）
            val validDays = dailyMinutes.count { it > 0 }
            val avgDailyMinutes = if (validDays > 0) {
                dailyMinutes.filter { it > 0 }.average().toInt()
            } else 0

            // 推荐区间：60% ~ 80% 的日均，至少5分钟
            val recommendedLow = (avgDailyMinutes * 0.6).toInt().coerceAtLeast(5)
            val recommendedHigh = (avgDailyMinutes * 0.8).toInt().coerceAtLeast(recommendedLow + 5)

            HistoryUsageResult(
                validDays = validDays,
                avgDailyMinutes = avgDailyMinutes,
                dailyMinutes = dailyMinutes,
                recommendedMinLow = recommendedLow,
                recommendedMinHigh = recommendedHigh
            )
        } catch (e: Exception) {
            // 无权限或其他异常，返回空结果
            HistoryUsageResult(
                validDays = 0,
                avgDailyMinutes = 0,
                dailyMinutes = emptyList(),
                recommendedMinLow = 30,
                recommendedMinHigh = 60
            )
        }
    }
}
