package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条常用意图。
 * @param showOnGate 是否展示在拦截页写意图区（中间组件）
 * @param defaultMinutes 点选后「设时长」时的预填；0 = 无默认（不限）
 * @param deepLinkId 可选；绑定 [com.life.mindfulnessapp.data.deeplink.AppDeepLinkEntry.id]，
 *   进入后按 id 直达对应页（文案可改，不依赖 label 碰巧相等）
 */
data class CommonIntentItem(
    val label: String,
    val showOnGate: Boolean = true,
    val defaultMinutes: Int = 0,
    val deepLinkId: String? = null,
)

/**
 * 拦截页「常用意图」编解码。
 * 存在 [com.life.mindfulnessapp.data.db.entity.AppLimitEntity.quickIntentsJson]。
 *
 * 兼容旧版纯字符串数组：`["回消息","逛商城"]` → 默认全部 [CommonIntentItem.showOnGate]=true。
 * 新版：`[{"label":"回消息","showOnGate":true,"defaultMinutes":5,"deepLinkId":"xhs_message"},...]`
 */
object CommonIntentsCodec {
    const val MAX_ITEMS = 12
    const val MAX_LABEL = 24
    /** 拦截页中间组件最多露出几条（不含系统「随意浏览」） */
    const val MAX_GATE_VISIBLE = 6
    /** 单条默认时长上限（与 [SessionLimitPolicy.MAX_SESSION_MINUTES] 对齐） */
    const val MAX_DEFAULT_MINUTES = SessionLimitPolicy.MAX_SESSION_MINUTES

    fun sanitizeDefaultMinutes(raw: Int): Int =
        raw.coerceIn(0, MAX_DEFAULT_MINUTES)

    fun sanitizeDeepLinkId(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    fun decode(json: String?): List<CommonIntentItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    when (val raw = arr.opt(i)) {
                        is String -> {
                            val label = raw.trim().take(MAX_LABEL)
                            if (label.isNotEmpty()) {
                                add(CommonIntentItem(label, showOnGate = true, defaultMinutes = 0))
                            }
                        }
                        is JSONObject -> {
                            val label = raw.optString("label", "").trim().take(MAX_LABEL)
                            if (label.isNotEmpty()) {
                                add(
                                    CommonIntentItem(
                                        label = label,
                                        showOnGate = raw.optBoolean("showOnGate", true),
                                        defaultMinutes = sanitizeDefaultMinutes(
                                            raw.optInt("defaultMinutes", 0)
                                        ),
                                        deepLinkId = if (raw.has("deepLinkId")) {
                                            sanitizeDeepLinkId(raw.optString("deepLinkId"))
                                        } else {
                                            null
                                        }
                                    )
                                )
                            }
                        }
                    }
                    if (size >= MAX_ITEMS) break
                }
            }.distinctBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(items: List<CommonIntentItem>): String {
        val cleaned = items
            .map {
                it.copy(
                    label = it.label.trim().take(MAX_LABEL),
                    defaultMinutes = sanitizeDefaultMinutes(it.defaultMinutes),
                    deepLinkId = sanitizeDeepLinkId(it.deepLinkId)
                )
            }
            .filter { it.label.isNotEmpty() }
            .distinctBy { it.label.lowercase() }
            .take(MAX_ITEMS)
        if (cleaned.isEmpty()) return ""
        var gateUsed = 0
        val arr = JSONArray()
        cleaned.forEach { item ->
            val onGate = item.showOnGate && gateUsed < MAX_GATE_VISIBLE
            if (onGate) gateUsed++
            val obj = JSONObject()
                .put("label", item.label)
                .put("showOnGate", onGate)
                .put("defaultMinutes", item.defaultMinutes)
            item.deepLinkId?.let { obj.put("deepLinkId", it) }
            arr.put(obj)
        }
        return arr.toString()
    }

    /** 兼容旧调用：只取文案列表 */
    fun encodeLabels(labels: List<String>): String =
        encode(labels.map { CommonIntentItem(it, showOnGate = true, defaultMinutes = 0) })

    fun normalizeLabel(raw: String): String =
        raw.trim().take(MAX_LABEL)

    /** 钉在拦截页中间组件上的条目（有序） */
    fun gateItems(items: List<CommonIntentItem>): List<CommonIntentItem> =
        items.filter { it.showOnGate }.take(MAX_GATE_VISIBLE)

    /** 钉在拦截页中间组件上的文案（有序） */
    fun gateLabels(items: List<CommonIntentItem>): List<String> =
        gateItems(items).map { it.label }

    fun countOnGate(items: List<CommonIntentItem>): Int =
        items.count { it.showOnGate }.coerceAtMost(MAX_GATE_VISIBLE)

    fun canPinMore(items: List<CommonIntentItem>): Boolean =
        countOnGate(items) < MAX_GATE_VISIBLE
}
