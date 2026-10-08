package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 加入监控前 7 个完整自然日的用量快照（逐日时长 + 打开）。
 * 系锚瞬间冻结写入本地；走势灰柱只读此快照，不再依赖会过期的系统 UsageStats。
 */
data class PreJoinDaySnap(
    val dayStartMs: Long,
    val totalSeconds: Long,
    val opens: Int
)

object PreJoinUsageSnapshot {
    fun encode(joinDayStartMs: Long, days: List<PreJoinDaySnap>, capturedAt: Long = System.currentTimeMillis()): String {
        val arr = JSONArray()
        days.forEach { d ->
            arr.put(
                JSONObject()
                    .put("d", d.dayStartMs)
                    .put("s", d.totalSeconds.coerceAtLeast(0L))
                    .put("o", d.opens.coerceAtLeast(0))
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("join", joinDayStartMs)
            .put("at", capturedAt)
            .put("days", arr)
            .toString()
    }

    fun decode(json: String): List<PreJoinDaySnap> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("days") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        PreJoinDaySnap(
                            dayStartMs = o.optLong("d", 0L),
                            totalSeconds = o.optLong("s", 0L).coerceAtLeast(0L),
                            opens = o.optInt("o", 0).coerceAtLeast(0)
                        )
                    )
                }
            }.filter { it.dayStartMs > 0L }
                .sortedBy { it.dayStartMs }
        }.getOrDefault(emptyList())
    }

    fun hasSnapshot(json: String): Boolean =
        json.isNotBlank() && runCatching {
            JSONObject(json).optLong("at", 0L) > 0L
        }.getOrDefault(false)

    fun byDayStart(json: String): Map<Long, PreJoinDaySnap> =
        decode(json).associateBy { it.dayStartMs }

    fun avgOpens(json: String): Int? {
        val days = decode(json)
        if (days.isEmpty()) return null
        return (days.sumOf { it.opens.toDouble() } / days.size).toInt()
    }

    fun totalSeconds(json: String): Long = decode(json).sumOf { it.totalSeconds }
}
