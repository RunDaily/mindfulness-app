package com.life.mindfulnessapp.overlay

/**
 * 离开反馈种类：门外克制与会话结束共用同一套叙事，只改强度与文案。
 */
/** 胶囊手动结束后的去向（由 OverlayManager → Service 分流） */
enum class ManualEndDestination {
    /** 已回顾 · 没跑偏 → 回桌面 + 对齐轻条 */
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
    /** 手动结束 + 没跑偏 */
    SessionAligned,
    /** 手动结束 + 跑偏/跑远：可点回看 */
    SessionDrifted,
    /** 结束并去心锚 / 到点收口：不挂桌面轻条 */
    SessionToAnchor
}

/** 离开去向：决定门外轻提示文案与是否播勋章 */
enum class DismissDestination {
    HOME,
    OWN_APP
}

/** 轻条上的可选正向去处 */
data class LeaveDestinationChoice(
    val packageName: String,
    val label: String
)

/**
 * 一次离开反馈请求。
 *
 * @param offerPositiveDestination 仅主动点「离开」为 true；Home 切走为 false
 */
data class LeaveFeedbackRequest(
    val kind: LeaveFeedbackKind,
    val packageName: String,
    val destination: DismissDestination = DismissDestination.HOME,
    val dismissCount: Int = 0,
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
    val showManageLink: Boolean = false
)

fun leaveFeedbackCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
    return when (request.kind) {
        LeaveFeedbackKind.GateSilent, LeaveFeedbackKind.SessionToAnchor ->
            LeaveFeedbackCopy(title = "")

        LeaveFeedbackKind.GateLight, LeaveFeedbackKind.GateMilestone ->
            gateLeaveCopy(request)

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

private fun gateLeaveCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
    val count = request.dismissCount
    return when (request.destination) {
        DismissDestination.OWN_APP -> LeaveFeedbackCopy(
            title = "先去心锚",
            subtitle = if (count <= 1) "这一次，你选择了离开" else "今日已守住 $count 次"
        )
        DismissDestination.HOME -> when {
            request.isLimitTheme -> LeaveFeedbackCopy(
                title = "时间到了 · 先离开",
                subtitle = if (count > 1) "今日已守住 $count 次" else null
            )
            count <= 1 -> LeaveFeedbackCopy(title = "守住了")
            else -> LeaveFeedbackCopy(title = "今日已守住 $count 次")
        }
    }
}

private fun truncatePurpose(purpose: String, maxChars: Int = 16): String {
    val t = purpose.trim()
    return if (t.length <= maxChars) t else t.take(maxChars - 1) + "…"
}

/**
 * 今日克制次数是否值得播全屏勋章（里程碑，而非每次离开）。
 * 第 5 次、以及每满 10 次；日常与「今日第一次」走轻提示。
 */
fun isDismissMilestone(displayCount: Int): Boolean =
    displayCount == 5 || (displayCount >= 10 && displayCount % 10 == 0)
