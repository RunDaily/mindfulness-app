package com.life.mindfulnessapp.domain.model

import kotlin.math.abs

/**
 * 首页时间轴的展示层节点。
 * 在 [TimelineEvent] 之上做视觉折叠：连续门外停下可合并（同 App 或跨 App 连点离开）。
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
     * 合并簇：短时连续门外停下（可同 App，也可跨 App）。
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

        /** 主标题，如「离开了 · 3次」「门外停下 · 5次」 */
        val titleLabel: String
            get() {
                if (isMixedApps) return "门外停下 · ${count}次"
                val family = events.first().mergeFamily()
                val base = when (family) {
                    MergeFamily.GATE_QUIT -> {
                        val toOwn = events.count { it.isGateDismissToOwnApp }
                        when {
                            toOwn == events.size -> "到了心锚"
                            toOwn == 0 -> "离开了"
                            else -> "门外停下"
                        }
                    }
                    MergeFamily.ABNORMAL, MergeFamily.NONE -> "短暂记录"
                }
                return "$base · ${count}次"
            }

        fun containsRecordId(recordId: Long): Boolean =
            events.any { it.recordId == recordId }
    }
}

/** 合并族：只有同族才视觉合并，避免「门外停下」和「息屏结束」糊成一条 */
enum class MergeFamily {
    GATE_QUIT,
    ABNORMAL,
    NONE
}

/** 同 App 门外停下：允许较长间隔仍合并 */
private const val MERGE_SAME_APP_GAP_MS = 45L * 60L * 1000L

/** 跨 App 连点离开：更短窗口，避免把半天的离开糊成一条 */
private const val MERGE_CROSS_APP_GAP_MS = 20L * 60L * 1000L

fun TimelineEvent.UsageEvent.mergeFamily(): MergeFamily = when {
    isGateQuit -> MergeFamily.GATE_QUIT
    else -> MergeFamily.NONE
}

/**
 * 是否可作为合并候选：仅连续门外停下。
 */
fun TimelineEvent.UsageEvent.isMergeCandidate(): Boolean {
    if (isOngoing || isLimitReached || isSeed) return false
    if (!note.isNullOrBlank()) return false
    return isGateQuit
}

private fun canMergeAdjacent(
    newer: TimelineEvent.UsageEvent,
    older: TimelineEvent.UsageEvent
): Boolean {
    if (!newer.isMergeCandidate() || !older.isMergeCandidate()) return false
    if (newer.mergeFamily() != older.mergeFamily()) return false
    if (newer.mergeFamily() == MergeFamily.NONE) return false
    val gap = abs(newer.startTime - older.startTime)
    return if (newer.packageName == older.packageName) {
        gap <= MERGE_SAME_APP_GAP_MS
    } else {
        // 跨 App：连串「离开了」收成「门外停下 · N次」
        gap <= MERGE_CROSS_APP_GAP_MS
    }
}

/**
 * 将已按时间倒序的 [TimelineEvent] 列表折叠为展示层节点。
 * 仅合并连续 ≥2 条的候选；其余保持单条。
 */
fun collapseTimelineForDisplay(
    events: List<TimelineEvent>
): List<TimelineDisplayItem> {
    if (events.isEmpty()) return emptyList()
    val result = ArrayList<TimelineDisplayItem>(events.size)
    var i = 0
    while (i < events.size) {
        val current = events[i]
        val usage = current as? TimelineEvent.UsageEvent
        if (usage == null || !usage.isMergeCandidate()) {
            result += TimelineDisplayItem.Single(current)
            i++
            continue
        }
        val cluster = mutableListOf(usage)
        var j = i + 1
        while (j < events.size) {
            val next = events[j] as? TimelineEvent.UsageEvent ?: break
            if (!canMergeAdjacent(cluster.last(), next)) break
            cluster += next
            j++
        }
        if (cluster.size >= 2) {
            val mixed = cluster.distinctBy { it.packageName }.size > 1
            result += TimelineDisplayItem.MergedCluster(
                packageName = usage.packageName,
                appName = if (mixed) "多个应用" else usage.appName,
                events = cluster.toList()
            )
            i = j
        } else {
            result += TimelineDisplayItem.Single(current)
            i++
        }
    }
    return result
}
