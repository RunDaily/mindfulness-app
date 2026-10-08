package com.life.mindfulnessapp.overlay

import com.life.mindfulnessapp.domain.model.LeaveRitual

/**
 * 离开反馈种类：门外克制与会话结束共用同一套叙事，只改强度与文案。
 * 主动离开仪式口径见 [LeaveRitual] / `.design/leave-ritual-sketch.html`。
 */
/** 胶囊手动结束后的去向（由 OverlayManager → Service 分流） */
enum class ManualEndDestination {
    /** 已回顾 · 没跑偏 → 回桌面静默（不挂轻条） */
    HomeAligned,
    /** 已回顾 · 跑偏/跑远 → 回桌面 + 可点回看轻条 */
    HomeDrifted,
    /** 结束并去心锚 / 点「去看这一次」 */
    OpenRecord,
    /** 未做正念回顾的旧路径 */
    LegacyUnreviewed
}

enum class LeaveFeedbackKind {
    /** 同 App 冷却内：静默离开 */
    GateSilent,
    /** 日常门外离开：顶部轻条 */
    GateLight,
    /** 里程碑门外离开：全屏短勋章（可跳过）后再离开 */
    GateMilestone,
    /** 正向出口：去做了 */
    GatePositiveExit,
    /** 手动结束 + 没跑偏：静默（回顾已确认，不挂轻条） */
    SessionAligned,
    /** 手动结束 + 跑偏/跑远：可点回看 */
    SessionDrifted,
    /** 结束并去心锚 / 到点收口：不挂桌面轻条 */
    SessionToAnchor
}

/** 离开去向：门外轻提示文案（目前仅回桌面） */
enum class DismissDestination {
    HOME
}

/**
 * 全屏拦截层种类。门口离开以页上动作为准；Home / 多任务拉回门口（见 product.md P6）。
 *
 * - [IntentGate]：门未进；页上离开才记守住
 * - [SessionLimit]：意图时长到点
 * - [DailyLimit] / [PeriodLock]：硬墙已生效
 */
enum class InterceptOverlayKind {
    IntentGate,
    Breath,
    SessionLimit,
    DailyLimit,
    OpenLimit,
    PeriodLock;

    val dismissesSilentlyOnHome: Boolean
        get() = false

    /**
     * UsageStats 仍报原 App 时，也要探测桌面/切走。
     * 只认保护窗之后的桌面进入；盖层抢焦点写出的 BACKGROUND 不算 Home。
     */
    val probesHomeWhileSamePackage: Boolean
        get() = true
}

/** 轻条上的可选正向去处 */
data class LeaveDestinationChoice(
    val packageName: String,
    val label: String
)

/**
 * 一次离开反馈请求。
 *
 * @param offerPositiveDestination 仅页上主动点「离开」为 true
 */
data class LeaveFeedbackRequest(
    val kind: LeaveFeedbackKind,
    val packageName: String,
    val destination: DismissDestination = DismissDestination.HOME,
    val dismissCount: Int = 0,
    /** 「去做了」今日累计（仅 [LeaveFeedbackKind.GatePositiveExit]） */
    val positiveExitCount: Int = 0,
    /** 展示用应用名，如「小红书」 */
    val appLabel: String? = null,
    val purposeHint: String? = null,
    val applyGateCooldown: Boolean = false,
    val isLimitTheme: Boolean = false,
    val offerPositiveDestination: Boolean = false
)

data class LeaveFeedbackCopy(
    val title: String,
    val subtitle: String? = null,
    /** 主动作文案；null 表示不可点主区 */
    val actionLabel: String? = null,
    /** 「更多」文案；有多个正向 App 时出现 */
    val moreLabel: String? = null,
    /** 展开「更多」后的选项（轻条最多展示池内其余项） */
    val moreChoices: List<LeaveDestinationChoice> = emptyList(),
    /** 主动作目标包名（去正向 App） */
    val primaryPackageName: String? = null,
    /** 主动作是打开设置引导 */
    val opensSettings: Boolean = false,
    /** 配置数超过轻条展示上限时，展开区提供「管理」入口 */
    val showManageLink: Boolean = false,
    /** 进心锚查看该 App 今日记录；null 表示不展示 */
    val detailLabel: String? = null
)

fun leaveFeedbackCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
    return when (request.kind) {
        LeaveFeedbackKind.GateSilent, LeaveFeedbackKind.SessionToAnchor ->
            LeaveFeedbackCopy(title = "")

        LeaveFeedbackKind.GateLight, LeaveFeedbackKind.GateMilestone ->
            gateLeaveCopy(request)

        LeaveFeedbackKind.GatePositiveExit ->
            positiveExitLeaveCopy(request)

        LeaveFeedbackKind.SessionAligned -> {
            val hint = request.purposeHint?.trim().orEmpty()
            LeaveFeedbackCopy(
                title = "对齐了",
                subtitle = hint.takeIf { it.isNotEmpty() }?.let { truncatePurpose(it) }
            )
        }

        LeaveFeedbackKind.SessionDrifted ->
            LeaveFeedbackCopy(
                title = "记下了",
                actionLabel = "去看这一次"
            )
    }
}

/**
 * 在基础门外文案上叠加正向归属 / 配置引导。
 *
 * @param displayChoices 轻条本次最多露出的去处（含主项，通常 ≤ 3）
 * @param totalConfigured 用户配置总数；大于 display 时出「管理」
 */
fun enrichGateCopyWithDestination(
    base: LeaveFeedbackCopy,
    primary: LeaveDestinationChoice?,
    displayChoices: List<LeaveDestinationChoice>,
    setupNudge: Boolean,
    totalConfigured: Int = displayChoices.size
): LeaveFeedbackCopy {
    if (setupNudge) {
        return base.copy(
            actionLabel = "给自己留一个去处",
            moreLabel = null,
            moreChoices = emptyList(),
            primaryPackageName = null,
            opensSettings = true,
            showManageLink = false
        )
    }
    if (primary == null) return base
    val others = displayChoices.filter { it.packageName != primary.packageName }
    return base.copy(
        actionLabel = "去「${primary.label}」",
        moreLabel = if (others.isNotEmpty() || totalConfigured > displayChoices.size) "更多" else null,
        moreChoices = others,
        primaryPackageName = primary.packageName,
        opensSettings = false,
        showManageLink = totalConfigured > displayChoices.size
    )
}

/** 门外离开轻条主文案（到场，非毅力勋章） */
fun formatGateDismissTitle(
    appLabel: String?,
    count: Int,
    isLimitTheme: Boolean = false
): String {
    if (isLimitTheme) return "时间到了 · 先离开"
    val named = appLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { "「$it」" } ?: ""
    return when {
        count <= 1 -> if (named.isNotEmpty()) "${named}到场了" else "到场了"
        else -> if (named.isNotEmpty()) "${named}今日到场 $count 次" else "今日到场 $count 次"
    }
}

fun formatPositiveExitTitle(hint: String?): String {
    val t = hint?.trim().orEmpty()
    return if (t.isEmpty()) "去做了" else "去做了 · ${truncatePurpose(t, 14)}"
}

private fun gateLeaveCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
    if (request.isLimitTheme) {
        return LeaveFeedbackCopy(
            title = "时间到了 · 先离开",
            detailLabel = "详细"
        )
    }
    // 余韵小池；门上副句固定不走这里
    val seed = request.dismissCount * 31 + request.packageName.hashCode()
    return LeaveFeedbackCopy(
        title = LeaveRitual.nextResidual(seed = seed),
        detailLabel = "详细"
    )
}

private fun positiveExitLeaveCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
    val count = request.positiveExitCount.coerceAtLeast(1)
    val subtitle = when {
        count <= 1 -> null
        else -> "今日去做 $count 次"
    }
    return LeaveFeedbackCopy(
        title = formatPositiveExitTitle(request.purposeHint),
        subtitle = subtitle,
        detailLabel = "详细"
    )
}

private fun truncatePurpose(purpose: String, maxChars: Int = 16): String {
    val t = purpose.trim()
    return if (t.length <= maxChars) t else t.take(maxChars - 1) + "…"
}

/**
 * 今日克制次数是否值得播全屏勋章（里程碑，而非每次离开）。
 * 第 5 次、以及每满 10 次；冷却内撞上则推迟（见 OverlayManager）。
 */
fun isDismissMilestone(displayCount: Int): Boolean =
    LeaveRitual.isMilestone(displayCount)
