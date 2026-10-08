package com.life.mindfulnessapp.domain.model

/**
 * 系统「使用情况」口径的单次前台会话（探索详情用）。
 * 由 UsageEvents 前后台配对还原，不含意图等心锚自有字段。
 */
data class SystemForegroundSession(
    val startMs: Long,
    /** 结束时间戳；仍在前台时为查询终点（通常为 now） */
    val endMs: Long,
    val durationSeconds: Long,
    val ongoing: Boolean = false,
    /**
     * 是否计为「一次打开」。
     * 跨日切开的后半段为 false，避免一次打开被计两次。
     */
    val countsAsOpen: Boolean = true
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** 一天内按时段统计的打开次数（按会话开始时刻分桶） */
data class SystemDayPeriodStats(
    val dawnOpens: Int,      // 0–6
    val morningOpens: Int,   // 6–12
    val afternoonOpens: Int, // 12–18
    val eveningOpens: Int    // 18–24
) {
    companion object {
        val Zero = SystemDayPeriodStats(0, 0, 0, 0)
    }
}

/** 探索详情：单日系统用量（含逐次会话） */
data class SystemUsageDayDetail(
    val dayStartMs: Long,
    val label: String,
    /** 图表横轴：MM-dd */
    val chartDateLabel: String,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val sessions: List<SystemForegroundSession>,
    val totalSeconds: Long,
    /** 当日进入次数（= 下方「每次进入」记录条数） */
    val openCount: Int,
    val periods: SystemDayPeriodStats
)

/** 探索详情图表维度 */
enum class ExploreChartMetric {
    Duration,
    Launches
}

/**
 * 单日系统用量对齐结果：总数与逐次同源（前台会话之和）。
 */
data class SystemDayAligned(
    val totalSeconds: Long,
    val sessions: List<SystemForegroundSession>
) {
    companion object {
        val Empty = SystemDayAligned(0L, emptyList())
    }
}

/**
 * 二八统计：一行（含占总量比例与累计比例）。
 * [shareOfTotal] / [cumulativeShare] 相对当前排序指标（时长或打开次数）的总量。
 */
data class ParetoRankRow(
    val rank: Int,
    val app: AppInfo,
    val usage: AppWeeklySystemUsage,
    /** 0..1，相对总量的占比 */
    val shareOfTotal: Float,
    /** 0..1，从第 1 名累加到本行的占比 */
    val cumulativeShare: Float
)

/**
 * 近 7 日系统用量的二八切分（阈值默认 80%）。
 * [core]：累计首次达到阈值的最少 App；[tail]：其余。
 */
data class ParetoUsageSnapshot(
    val metric: BatchPickSortMode,
    val totalSeconds: Long,
    val totalLaunches: Int,
    val threshold: Float,
    val core: List<ParetoRankRow>,
    val tail: List<ParetoRankRow>
) {
    val coreShare: Float get() = core.lastOrNull()?.cumulativeShare ?: 0f
    val tailShare: Float get() = (1f - coreShare).coerceAtLeast(0f)
    val isEmpty: Boolean get() = core.isEmpty() && tail.isEmpty()

    companion object {
        const val DEFAULT_THRESHOLD = 0.8f
        val Empty = ParetoUsageSnapshot(
            metric = BatchPickSortMode.Duration,
            totalSeconds = 0L,
            totalLaunches = 0,
            threshold = DEFAULT_THRESHOLD,
            core = emptyList(),
            tail = emptyList()
        )
    }
}

/**
 * 按时长或打开次数降序，累计切到 [threshold]（默认 80%）。
 * 总量为 0 时返回空快照。
 */
fun computeParetoUsage(
    ranked: List<Pair<AppInfo, AppWeeklySystemUsage>>,
    metric: BatchPickSortMode,
    threshold: Float = ParetoUsageSnapshot.DEFAULT_THRESHOLD
): ParetoUsageSnapshot {
    if (ranked.isEmpty()) {
        return ParetoUsageSnapshot.Empty.copy(metric = metric, threshold = threshold)
    }
    val totalSeconds = ranked.sumOf { it.second.totalSeconds }
    val totalLaunches = ranked.sumOf { it.second.totalLaunches }
    val totalValue = when (metric) {
        BatchPickSortMode.Duration -> totalSeconds
        BatchPickSortMode.Launches -> totalLaunches.toLong()
    }
    if (totalValue <= 0L) {
        return ParetoUsageSnapshot(
            metric = metric,
            totalSeconds = totalSeconds,
            totalLaunches = totalLaunches,
            threshold = threshold,
            core = emptyList(),
            tail = emptyList()
        )
    }
    val rows = ArrayList<ParetoRankRow>(ranked.size)
    var cumulative = 0L
    ranked.forEachIndexed { index, (app, usage) ->
        val value = when (metric) {
            BatchPickSortMode.Duration -> usage.totalSeconds
            BatchPickSortMode.Launches -> usage.totalLaunches.toLong()
        }
        cumulative += value
        rows += ParetoRankRow(
            rank = index + 1,
            app = app,
            usage = usage,
            shareOfTotal = value.toFloat() / totalValue.toFloat(),
            cumulativeShare = cumulative.toFloat() / totalValue.toFloat()
        )
    }
    val cutIndex = rows.indexOfFirst { it.cumulativeShare >= threshold }
    val coreCount = if (cutIndex < 0) rows.size else (cutIndex + 1)
    return ParetoUsageSnapshot(
        metric = metric,
        totalSeconds = totalSeconds,
        totalLaunches = totalLaunches,
        threshold = threshold,
        core = rows.take(coreCount),
        tail = rows.drop(coreCount)
    )
}

/** 探索详情：7 个完整自然日（不含今天），与多选网格 / 排行列表同口径 */
data class ExploreAppUsageDetail(
    /** 今天之前连续 7 个完整自然日，从旧到新 */
    val completeDays: List<SystemUsageDayDetail>,
    /** 与排行列表相同口径的 7 日汇总（不含今天） */
    val weekUsage: AppWeeklySystemUsage
)
