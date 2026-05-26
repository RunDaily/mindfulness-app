package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.dao.HourlyUsage
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 单个 App 全维度使用洞察数据（用于详情页和今日统计增强展示）
 */
data class AppDetailStats(
    val packageName: String,
    val appName: String,

    // ── 基础时长 ──────────────────────────────────────────────
    val todaySeconds: Long,
    val weekSeconds: Long,
    val dailyLimitSeconds: Long,
    val weeklyLimitSeconds: Long,

    // ── 行为频次 ──────────────────────────────────────────────
    /** 今日打开次数 */
    val todayOpenCount: Int,
    /** 今日平均单次使用时长（秒） */
    val avgSessionSeconds: Long,
    /** 今日最长单次使用时长（秒） */
    val longestSessionSeconds: Long,

    // ── 时段分布 ──────────────────────────────────────────────
    /** 今日 24 小时使用分布（索引=小时 0-23，值=该小时使用秒数）*/
    val hourlyDistribution: List<HourlyUsage>,

    // ── 使用质量 ──────────────────────────────────────────────
    /** 今日有目的打开次数（写了使用目的的次数）*/
    val purposefulOpenCount: Int,
    /** 是否启用时长监控 */
    val timeLimitEnabled: Boolean = true,
    /** 超时提醒文案 */
    val overTimeMessage: String = "",

    // ── 趋势对比（昨日）────────────────────────────────────────
    /** 昨日使用时长（秒）*/
    val yesterdaySeconds: Long,

    // ── 本周每日数据（用于本周 Tab）────────────────────────────
    /** 本周每日使用秒数，列表长度=7，索引 0=周一，索引 6=周日 */
    val weekDailySeconds: List<Long> = List(7) { 0L },
    /** 本周打开总次数 */
    val weekOpenCount: Int = 0,

    // ── 今日使用记录列表（用于记录 Tab）────────────────────────
    /** 今日所有使用记录（倒序，最新在前）*/
    val todayRecords: List<UsageRecordEntity> = emptyList(),
    /** 本周所有使用记录（倒序，最新在前）*/
    val weekRecords: List<UsageRecordEntity> = emptyList(),
) {
    /** 今日使用进度（0-1f，超出则 = 1f）*/
    val dailyUsagePercent: Float
        get() = if (dailyLimitSeconds > 0)
            (todaySeconds.toFloat() / dailyLimitSeconds).coerceAtMost(1f)
        else 0f

    /** 有目的使用比率（0-1f）：有目的打开次数 / 总打开次数 */
    val purposeRatio: Float
        get() = if (todayOpenCount > 0)
            purposefulOpenCount.toFloat() / todayOpenCount
        else 0f

    /** 今日 vs 昨日变化量（秒，正=增加，负=减少）*/
    val vsYesterdayDeltaSeconds: Long
        get() = todaySeconds - yesterdaySeconds

    /** 今日 vs 昨日变化百分比（正=更多，负=更少），昨日为 0 时返回 null */
    val vsYesterdayPercent: Float?
        get() = if (yesterdaySeconds > 0)
            (todaySeconds - yesterdaySeconds).toFloat() / yesterdaySeconds
        else null

    /** 高峰使用小时（使用最多的那个小时，null 表示没有数据）*/
    val peakHour: Int?
        get() = hourlyDistribution.maxByOrNull { it.totalSeconds }?.hour
}

/**
 * 全局（所有被监控 App）统计快照
 */
data class GlobalDayStats(
    /** 总使用时长（秒）*/
    val totalSeconds: Long,
    /** 已使用 App 数量 */
    val activeAppCount: Int,
    /** 接近或超过限额的 App 数量（>=80%）*/
    val nearLimitCount: Int,
    /** 全局 24h 时段分布 */
    val hourlyDistribution: List<HourlyUsage>,
    /** 各 App 占总时间百分比，packageName -> ratio (0-1f) */
    val appShareRatios: Map<String, Float>,
)

/**
 * 本周 vs 上周趋势对比
 */
data class WeekTrendStats(
    /** 本周每日时长（列表长度=7，索引0=周一）*/
    val thisWeekDailySeconds: List<Long>,
    /** 上周每日时长（列表长度=7，索引0=周一）*/
    val lastWeekDailySeconds: List<Long>,
    /** 本周总时长（秒）*/
    val thisWeekTotal: Long,
    /** 上周总时长（秒）*/
    val lastWeekTotal: Long,
) {
    /** 本周 vs 上周总时长变化百分比，上周为 0 时返回 null */
    val weekOverWeekPercent: Float?
        get() = if (lastWeekTotal > 0)
            (thisWeekTotal - lastWeekTotal).toFloat() / lastWeekTotal
        else null
}
