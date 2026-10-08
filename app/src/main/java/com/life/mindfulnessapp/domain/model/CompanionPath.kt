package com.life.mindfulnessapp.domain.model

/**
 * 拦截门进入后的三条陪伴路径。
 * 决定左标形态、计时方向语义、锚提示 / 觉察问题文案。
 */
enum class CompanionPath {
    /** 写下意图：正计时 + App 图标轻呼吸 */
    INTENT,
    /** 随意浏览：倒计时 + 计时器图标；过程少打断 */
    BROWSE,
    /** 搜索：正计时 + 搜索图标静置 */
    SEARCH;

    companion object {
        fun resolve(
            intentKind: IntentKind?,
            purpose: String? = null,
            hasSessionLimit: Boolean = false,
        ): CompanionPath {
            when (intentKind) {
                IntentKind.SEARCH -> return SEARCH
                IntentKind.URGE, IntentKind.PURPOSELESS -> return BROWSE
                IntentKind.PURPOSEFUL, IntentKind.QUICK -> return INTENT
                null -> Unit
            }
            val t = purpose?.trim().orEmpty()
            if (t.isNotEmpty() &&
                (BrowseCasualIntent.isBrowseLike(t) || NamingKind.isUrgeNaming(t))
            ) {
                return BROWSE
            }
            // 门上选了单次时长、又无明确事务意图 → 按随意浏览
            if (hasSessionLimit && t.isEmpty()) return BROWSE
            return INTENT
        }
    }
}
