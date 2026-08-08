package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** [PeriodWindow] 列表 ↔ JSON 字符串，存于 app_limits.periodWindowsJson */
object PeriodWindowsCodec {

    fun encode(windows: List<PeriodWindow>): String {
        if (windows.isEmpty()) return ""
        val arr = JSONArray()
        windows.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("s", w.startMinute)
                    .put("e", w.endMinute)
                    .put("d", w.daysMask)
                    .put("on", w.enabled)
            )
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
                        add(
                            PeriodWindow(
                                id = o.optString("id").ifBlank {
                                    java.util.UUID.randomUUID().toString()
                                },
                                startMinute = s,
                                endMinute = e,
                                daysMask = d and PeriodDays.EVERY_DAY,
                                enabled = o.optBoolean("on", true)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
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
