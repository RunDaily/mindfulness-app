package com.life.mindfulnessapp.data.analytics

import android.content.Context
import com.life.mindfulnessapp.data.network.AnalyticsEventDto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 埋点事件磁盘队列：进程被杀 / 无网时保留，下次启动继续上报。
 * 上限 [MAX_EVENTS]，超限丢最旧。
 */
class AnalyticsEventStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun load(): List<AnalyticsEventDto> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    if (name.isEmpty()) continue
                    val propsObj = o.optJSONObject("props")
                    val props = buildMap {
                        if (propsObj != null) {
                            propsObj.keys().forEach { key ->
                                put(key, propsObj.optString(key, ""))
                            }
                        }
                    }
                    add(AnalyticsEventDto(name = name.take(80), props = props))
                }
            }.takeLast(MAX_EVENTS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(events: List<AnalyticsEventDto>) {
        try {
            val trimmed = events.takeLast(MAX_EVENTS)
            if (trimmed.isEmpty()) {
                if (file.exists()) file.delete()
                return
            }
            val arr = JSONArray()
            trimmed.forEach { e ->
                val props = JSONObject()
                e.props.forEach { (k, v) -> props.put(k, v) }
                arr.put(
                    JSONObject()
                        .put("name", e.name)
                        .put("props", props)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
            // 磁盘失败不影响主流程
        }
    }

    companion object {
        private const val FILE_NAME = "ha_analytics_queue.json"
        const val MAX_EVENTS = 200
    }
}
