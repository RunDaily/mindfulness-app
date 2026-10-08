package com.life.mindfulnessapp.domain.model

/**
 * 主动离开（页上「先不进去了」）仪式口径。
 *
 * - 门上副句固定，不进轮换池。
 * - 余韵小池轮换；短时不重复。
 * - 同 App 冷却内只静默离场，不播余韵 / 勋章。
 * - Home / 多任务不是离开路径（拉回门口），不走本仪式。
 *
 * 草图：`.design/leave-ritual-sketch.html`
 */
object LeaveRitual {
    /** 门上离开入口副句 · 始终固定 */
    const val GATE_SUBTITLE = "这一次，也很好"

    /** 同 App 仪式冷却（与 OverlayManager.dismissCeremonyCooldownMs 对齐） */
    const val COOLDOWN_MS = 2 * 60 * 1000L

    /** 日常门内呼气收束 */
    const val EXHALE_MS = 520

    /** 冷却内仅极短淡出 */
    const val COOLDOWN_FADE_MS = 180

    /**
     * 离开余韵小池（门外轻条或收束末拍）。
     * 短、静、不评价；不含鸡汤与连胜。
     */
    val RESIDUAL_POOL: List<String> = listOf(
        "这一次，也很好",
        "守住了",
        "先到这里",
        "也好"
    )

    /**
     * 从池中取一句；避开 [avoid]（短时去重）。
     */
    fun nextResidual(avoid: String? = null, seed: Int = 0): String {
        val pool = RESIDUAL_POOL
        if (pool.isEmpty()) return GATE_SUBTITLE
        val filtered = avoid?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { a -> pool.filter { it != a } }
            ?.takeIf { it.isNotEmpty() }
            ?: pool
        val idx = ((seed % filtered.size) + filtered.size) % filtered.size
        return filtered[idx]
    }

    /** 今日克制次数是否里程碑（第 5 / 每满 10） */
    fun isMilestone(displayCount: Int): Boolean =
        displayCount == 5 || (displayCount >= 10 && displayCount % 10 == 0)
}
