package com.life.mindfulnessapp.ui.applist

import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec

/**
 * 监控配置页顶部的「下次打开会发生什么」摘要。
 * 把三能力开关翻译成一句可读的行为预览。
 */
data class MonitorEffectSummary(
    /** 主句：用户最该记住的结果 */
    val headline: String,
    /** 次句：补充边界 / 承诺 */
    val detail: String?,
    /** 轻状态：如「时段锁生效中」 */
    val status: String?
)

fun buildMonitorEffectSummary(
    intentOn: Boolean,
    sessionLimitOn: Boolean,
    intentQualityCheckOn: Boolean = false,
    intentBlockKeywordCount: Int = 0,
    timeOn: Boolean,
    dailyLimitMinutes: Int,
    periodOn: Boolean,
    windows: List<PeriodWindow>,
    commitment: String,
    todayUsedLabel: String? = null
): MonitorEffectSummary {
    if (!intentOn && !timeOn && !periodOn) {
        return MonitorEffectSummary(
            headline = "还没有开启任何能力",
            detail = "至少打开一项，才会开始监控",
            status = null
        )
    }

    val activeWindow = if (periodOn) PeriodLockPolicy.activeWindow(windows) else null
    val enabledWindows = windows.filter { it.enabled }
    val periodSummary = when {
        !periodOn -> null
        enabledWindows.isEmpty() -> "时段已设但均未开启"
        else -> PeriodWindowsCodec.summaryLabel(windows)
    }
    val keywordGateReady = intentQualityCheckOn && intentBlockKeywordCount > 0

    val status = when {
        activeWindow != null -> "时段锁生效中 · ${activeWindow.label()}"
        todayUsedLabel != null && timeOn -> "今日已用 $todayUsedLabel"
        else -> null
    }

    val headline = when {
        activeWindow != null ->
            "此刻不可进入 · 锁定至 ${PeriodWindow.formatHm(activeWindow.endMinute)}"
        periodOn && intentOn && timeOn ->
            "非锁定时段：先写意图再进 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
        periodOn && intentOn ->
            "非锁定时段：打开前写下意图"
        periodOn && timeOn ->
            "非锁定时段可进入 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
        periodOn ->
            "非锁定时段可直接进入 · 锁定时段硬挡"
        intentOn && timeOn ->
            when {
                sessionLimitOn && keywordGateReady ->
                    "打开前写意图（限制词生效）并承诺多久 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
                sessionLimitOn ->
                    "打开前写意图并承诺多久 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
                keywordGateReady ->
                    "打开前写意图（限制词生效）· 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
                else ->
                    "打开前写下意图 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
            }
        intentOn ->
            when {
                sessionLimitOn && keywordGateReady ->
                    "打开前写意图（限制词生效），并承诺本次多久"
                sessionLimitOn ->
                    "打开前写下意图，并承诺本次多久"
                keywordGateReady ->
                    "打开前写意图；命中限制词不能进入"
                intentQualityCheckOn ->
                    "打开前写下意图 · 请先添加限制词"
                else ->
                    "打开前写下意图，再进入"
            }
        timeOn ->
            "可直接进入 · 每日最多 ${formatLimitMinutes(dailyLimitMinutes)}"
        else -> "已开启监控"
    }

    val detailParts = buildList {
        if (periodOn && activeWindow == null && periodSummary != null) {
            add(periodSummary)
        }
        if (periodOn && commitment.isNotBlank()) {
            add("守护「${commitment.trim()}」")
        }
        if (intentOn && keywordGateReady) {
            add("限制词 ${intentBlockKeywordCount} 个")
        }
        if (intentOn && sessionLimitOn && !(intentOn && timeOn && periodOn && activeWindow == null)) {
            if (periodOn && activeWindow == null && !timeOn) {
                add("含单次时长承诺")
            }
        }
    }

    return MonitorEffectSummary(
        headline = headline,
        detail = detailParts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        status = status
    )
}
