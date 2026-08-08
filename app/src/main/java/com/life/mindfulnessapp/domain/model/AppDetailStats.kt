package com.life.mindfulnessapp.domain.model

/**
 * 周报 / 本周觉察中单个 App 的觉察统计
 */
data class WeeklyAppStat(
    val packageName: String,
    val appName: String,
    val enterCount: Int,
    val dismissCount: Int,
    val mindfulEnterCount: Int,
    val totalSeconds: Long,
    /** 今日是否已触日限额顶 */
    val todayLimitReached: Boolean = false,
    /** 本周是否已触周限额顶 */
    val weekLimitReached: Boolean = false,
    /** 是否开启意图门（用于详情展示） */
    val requireIntentOnOpen: Boolean = true,
) {
    val mindfulRatio: Float?
        get() = if (enterCount > 0) mindfulEnterCount.toFloat() / enterCount else null

    val limitReachedLabel: String?
        get() = when {
            todayLimitReached -> "今日已触顶"
            weekLimitReached -> "本周已触顶"
            else -> null
        }
}

/**
 * 本周觉察数据包（周一 0 点 → 此刻；可含进行中周）
 */
data class WeeklyReportData(
    val weekStartMs: Long,
    val weekEndMs: Long,
    /** Hero 一句人话 */
    val heroText: String,
    val enterCount: Int,
    val mindfulEnterCount: Int,
    val dismissCount: Int,
    val impulseCount: Int,
    /** 有意图打开占比；无放行时 null */
    val mindfulRatio: Float?,
    /** 对照达成率（仅 ALIGNED）；样本不足 null */
    val alignmentRate: Float?,
    val reviewedCount: Int,
    val alignedCount: Int,
    val totalSeconds: Long,
    val prevWeekTotalSeconds: Long,
    val appSummaries: List<WeeklyAppStat>,
    /** 周一→周日每日守住次数（进行中周未到天为 0） */
    val dailyDismissCounts: List<Int> = List(7) { 0 },
    /** 是否有开启意图门的监控 App */
    val hasIntentGateApps: Boolean = false,
    /** 是否已系锚 */
    val hasMonitoredApps: Boolean = false,
) {
    val holdRate: Float?
        get() = if (impulseCount > 0) dismissCount.toFloat() / impulseCount else null

    /** 本周是否有足够事件，适合在今日露出强入口 */
    val showHomeEntry: Boolean
        get() = hasMonitoredApps && (enterCount + dismissCount >= 1 || impulseCount >= 1)

    /** 已系锚但本周尚静：今日弱入口 */
    val showQuietHomeEntry: Boolean
        get() = hasMonitoredApps && !showHomeEntry

    val homeTeaser: String
        get() = when {
            dismissCount > 0 -> "本周觉察 · 守住了 $dismissCount 次"
            mindfulRatio != null && mindfulRatio >= 0.5f ->
                "本周觉察 · ${(mindfulRatio * 100).toInt()}% 带着意图"
            enterCount > 0 -> "本周觉察 · 已记下 $enterCount 次进入"
            else -> "本周觉察"
        }

    val quietHomeTeaser: String get() = "本周还很安静 · 看看"
}

/**
 * App 使用回看：
 * - [weeksAgo] = 0：进行中的本周（到现在）
 * - [weeksAgo] ≥ 1：已结束的完整周
 */
data class AppWeekReview(
    val packageName: String,
    val appName: String,
    val weekStartMs: Long,
    val weekEndMs: Long,
    /** 0=本周到现在；1=上周… */
    val weeksAgo: Int,
    val requireIntentOnOpen: Boolean,
    val days: List<AppWeekReviewDay>,
    val intentItems: List<AppWeekReviewIntentItem>,
) {
    val isCurrentWeek: Boolean get() = weeksAgo == 0
    val weekTotalSeconds: Long get() = days.sumOf { it.durationSeconds }
    val weekDismissCount: Int get() = days.sumOf { it.dismissCount }
    val weekEnterCount: Int get() = intentItems.size
}

data class AppWeekReviewDay(
    /** 0=周一 … 6=周日 */
    val dayIndex: Int,
    val weekdayLabel: String,
    val dateLabel: String,
    val dayStartMs: Long,
    val durationSeconds: Long,
    val dismissCount: Int,
)

/** 「做了什么」时间轴节点：一次进入 */
data class AppWeekReviewIntentItem(
    val recordId: Long,
    val startTime: Long,
    val durationSeconds: Long,
    val purpose: String?,
    val note: String?,
    val mindfulnessLevel: Int?,
)
