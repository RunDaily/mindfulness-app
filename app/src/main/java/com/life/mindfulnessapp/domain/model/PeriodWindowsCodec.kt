package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** [PeriodWindow] 列表 ↔ JSON 字符串，存于 app_limits.periodWindowsJson */
object PeriodWindowsCodec {

    fun encode(windows: List<PeriodWindow>): String {
        if (windows.isEmpty()) return ""
        val arr = JSONArray()
        windows.forEach { w ->
            val (ns, ne) = PeriodWindow.normalizeRange(w.startMinute, w.endMinute)
            val normalized = if (ns == w.startMinute && ne == w.endMinute) w
            else w.copy(startMinute = ns, endMinute = ne)
            val o = JSONObject()
                .put("id", normalized.id)
                .put("s", normalized.startMinute)
                .put("e", normalized.endMinute)
                .put("d", normalized.daysMask)
                .put("on", normalized.enabled)
            val msg = normalized.message.trim().take(PeriodLockPolicy.MESSAGE_MAX_CHARS)
            if (msg.isNotEmpty()) o.put("msg", msg)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String?): List<PeriodWindow> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val s = o.optInt("s", -1)
                    val e = o.optInt("e", -1)
                    val d = o.optInt("d", PeriodDays.EVERY_DAY)
                    if (s in 0..1439 && e in 0..1439) {
                        val (ns, ne) = PeriodWindow.normalizeRange(s, e)
                        add(
                            PeriodWindow(
                                id = o.optString("id").ifBlank {
                                    java.util.UUID.randomUUID().toString()
                                },
                                startMinute = ns,
                                endMinute = ne,
                                daysMask = d and PeriodDays.EVERY_DAY,
                                enabled = o.optBoolean("on", true),
                                message = o.optString("msg", "")
                                    .take(PeriodLockPolicy.MESSAGE_MAX_CHARS)
                            )
                        )
                    }
                }
            }.let(::distinctByRange)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 去掉完全相同的起止 + 重复日，保留先出现的一段（含寄语与开关）。 */
    private fun distinctByRange(windows: List<PeriodWindow>): List<PeriodWindow> {
        val seen = mutableSetOf<Triple<Int, Int, Int>>()
        return windows.filter { w ->
            val (s, e) = PeriodWindow.normalizeRange(w.startMinute, w.endMinute)
            seen.add(Triple(s, e, w.daysMask and PeriodDays.EVERY_DAY))
        }
    }

    /**
     * 把旧版 App 级 [legacyCommitment] 填进尚无寄语的时段（一次性兼容）。
     * 若任一时段已有寄语，则不再覆盖。
     */
    fun withLegacyCommitment(
        windows: List<PeriodWindow>,
        legacyCommitment: String?
    ): List<PeriodWindow> {
        val legacy = legacyCommitment?.trim().orEmpty()
            .take(PeriodLockPolicy.MESSAGE_MAX_CHARS)
        if (legacy.isEmpty() || windows.isEmpty()) return windows
        if (windows.any { it.message.isNotBlank() }) return windows
        return windows.map { it.copy(message = legacy) }
    }

    fun summaryLabel(windows: List<PeriodWindow>): String {
        val enabled = windows.filter { it.enabled }
        if (windows.isEmpty()) return "未设置时段"
        if (enabled.isEmpty()) return "已设 ${windows.size} 段 · 均已关闭"
        val first = enabled.first()
        return if (enabled.size == 1) {
            "${first.label()} · ${first.daysLabel()}"
        } else {
            "${first.label()} 等 ${enabled.size} 段开启"
        }
    }
}
