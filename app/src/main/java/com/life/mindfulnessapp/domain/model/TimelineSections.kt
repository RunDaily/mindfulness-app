package com.life.mindfulnessapp.domain.model

/**
 * 首页 / 历史列表的分段展示节点：时段头、间隔、事件。
 * 在 [collapseTimelineForDisplay]（现为逐条）之上再组。
 */
sealed class TimelineSectionItem {
    abstract val key: String

    /**
     * @param name 口语段名（上午 / 下午…）
     * @param range 具体钟点范围（9:00–12:00）
     */
    data class PeriodHeader(
        val name: String,
        val range: String
    ) : TimelineSectionItem() {
        override val key: String get() = "period_$name"

        /** 兼容旧调用：段名 */
        val label: String get() = name
    }

    /** 上方（更新）事件与下方（更早）事件之间的空档；[afterEventKey] 保证 Lazy 稳定 key */
    data class Gap(
        val seconds: Long,
        val afterEventKey: String
    ) : TimelineSectionItem() {
        override val key: String get() = "gap_${afterEventKey}_$seconds"
    }

    data class Event(val item: TimelineDisplayItem) : TimelineSectionItem() {
        override val key: String get() = item.key
    }
}

/** 按时段头切分，供 LazyColumn stickyHeader 使用 */
data class TimelinePeriodGroup(
    val name: String,
    val range: String,
    val rows: List<TimelineSectionItem>,
    val stats: PeriodSectionStats = PeriodSectionStats.Empty
)

/** 某时段内的进入 / 门外离开 / 使用时长（按 App） */
data class PeriodAppStat(
    val packageName: String,
    val appName: String,
    val enters: Int,
    val leaves: Int,
    val durationSeconds: Long
)

data class PeriodSectionStats(
    val apps: List<PeriodAppStat>,
    val enterCount: Int,
    val leaveCount: Int,
    val durationSeconds: Long
) {
    val hasSignal: Boolean
        get() = enterCount > 0 || leaveCount > 0 || durationSeconds > 0L

    companion object {
        val Empty = PeriodSectionStats(
            apps = emptyList(),
            enterCount = 0,
            leaveCount = 0,
            durationSeconds = 0L
        )
    }
}

fun groupTimelineSections(items: List<TimelineSectionItem>): List<TimelinePeriodGroup> {
    if (items.isEmpty()) return emptyList()
    val groups = ArrayList<TimelinePeriodGroup>(4)
    var name: String? = null
    var range = ""
    var rows = ArrayList<TimelineSectionItem>(8)
    fun flush() {
        val n = name ?: return
        groups += TimelinePeriodGroup(
            name = n,
            range = range,
            rows = rows,
            stats = computePeriodStats(rows)
        )
        rows = ArrayList(8)
    }
    for (item in items) {
        when (item) {
            is TimelineSectionItem.PeriodHeader -> {
                flush()
                name = item.name
                range = item.range
            }
            else -> {
                if (name == null) {
                    name = ""
                    range = ""
                }
                rows += item
            }
        }
    }
    flush()
    return groups
}

fun computePeriodStats(rows: List<TimelineSectionItem>): PeriodSectionStats {
    val usages = ArrayList<TimelineEvent.UsageEvent>(rows.size)
    for (row in rows) {
        val eventItem = row as? TimelineSectionItem.Event ?: continue
        when (val item = eventItem.item) {
            is TimelineDisplayItem.Single ->
                (item.event as? TimelineEvent.UsageEvent)?.let { usages += it }
            is TimelineDisplayItem.MergedCluster ->
                usages.addAll(item.events)
        }
    }
    if (usages.isEmpty()) return PeriodSectionStats.Empty

    val perApp = usages
        .filter { !it.isSeed }
        .groupBy { it.packageName }
        .map { (pkg, events) ->
            val enters = events.count { !it.isGateQuit && !it.isPositiveExit }
            val leaves = events.count { it.isGateQuit || it.isPositiveExit }
            val duration = events.filter { !it.isGateQuit && !it.isPositiveExit }
                .sumOf { it.durationSeconds.coerceAtLeast(0L) }
            PeriodAppStat(
                packageName = pkg,
                appName = events.first().appName,
                enters = enters,
                leaves = leaves,
                durationSeconds = duration
            )
        }
        .sortedWith(
            compareByDescending<PeriodAppStat> { it.enters + it.leaves }
                .thenByDescending { it.durationSeconds }
        )

    return PeriodSectionStats(
        apps = perApp,
        enterCount = perApp.sumOf { it.enters },
        leaveCount = perApp.sumOf { it.leaves },
        durationSeconds = perApp.sumOf { it.durationSeconds }
    )
}

/**
 * 一日时段（与用户口语对齐；列表倒序时从上到下自然穿越）。
 * 深夜 0–5 · 早晨 5–9 · 上午 9–12 · 下午 12–18 · 晚上 18–24
 */
fun dayPeriodLabel(timeMs: Long): String {
    val hour = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        .get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0 until 5 -> "深夜"
        in 5 until 9 -> "早晨"
        in 9 until 12 -> "上午"
        in 12 until 18 -> "下午"
        else -> "晚上"
    }
}

/** 与 [dayPeriodLabel] 对应的钟点范围文案 */
fun dayPeriodRange(timeMs: Long): String = when (dayPeriodLabel(timeMs)) {
    "深夜" -> "0:00–5:00"
    "早晨" -> "5:00–9:00"
    "上午" -> "9:00–12:00"
    "下午" -> "12:00–18:00"
    else -> "18:00–24:00"
}

/**
 * 仅「长空档」才插入额外留白：≥30 分钟。
 * 更短的间隙交给卡片间距 + 时间戳；跨时段交给时段头，不再叠一层空档。
 */
private const val MIN_GAP_SHOW_SECONDS = 30L * 60L

/**
 * 将倒序展示节点编成：时段头 + 事件 +（可选）长空档留白。
 * [displayItems] 须已按时间倒序（新→旧）。
 */
fun buildTimelineSections(
    displayItems: List<TimelineDisplayItem>
): List<TimelineSectionItem> {
    if (displayItems.isEmpty()) return emptyList()
    val out = ArrayList<TimelineSectionItem>(displayItems.size * 2)
    var lastPeriod: String? = null

    displayItems.forEachIndexed { index, item ->
        val period = dayPeriodLabel(item.timeMs)
        if (period != lastPeriod) {
            out += TimelineSectionItem.PeriodHeader(
                name = period,
                range = dayPeriodRange(item.timeMs)
            )
            lastPeriod = period
        }
        out += TimelineSectionItem.Event(item)

        val next = displayItems.getOrNull(index + 1) ?: return@forEachIndexed
        // 下一条会换时段头时，节奏已由时段头承担，不再插空档
        if (dayPeriodLabel(next.timeMs) != period) return@forEachIndexed
        val gapSec = intervalSecondsBetween(newer = item, older = next)
        if (gapSec != null && gapSec >= MIN_GAP_SHOW_SECONDS) {
            out += TimelineSectionItem.Gap(seconds = gapSec, afterEventKey = item.key)
        }
    }
    return out
}

/**
 * 上方条目（较新）开始，相对下方条目（较旧）结束，中间空了多久。
 */
fun intervalSecondsBetween(
    newer: TimelineDisplayItem,
    older: TimelineDisplayItem
): Long? {
    val newerStart = newer.timeMs
    val olderEnd = effectiveEndMs(older) ?: return null
    val gapMs = newerStart - olderEnd
    if (gapMs <= 0L) return null
    return gapMs / 1000L
}

private fun effectiveEndMs(item: TimelineDisplayItem): Long? = when (item) {
    is TimelineDisplayItem.Single -> when (val e = item.event) {
        is TimelineEvent.UsageEvent -> when {
            e.isOngoing -> System.currentTimeMillis()
            e.isGateQuit || e.isSeed -> e.startTime
            e.endTime > 0L -> e.endTime
            e.durationSeconds > 0L -> e.startTime + e.durationSeconds * 1000L
            else -> e.startTime
        }
        is TimelineEvent.LimitResetEvent -> e.resetTime
    }
    is TimelineDisplayItem.MergedCluster -> {
        // 簇内最旧一条的结束（倒序列表里 last）
        val oldest = item.events.lastOrNull() ?: return null
        if (oldest.isGateQuit) oldest.startTime else {
            if (oldest.endTime > 0L) oldest.endTime
            else oldest.startTime + oldest.durationSeconds * 1000L
        }
    }
}
