package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel

/**
 * 会话觉察模式：由进门命名决定，贯穿进行中提醒与结束对照。
 *
 * - [TASK]：有实际要做的事 → 进行中问「还在做吗」，对照「有没有跑偏」
 * - [URGE]：诚实冲动 / 无事务意图 → 进行中问「还想待着吗」，对照「待得有没有数」
 *
 * 两条线共用「到场」伦理：成功是看见，不是做对。
 */
enum class SessionAwarenessMode {
    TASK,
    URGE;

    companion object {
        fun from(intentKind: IntentKind?, purpose: String? = null): SessionAwarenessMode {
            when (intentKind) {
                IntentKind.URGE, IntentKind.PURPOSELESS -> return URGE
                IntentKind.PURPOSEFUL, IntentKind.QUICK, IntentKind.SEARCH -> return TASK
                null -> Unit
            }
            return if (NamingKind.isUrgeNaming(purpose)) URGE else TASK
        }
    }
}

/**
 * 进门命名分类：事务意图 vs 诚实冲动。
 * 与门上黄色芯片、BrowseCasual 对齐。
 */
object NamingKind {
    fun isUrgeNaming(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return false
        if (BrowseCasualIntent.isBrowseLike(t) || BrowseCasualIntent.isCanonical(t)) return true
        return URGE_PATTERN.containsMatchIn(t)
    }

    fun resolveIntentKind(purpose: String): IntentKind =
        if (isUrgeNaming(purpose)) IntentKind.URGE else IntentKind.PURPOSEFUL

    private val URGE_PATTERN = Regex(
        "想刷|顺手|无聊|看一眼|没什么|随便|冲动|刷一会|就是想刷|想刷一会|边走边|有点空|心里空|下意识|手已经|手在点"
    )
}

/**
 * 门 → 进行中 → SoftExit → 结束对照：按 [SessionAwarenessMode] 分支的文案。
 *
 * TASK 对照的是「命名的事有没有被守住」；
 * URGE 对照的是「待得有没有数」——没有「事」可跑偏，就不问跑偏。
 */
object SessionAwarenessCopy {

    /** 托住拍：命名上方小标签 */
    fun holdBadge(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "这件事"
        SessionAwarenessMode.URGE -> "看见了"
    }

    fun holdHint(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "拖圆环选择多久 · 事已被托住"
        SessionAwarenessMode.URGE -> "拖圆环托住这一次 · 看见了就可以进"
    }

    fun runwayPrefix(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "专注于此"
        SessionAwarenessMode.URGE -> "带着看见"
    }

    /**
     * 锚提示：短暂看见，不可点。
     * 意图回声意图；搜索轻问找没找到；随意浏览临期报剩余。
     */
    fun anchorHint(
        path: CompanionPath,
        naming: String? = null,
        remainSec: Long = 0L,
    ): String {
        val name = shortenNaming(naming)
        return when (path) {
            CompanionPath.INTENT ->
                if (name.isNotEmpty()) "你刚才说：$name" else "带着意图进来的"
            CompanionPath.SEARCH -> "搜到了吗？"
            CompanionPath.BROWSE -> {
                val m = (remainSec.coerceAtLeast(0L) + 59L) / 60L
                if (m <= 1L) "还剩 1 分钟" else "还剩 ${m} 分钟"
            }
        }
    }

    fun midCheckPrompt(
        mode: SessionAwarenessMode,
        naming: String? = null,
        variant: Int = 0,
        path: CompanionPath? = null,
    ): String {
        when (path) {
            CompanionPath.SEARCH -> return when (variant and 1) {
                0 -> "你还在搜吗？"
                else -> "还在找答案吗？"
            }
            CompanionPath.BROWSE -> return when (variant and 1) {
                0 -> "这轮刷够了吗？"
                else -> "还想接着刷吗？"
            }
            CompanionPath.INTENT, null -> Unit
        }
        val name = naming?.trim().orEmpty().take(14)
        return when (mode) {
            SessionAwarenessMode.TASK -> when (variant and 1) {
                0 -> if (name.isNotEmpty()) "还在做「$name」吗？" else "还在做这件事吗？"
                else -> if (name.isNotEmpty()) "还是「$name」吗？" else "还在这件事上吗？"
            }
            SessionAwarenessMode.URGE -> when (variant and 1) {
                0 -> "还想待着吗？"
                else -> "还是刚才那一下吗？"
            }
        }
    }

    fun midCheckYes(
        mode: SessionAwarenessMode,
        path: CompanionPath? = null,
    ): String = when (path) {
        CompanionPath.SEARCH -> "还在搜"
        CompanionPath.BROWSE -> "再一会儿"
        CompanionPath.INTENT, null -> when (mode) {
            SessionAwarenessMode.TASK -> "还在"
            SessionAwarenessMode.URGE -> "还想"
        }
    }

    fun midCheckNo(
        mode: SessionAwarenessMode,
        path: CompanionPath? = null,
    ): String = when (path) {
        CompanionPath.SEARCH -> "找到了"
        CompanionPath.BROWSE -> "够了"
        CompanionPath.INTENT, null -> when (mode) {
            SessionAwarenessMode.TASK -> "已经偏了"
            SessionAwarenessMode.URGE -> "可以停了"
        }
    }

    /** 长意图截断，锚提示保持短句 */
    fun shortenNaming(raw: String?, maxChars: Int = 10): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        return if (t.length <= maxChars) t else t.take(maxChars - 1) + "…"
    }

    fun softExitTitle(
        mode: SessionAwarenessMode,
        path: CompanionPath? = null,
    ): String = when (path) {
        CompanionPath.SEARCH -> "要收这一次搜索吗？"
        CompanionPath.BROWSE -> "要收这一轮吗？"
        CompanionPath.INTENT, null -> when (mode) {
            SessionAwarenessMode.TASK -> "要结束这一次吗？"
            SessionAwarenessMode.URGE -> "要收这一次吗？"
        }
    }

    fun softExitBody(
        mode: SessionAwarenessMode,
        path: CompanionPath? = null,
    ): String = when (path) {
        CompanionPath.SEARCH -> "找到了可以停，想接着也行。"
        CompanionPath.BROWSE -> "停下来也行，想接着也行。"
        CompanionPath.INTENT, null -> when (mode) {
            SessionAwarenessMode.TASK -> "结束，或再待一会儿。"
            SessionAwarenessMode.URGE -> "停下来也行，想接着也行。"
        }
    }

    fun softExitStay(): String = "再待一会儿"
    fun softExitEnd(): String = "结束本次"

    // —— 结束后对照：先二选一，偏了侧再可选细档 ——

    /** 结束主问（路径差分） */
    fun endComparePrompt(path: CompanionPath): String = when (path) {
        CompanionPath.INTENT -> "完成了吗？"
        CompanionPath.SEARCH -> "找到答案了吗？"
        CompanionPath.BROWSE -> "这轮刷够了吗？"
    }

    /** 结束主问 · 对齐侧（立刻收束） */
    fun endCompareYes(path: CompanionPath): String = when (path) {
        CompanionPath.INTENT -> "完成了"
        CompanionPath.SEARCH -> "找到了"
        CompanionPath.BROWSE -> "够了"
    }

    /** 结束主问 · 偏航侧（可展开细档） */
    fun endCompareNo(path: CompanionPath): String = when (path) {
        CompanionPath.INTENT -> "没做完"
        CompanionPath.SEARCH -> "没找到"
        CompanionPath.BROWSE -> "没收住"
    }

    /** 对齐侧收束时的短确认 */
    fun endCompareDoneChip(path: CompanionPath): String = when (path) {
        CompanionPath.INTENT -> "已看见 · 这一次收了"
        CompanionPath.SEARCH -> "找到了就好"
        CompanionPath.BROWSE -> "这轮收了"
    }

    fun comparePrompt(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "和命名比，这一次"
        SessionAwarenessMode.URGE -> "这一次，待得"
    }

    fun compareHint(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "选一档，看见有没有跑偏"
        SessionAwarenessMode.URGE -> "选一档，看见有没有数"
    }

    fun tierLabel(mode: SessionAwarenessMode, level: Int): String = when (mode) {
        SessionAwarenessMode.TASK -> when (level) {
            MindfulnessLevel.ALIGNED -> "没跑偏"
            MindfulnessLevel.SLIGHT -> "跑偏了"
            MindfulnessLevel.LARGE -> "跑远了"
            else -> ""
        }
        SessionAwarenessMode.URGE -> when (level) {
            MindfulnessLevel.ALIGNED -> "有数"
            MindfulnessLevel.SLIGHT -> "有点久"
            MindfulnessLevel.LARGE -> "陷进去了"
            else -> ""
        }
    }

    fun notePlaceholder(mode: SessionAwarenessMode, level: Int?): String = when (mode) {
        SessionAwarenessMode.TASK -> when (level) {
            MindfulnessLevel.SLIGHT -> "实际去做了什么"
            MindfulnessLevel.LARGE -> "最后去了哪里"
            else -> ""
        }
        SessionAwarenessMode.URGE -> when (level) {
            MindfulnessLevel.SLIGHT -> "多待在哪了"
            MindfulnessLevel.LARGE -> "最后卡在哪了"
            else -> ""
        }
    }

    fun driftPrompt(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "其中大约多少时间跑偏了？"
        SessionAwarenessMode.URGE -> "大约多待了多久？"
    }

    /** 到点页 / 胶囊上展示命名时的前缀 */
    fun namingLine(mode: SessionAwarenessMode, naming: String): String {
        val t = naming.trim()
        if (t.isEmpty()) return ""
        return when (mode) {
            SessionAwarenessMode.TASK -> "意图「$t」"
            SessionAwarenessMode.URGE -> "带着「$t」"
        }
    }

    fun extendGateBody(mode: SessionAwarenessMode, extendMinutes: Int): String = when (mode) {
        SessionAwarenessMode.TASK ->
            "若这次已经跑偏，完成回顾会更清楚；还没跑偏，可以再要 $extendMinutes 分钟。"
        SessionAwarenessMode.URGE ->
            "若已经待得没数了，完成回顾会更清楚；还想接着，可以再要 $extendMinutes 分钟。"
    }

    fun extendHint(mode: SessionAwarenessMode): String = when (mode) {
        SessionAwarenessMode.TASK -> "已经跑偏了？先完成回顾"
        SessionAwarenessMode.URGE -> "待得没数了？先完成回顾"
    }
}
