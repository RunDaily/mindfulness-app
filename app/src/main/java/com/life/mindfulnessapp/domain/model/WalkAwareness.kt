package com.life.mindfulnessapp.domain.model

/**
 * 步行觉察：全局「路况锚点」形态阶梯。
 *
 * - L0 锚点：极小在场，几乎无字
 * - L1 轻语：短词轮换
 * - L2 醒神：整句短暂展开后收回
 */
enum class WalkAwarenessLevel {
    L0,
    L1,
    L2
}

object WalkAwarenessCopy {
    val L1_WORDS = listOf(
        "慢一点",
        "抬头",
        "脚下",
        "留意",
        "在路上"
    )

    val L2_SENTENCES = listOf(
        "边走边看，留意周围",
        "屏幕可以等等，路不能",
        "抬抬头，注意安全"
    )

    fun labelFor(level: WalkAwarenessLevel, seed: Int = 0): String = when (level) {
        WalkAwarenessLevel.L0 -> ""
        WalkAwarenessLevel.L1 -> L1_WORDS[Math.floorMod(seed, L1_WORDS.size)]
        WalkAwarenessLevel.L2 -> L2_SENTENCES[Math.floorMod(seed, L2_SENTENCES.size)]
    }
}
