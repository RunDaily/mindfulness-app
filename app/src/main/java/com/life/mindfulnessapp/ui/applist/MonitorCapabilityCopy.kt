package com.life.mindfulnessapp.ui.applist

import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.IntentBlockKeywords
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.MonitorCapability

/**
 * 三能力对外文案：列表短句 + 介绍页长文，绑定/详情/入门指南共用。
 *
 * 字段分工：
 * - [description]：机制句，决策主信息（选能力 / 列表 / 引导）
 * - [slogan]：入门卡 / 详情页价值口号
 * - [identityOne] / [identityTwo]：双成就身份（勋章式展示）
 * - [featureExplain]：入门详情·功能说明
 * - [featureNote]：功能说明补充（如特殊规则），可空
 */
data class CapabilityCopy(
    val kind: CapabilityKind,
    val description: String,
    val slogan: String,
    /** 介绍页正文：讲清机制与体感（绑定流「了解形态」） */
    val introBody: String,
    /** 介绍页场景要点（绑定流） */
    val scenes: List<String>,
    /** 入门：成就身份其一 */
    val identityOne: String,
    /** 入门：成就身份其二 */
    val identityTwo: String,
    /** 入门详情：功能说明 */
    val featureExplain: String,
    /** 入门详情：功能说明旁注（特殊规则等） */
    val featureNote: String? = null
) {
    val label: String get() = MonitorCapability.label(kind)

    companion object {
        val All: List<CapabilityCopy> = listOf(
            CapabilityCopy(
                kind = CapabilityKind.IntentGate,
                description = "打开前先停一下，叫出这一次是什么",
                slogan = "用觉察，而不是意志力",
                introBody = "打开被监控 App 前，会先停住一瞬，再问「这一次是什么」。可以写要做的事，也可以诚实说「就是想刷」；然后托住多久再进入。也可以不进去，或去做别的。适合容易「点开就滑走」的应用，把无意识打开变成一次到场。",
                scenes = listOf(
                    "刷短视频、信息流前，先叫出这一次是什么",
                    "允许「就是想刷」——诚实比表演更靠近觉察",
                    "离开也算到场，不是毅力考核"
                ),
                identityOne = "无意识打破者",
                identityTwo = "觉察培养者",
                featureExplain = "每次打开前先停住，再命名这一次（事或冲动都合法），托住时长后进入；也可以离开或转向去做别的。有事时，进行中问还在不在做、结束对照有没有跑偏；诚实冲动则问还想不想待、对照待得有没有数。"
            ),
            CapabilityCopy(
                kind = CapabilityKind.TimeLock,
                description = "限制今天一共能用多久，用尽即拦",
                slogan = "有用也有度",
                introBody = "为 App 设定每日可用上限。用着时边缘会浮着胶囊显示用量；额度用尽后会拦住继续进入。管的是「今天一共能用多久」，适合总量失控、需要日额度的场景。",
                scenes = listOf(
                    "某一款 App 每天总超时，想设硬上限",
                    "希望用着时能看见「今天还剩多少」",
                    "与意图门可叠加：先写意图、定时长，再管好全天额度"
                ),
                identityOne = "拒绝无节制",
                identityTwo = "底线守护者",
                featureExplain = "为 App 设定每日的可用上限（例如 40 分钟），使用期间会有悬浮胶囊感知时间的用量，超出限额会限制继续使用。如果你察觉有的应用像一个时间黑洞，大量吸收你的时间，可启用这项能力来给它设下底线。",
                featureNote = "特殊情况：游戏类应用在即将到达时长限额前允许延长一定范围的收尾时间；非游戏类应用在达到限额后，进入需要写下明确的使用意图。"
            ),
            CapabilityCopy(
                kind = CapabilityKind.PeriodLock,
                description = "到了设定时段，打开即被硬挡",
                slogan = "守护你的关键时段",
                introBody = "在你设定的关键时段（如睡眠、学习、陪伴）内，打开被监控 App 会直接看到硬门，无法破界进入。适合需要「这段时间绝对不碰」的边界，而不是慢慢消耗额度。",
                scenes = listOf(
                    "睡前 / 起床后不想再刷手机",
                    "学习、工作块时间需要完全隔离",
                    "想为自己留一句承诺文案，提醒「为什么现在不能进」"
                ),
                identityOne = "晚睡杀手",
                identityTwo = "效率全开能手",
                featureExplain = "设定一段或多段关键时段（如 23:00–07:00）。在这段时间内打开被监控的 App，会直接看到硬门，无法破界进入。这个能力可以用来守护你的睡眠以及学习和工作的专注度。"
            )
        )

        fun of(kind: CapabilityKind): CapabilityCopy =
            All.first { it.kind == kind }
    }
}

/**
 * 能力条目下的 Peek：开启时给一眼今日状态；未开启时给轻引导。
 */
data class CapabilityPeek(
    val primary: String,
    val secondary: String? = null,
    /** false = 未开启时的占位 peek */
    val active: Boolean = true
)

fun buildCapabilityPeek(
    kind: CapabilityKind,
    enabled: Boolean,
    glance: AppTodayGlance?,
    dailyLimitMinutes: Int,
    sessionLimitOn: Boolean,
    intentQualityCheckOn: Boolean = false,
    intentBlockKeywordCount: Int = 0,
    periodWindows: List<PeriodWindow>,
    periodCommitment: String = ""
): CapabilityPeek {
    if (!enabled) {
        val copy = CapabilityCopy.of(kind)
        return CapabilityPeek(
            primary = "点此开启${copy.label}",
            secondary = copy.description,
            active = false
        )
    }
    return when (kind) {
        CapabilityKind.IntentGate -> {
            val dismiss = glance?.dismissCount ?: 0
            val open = glance?.enterCount ?: 0
            CapabilityPeek(
                primary = when {
                    open == 0 && dismiss == 0 -> "今日尚未打开"
                    else -> "今日打开 $open · 守住 $dismiss · 目的 ${glance?.mindfulEnterCount ?: 0}"
                },
                secondary = if (sessionLimitOn) "进门先定时长" else null
            )
        }
        CapabilityKind.TimeLock -> {
            val used = formatCapabilityDuration(glance?.totalSeconds ?: 0L)
            val limit = formatCapabilityLimitMinutes(dailyLimitMinutes)
            CapabilityPeek(
                primary = "今日 $used",
                secondary = "每日上限 $limit"
            )
        }
        CapabilityKind.PeriodLock -> {
            val active = PeriodLockPolicy.activeWindow(periodWindows)
            val summary = PeriodWindowsCodec.summaryLabel(periodWindows)
            val rawNote = active?.message?.trim().orEmpty().ifBlank {
                periodWindows.firstOrNull { it.message.isNotBlank() }?.message?.trim().orEmpty()
            }.ifBlank { periodCommitment.trim() }
            val note = rawNote.takeIf { it.isNotEmpty() }?.let { text ->
                if (text.length <= 24) text else text.take(24) + "…"
            }
            when {
                active != null -> CapabilityPeek(
                    primary = "此刻生效中 · ${active.label()}",
                    secondary = note ?: summary
                )
                else -> CapabilityPeek(
                    primary = summary.ifBlank { "已设定时段" },
                    secondary = note
                )
            }
        }
    }
}

/**
 * 能力应用列表行：今日状态 + 配置摘要，并标出与当前能力叠加的其他能力。
 */
fun buildCapabilityAppRowPeek(
    kind: CapabilityKind,
    app: AppInfo,
    glance: AppTodayGlance?
): CapabilityPeek {
    val windows = PeriodWindowsCodec.decode(app.periodWindowsJson)
    val keywordCount = IntentBlockKeywords.decode(app.intentBlockKeywordsJson).size
    val peek = buildCapabilityPeek(
        kind = kind,
        enabled = true,
        glance = glance,
        dailyLimitMinutes = app.dailyLimitMinutes,
        sessionLimitOn = app.requireIntentOnOpen && app.sessionLimitEnabled,
        intentQualityCheckOn = app.intentQualityCheckEnabled,
        intentBlockKeywordCount = keywordCount,
        periodWindows = windows,
        periodCommitment = app.periodLockCommitment
    )
    val stacked = buildList {
        when (kind) {
            CapabilityKind.IntentGate -> {
                if (app.timeLimitEnabled) add("叠时长锁")
                if (app.periodLockEnabled) add("叠时段锁")
            }
            CapabilityKind.TimeLock -> {
                if (app.requireIntentOnOpen) add("叠意图门")
                if (app.periodLockEnabled) add("叠时段锁")
            }
            CapabilityKind.PeriodLock -> {
                if (app.requireIntentOnOpen) add("叠意图门")
                if (app.timeLimitEnabled) add("叠时长锁")
            }
        }
    }
    val secondary = listOfNotNull(
        peek.secondary,
        stacked.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    ).joinToString(" · ").ifBlank { null }
    return peek.copy(secondary = secondary)
}

fun formatCapabilityDuration(seconds: Long): String {
    if (seconds <= 0L) return "0 分"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "$totalMin 分"
        else -> {
            val h = totalMin / 60L
            val m = totalMin % 60L
            if (m == 0L) "${h} 小时" else "${h} 小时 ${m} 分"
        }
    }
}

fun formatCapabilityLimitMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes} 分"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}
