package com.life.mindfulnessapp.ui.features

/**
 * 时间流布局：条目落在真实时间点上，不靠「挤在一起」避让。
 * - 放大：逐条完整信息
 * - 缩小：按时间窗做信息密度合并（一条摘要承载多条记录）
 */
internal sealed class DayFlowItem {
    /** 轴上落点（真实开始时间） */
    abstract val anchorYPx: Float

    data class Detail(
        val session: DayAxisSession,
        override val anchorYPx: Float
    ) : DayFlowItem()

    /** 信息密度合并后的摘要 */
    data class Dense(
        val sessions: List<DayAxisSession>,
        override val anchorYPx: Float,
        val label: String,
        val detailLine: String?
    ) : DayFlowItem() {
        val totalDurationSeconds: Long get() = sessions.sumOf { it.durationSeconds }
    }
}

internal data class DayFlowLayout(
    val items: List<DayFlowItem>,
    val axisHeightPx: Float
)

internal fun layoutDayFlow(
    sessions: List<DayAxisSession>,
    dayStartMs: Long,
    dayMs: Long,
    hourHeightPx: Float,
    dense: Boolean,
    mergeWindowMs: Long
): DayFlowLayout {
    val axisHeightPx = hourHeightPx * 24f

    fun yForTime(timeMs: Long): Float {
        val t = ((timeMs - dayStartMs).toFloat() / dayMs.toFloat()).coerceIn(0f, 1f)
        return axisHeightPx * t
    }

    if (sessions.isEmpty()) {
        return DayFlowLayout(emptyList(), axisHeightPx)
    }

    val sorted = sessions.sortedBy { it.startTime }

    if (!dense) {
        return DayFlowLayout(
            items = sorted.map { DayFlowItem.Detail(it, yForTime(it.startTime)) },
            axisHeightPx = axisHeightPx
        )
    }

    val groups = mergeForDensity(sorted, mergeWindowMs)
    val items = groups.map { group ->
        val anchor = yForTime(group.first().startTime)
        if (group.size == 1) {
            DayFlowItem.Detail(group.first(), anchor)
        } else {
            val names = group.map { it.appName }.distinct()
            val namePart = when {
                names.size == 1 -> names.first()
                names.size == 2 -> names.joinToString("、")
                else -> "${names.take(2).joinToString("、")}等"
            }
            val intents = group.mapNotNull { it.purpose }.distinct()
            val intentPart = when {
                intents.isEmpty() -> null
                intents.size == 1 -> "意图 · ${intents.first()}"
                else -> "意图 · ${intents.size} 条"
            }
            val gate = group.count { it.isGateQuit }
            val enter = group.size - gate
            val stats = buildString {
                append("${group.size} 次")
                if (enter > 0 && gate > 0) append(" · 打开 $enter · 守住 $gate")
                else if (gate == group.size) append(" · 守住")
                append(" · ${DayTimelineViewModel.formatDuration(group.sumOf { it.durationSeconds })}")
            }
            DayFlowItem.Dense(
                sessions = group,
                anchorYPx = anchor,
                label = "$namePart · $stats",
                detailLine = intentPart
            )
        }
    }
    return DayFlowLayout(items, axisHeightPx)
}

/** 时间相交或间隔 ≤ window 时并入同一信息簇 */
private fun mergeForDensity(
    sessions: List<DayAxisSession>,
    windowMs: Long
): List<List<DayAxisSession>> {
    if (sessions.isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<DayAxisSession>>()
    var current = mutableListOf(sessions.first())
    var clusterEnd = effectiveEnd(sessions.first())

    for (i in 1 until sessions.size) {
        val s = sessions[i]
        if (s.startTime <= clusterEnd + windowMs) {
            current += s
            clusterEnd = maxOf(clusterEnd, effectiveEnd(s))
        } else {
            result += current
            current = mutableListOf(s)
            clusterEnd = effectiveEnd(s)
        }
    }
    result += current
    return result
}

private fun effectiveEnd(session: DayAxisSession): Long =
    if (session.endTime > session.startTime) session.endTime else session.startTime
