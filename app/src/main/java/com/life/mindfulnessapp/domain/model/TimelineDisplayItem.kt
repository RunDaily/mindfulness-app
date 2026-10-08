package com.life.mindfulnessapp.domain.model

import kotlin.math.abs

/**
 * 首页时间轴的展示层节点。
 * 在 [TimelineEvent] 之上做视觉折叠：仅合并「同 App、短时守住后又返回再守住」的连续条目。
 */
sealed class TimelineDisplayItem {
    abstract val timeMs: Long
    abstract val key: String

    data class Single(
        val event: TimelineEvent
    ) : TimelineDisplayItem() {
        override val timeMs: Long get() = event.timeMs
        override val key: String
            get() = when (event) {
                is TimelineEvent.UsageEvent -> "usage_${event.recordId}"
                is TimelineEvent.LimitResetEvent -> "reset_${event.resetId}"
            }
    }

    /**
     * 合并簇：同 App 短时连续守住离开（暂停后又返回再守住）。
     * [events] 保持与时间轴一致的倒序（最新在前）。
     */
    data class MergedCluster(
        val packageName: String,
        val appName: String,
        val events: List<TimelineEvent.UsageEvent>
    ) : TimelineDisplayItem() {
        init {
            require(events.size >= 2) { "MergedCluster needs at least 2 events" }
        }

        override val timeMs: Long get() = events.first().timeMs
        override val key: String
            get() = "merged_" + events.joinToString("_") { it.recordId.toString() }

        val count: Int get() = events.size
        val totalDurationSeconds: Long get() = events.sumOf { it.durationSeconds }

        val isMixedApps: Boolean
            get() = events.distinctBy { it.packageName }.size > 1

        /** 主标题，如「守住离开 · 3次」「守住 · 到了心锚 · 2次」 */
        val titleLabel: String
            get() {
                val toOwn = events.count { it.isGateDismissToOwnApp }
                val base = when {
                    toOwn == events.size -> "守住 · 到了心锚"
                    else -> "守住离开"
                }
                return "$base · ${count}次"
            }

        fun containsRecordId(recordId: Long): Boolean =
            events.any { it.recordId == recordId }
    }
}

/**
 * 将已按时间倒序的 [TimelineEvent] 列表转为展示层节点。
 * 守住离开视为有价值的事实，逐条展示，不再合并折叠。
 */
fun collapseTimelineForDisplay(
    events: List<TimelineEvent>
): List<TimelineDisplayItem> =
    events.map { TimelineDisplayItem.Single(it) }

// ── 以下保留供兼容：合并逻辑已停用 ──────────────────────────────────────────

/**
 * 同 App「暂停后又返回再守住」的相邻间隔上限（历史合并用，现已停用）。
 */
@Suppress("unused")
private const val MERGE_RETURN_GAP_MS = 3L * 60L * 1000L

/**
 * 是否可作为合并候选：仅守住离开，且无备注。
 */
fun TimelineEvent.UsageEvent.isMergeCandidate(): Boolean {
    if (isOngoing || isLimitReached || isSeed) return false
    if (!note.isNullOrBlank()) return false
    return isGateQuit
}

/**
 * 仅同 App、短间隔的连续守住可合并（已停用，保留签名）。
 */
@Suppress("unused")
private fun canMergeAdjacent(
    newer: TimelineEvent.UsageEvent,
    older: TimelineEvent.UsageEvent
): Boolean {
    if (!newer.isMergeCandidate() || !older.isMergeCandidate()) return false
    if (newer.packageName != older.packageName) return false
    return abs(newer.startTime - older.startTime) <= MERGE_RETURN_GAP_MS
}

/**
 * 守住离开按「同一 App」发生先后编号（早→晚 = 第1次…）。
 * 跨 App 互不影响；时间轴倒序展示时，同 App 越新数字越大。
 */
data class HeldAwayOrdinalIndex(
    val ordinalByRecordId: Map<Long, Int> = emptyMap(),
    val countByPackage: Map<String, Int> = emptyMap()
) {
    /** 该 App 当日守住 ≥2 次才返回序位 */
    fun ordinalFor(recordId: Long, packageName: String): Int? {
        if ((countByPackage[packageName] ?: 0) < 2) return null
        return ordinalByRecordId[recordId]
    }

    /** 同 App 合并簇的序位区间；跨 App 簇不展示 */
    fun rangeLabelFor(events: List<TimelineEvent.UsageEvent>): String? {
        if (events.isEmpty()) return null
        val pkgs = events.map { it.packageName }.distinct()
        if (pkgs.size != 1) return null
        val pkg = pkgs.first()
        if ((countByPackage[pkg] ?: 0) < 2) return null
        return heldAwayOrdinalRangeLabel(events.mapNotNull { ordinalByRecordId[it.recordId] })
    }
}

fun buildHeldAwayOrdinalIndex(events: List<TimelineEvent>): HeldAwayOrdinalIndex {
    val quits = events.asSequence()
        .filterIsInstance<TimelineEvent.UsageEvent>()
        .filter { it.isGateQuit && !it.isSeed }
        .toList()
    if (quits.isEmpty()) return HeldAwayOrdinalIndex()
    val ordinals = LinkedHashMap<Long, Int>()
    val counts = HashMap<String, Int>()
    quits.groupBy { it.packageName }.forEach { (pkg, list) ->
        val sorted = list.sortedWith(compareBy({ it.startTime }, { it.recordId }))
        counts[pkg] = sorted.size
        sorted.forEachIndexed { index, event -> ordinals[event.recordId] = index + 1 }
    }
    return HeldAwayOrdinalIndex(ordinalByRecordId = ordinals, countByPackage = counts)
}

/** 合并簇：连续守住的序位区间文案，如「第3次」「第3–5次」 */
fun heldAwayOrdinalRangeLabel(ordinals: Collection<Int>): String? {
    if (ordinals.isEmpty()) return null
    val sorted = ordinals.sorted()
    val first = sorted.first()
    val last = sorted.last()
    return if (first == last) "第${first}次" else "第${first}–${last}次"
}
