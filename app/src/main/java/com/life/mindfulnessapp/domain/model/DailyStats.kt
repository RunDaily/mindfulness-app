package com.life.mindfulnessapp.domain.model

/**
 * 某天的统计汇总（用于报表展示）
 */
data class DailyStats(
    val dateLabel: String,        // 例如 "周一", "04/15"
    val dateMs: Long,
    val totalSeconds: Long,       // 当天所有被监控 App 的总使用时长
    val appBreakdown: Map<String, Long> // packageName -> 使用秒数
)

/**
 * App 级别的使用汇总
 */
data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val todaySeconds: Long,
    val weekSeconds: Long,
    val dailyLimitSeconds: Long,
    val weeklyLimitSeconds: Long,
    val dailyLimitMinutes: Int = 0,
    val weeklyLimitMinutes: Int = 0
) {
    val dailyUsagePercent: Float
        get() = if (dailyLimitSeconds > 0) {
            (todaySeconds.toFloat() / dailyLimitSeconds).coerceAtMost(1f)
        } else 0f
}
