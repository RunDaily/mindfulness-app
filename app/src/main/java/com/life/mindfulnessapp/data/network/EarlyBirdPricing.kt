package com.life.mindfulnessapp.data.network

/**
 * 会员码赠送期内的早鸟分档价（分）。
 * 本地兜底与页内「档位示意」共用，避免 UI 写死与仓库不一致。
 */
object EarlyBirdPricing {

    data class TierStep(
        val id: String,
        val label: String,
        val yearlyFen: Int,
        val lifetimeFen: Int
    )

    val STEPS: List<TierStep> = listOf(
        TierStep("super", "超早鸟", 2800, 7800),
        TierStep("early", "早鸟", 3600, 9800),
        TierStep("late", "临近结束", 4200, 11800)
    )

    fun listFen(plan: VipPlan): Int = when (plan) {
        VipPlan.MONTHLY -> 1200
        VipPlan.QUARTERLY -> 2800
        VipPlan.YEARLY -> 4800
        VipPlan.LIFETIME -> 12800
    }

    fun fenFor(plan: VipPlan, tierId: String?): Int {
        val step = STEPS.firstOrNull { it.id == tierId }
        val fen = when (plan) {
            VipPlan.YEARLY -> step?.yearlyFen
            VipPlan.LIFETIME -> step?.lifetimeFen
            else -> null
        } ?: return listFen(plan)
        return fen.coerceAtMost(listFen(plan))
    }

    fun formatYuan(fen: Int): String {
        val yuan = fen / 100.0
        return if (fen % 100 == 0) "¥${fen / 100}" else "¥${"%.2f".format(yuan)}"
    }

    /** 如：超早鸟 ¥78 → 早鸟 ¥98 → 临近结束 ¥118 → 原价 ¥128 */
    fun ladderLine(plan: VipPlan): String {
        val steps = STEPS.joinToString(" → ") { "${it.label} ${formatYuan(fenFor(plan, it.id))}" }
        return "$steps → 原价 ${formatYuan(listFen(plan))}"
    }
}
