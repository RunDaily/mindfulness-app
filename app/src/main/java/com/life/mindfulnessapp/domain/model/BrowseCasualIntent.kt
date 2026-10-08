package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 系统路径「随意浏览」：无明确目的的刷看入口。
 * 可单独设日限额、可刷时段；手写「看看 / 刷刷」等归一到此意图，避免重复条目。
 *
 * 历史记录可能仍写「随便刷刷」；新写入一律 [LABEL]（= 随意浏览）。
 */
object BrowseCasualIntent {
    /** 产品规范名：展示 + 新写入 */
    const val LABEL = "随意浏览"

    /** 同 [LABEL]；显式强调对用户展示 */
    const val DISPLAY_LABEL = LABEL

    /** 历史入库键，仅识别兼容 */
    const val LEGACY_LABEL = "随便刷刷"

    const val ACTION_ID = "browse"

    /** 默认每日可刷上限（分钟）；0 表示不限 */
    const val DEFAULT_DAILY_LIMIT_MINUTES = 30

    fun isCanonical(label: String): Boolean {
        val t = label.trim()
        return t == LABEL || t == LEGACY_LABEL
    }

    /**
     * 是否属于「随意浏览」一类目的（应归一到 [LABEL]，勿再手写或加进普通常用）。
     */
    fun isBrowseLike(raw: String): Boolean {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return false
        if (t == LABEL || t == LEGACY_LABEL || t == "随便看看") return true
        if (EXACT.contains(t)) return true
        if (CONTAINS.any { t.contains(it) }) return true
        if (t.length <= 4 && SOFT.any { t.contains(it) } && !hasConcreteVerb(t)) return true
        return IntentGateProfiles.isBrowseLikeLabel(raw)
    }

    fun canonicalLabelOrNull(raw: String): String? =
        if (isBrowseLike(raw)) LABEL else null

    /** 到点 / 胶囊等对用户展示；浏览类统一显示 [DISPLAY_LABEL] */
    fun displayLabel(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return DISPLAY_LABEL
        return if (isBrowseLike(t)) DISPLAY_LABEL else t
    }

    private fun hasConcreteVerb(t: String): Boolean =
        t.contains("回") || t.contains("发") || t.contains("搜") ||
            t.contains("买") || t.contains("订") || t.contains("学")

    /** 旧口语 / 历史名仍识别，但产品名是「随意浏览」 */
    private val EXACT = setOf(
        "看看", "随便看看", "随便刷刷", "随便刷", "随便", "无聊",
        "刷一下", "刷刷", "刷一刷", "玩玩", "没事", "打发时间",
        "摸鱼", "消遣", "溜达", "逛逛", "闲逛", "瞎逛",
        "看一下", "看一眼", "点开看看", "打开看看", "无目的",
        "没有目的", "没目的", "不知道", "随意", "随意浏览"
    )

    private val CONTAINS = listOf(
        "随便看看", "随便刷", "就是看看", "无聊看看", "没什么事",
        "没有目的", "没目的", "打发时间", "刷刷视频", "刷刷短视频"
    )

    private val SOFT = listOf("看看", "刷刷", "刷一下", "逛逛")
}

/**
 * 「随意浏览」专属策略（按 App 存 [AppLimitEntity.browseCasualJson]）。
 *
 * @param dailyLimitMinutes 今日该意图可用时长；0 = 不限
 * @param windowsEnabled 为 true 时仅 [windows] 内允许选此意图进入
 * @param windows 可刷时段（语义同 [PeriodWindow]：允许窗口，非锁定）
 */
data class BrowseCasualPolicy(
    val dailyLimitMinutes: Int = BrowseCasualIntent.DEFAULT_DAILY_LIMIT_MINUTES,
    val windowsEnabled: Boolean = false,
    val windows: List<PeriodWindow> = emptyList()
) {
    fun effectiveDailyLimitMinutes(): Int = dailyLimitMinutes.coerceAtLeast(0)

    fun isAllowedNow(nowMinuteOfDay: Int, dayMaskBit: Int): Boolean {
        if (!windowsEnabled) return true
        if (windows.isEmpty()) return false
        return windows.any { w ->
            w.enabled &&
                (w.daysMask and dayMaskBit) != 0 &&
                minuteInWindow(nowMinuteOfDay, w.startMinute, w.endMinute)
        }
    }

    companion object {
        fun default(): BrowseCasualPolicy = BrowseCasualPolicy()

        private fun minuteInWindow(now: Int, start: Int, end: Int): Boolean {
            val (s, e) = PeriodWindow.normalizeRange(start, end)
            return when {
                s == e -> true
                s < e -> now in s until e
                else -> now >= s || now < e
            }
        }
    }
}

object BrowseCasualPolicyCodec {
    fun decode(json: String?): BrowseCasualPolicy {
        if (json.isNullOrBlank()) return BrowseCasualPolicy.default()
        return try {
            val o = JSONObject(json)
            val windowsRaw = o.opt("windows")
            val windowsJson = when (windowsRaw) {
                is JSONArray -> windowsRaw.toString()
                is String -> windowsRaw
                else -> ""
            }
            BrowseCasualPolicy(
                dailyLimitMinutes = o.optInt(
                    "dailyLimitMinutes",
                    BrowseCasualIntent.DEFAULT_DAILY_LIMIT_MINUTES
                ).coerceAtLeast(0),
                windowsEnabled = o.optBoolean("windowsEnabled", false),
                windows = PeriodWindowsCodec.decode(windowsJson)
            )
        } catch (_: Exception) {
            BrowseCasualPolicy.default()
        }
    }

    fun encode(policy: BrowseCasualPolicy): String {
        val windowsJson = PeriodWindowsCodec.encode(policy.windows)
        val o = JSONObject()
            .put("dailyLimitMinutes", policy.dailyLimitMinutes.coerceAtLeast(0))
            .put("windowsEnabled", policy.windowsEnabled)
        o.put(
            "windows",
            if (windowsJson.isBlank()) JSONArray() else JSONArray(windowsJson)
        )
        return o.toString()
    }
}
