package com.life.mindfulnessapp.domain.model

/**
 * MVP 统一「冷静期代价」：全屏按住配合的 60 秒呼吸。
 *
 * 节奏：4s 吸 / 6s 呼，一轮 10s，共 6 轮 = 60s。
 * 圆环缩放与口令跟相位同步，不是按剩余秒数硬切文案。
 *
 * 用于：
 * - 时段锁 / 日程锁已武装：关闭 / 删除 / 改动（不要求此刻落在窗内）
 * - 被锁罩住时：取消监控
 * - 日时长触顶后：放宽今日限额
 *
 * 规则：手指按在触控区才计时；松手 / 失焦作废重来；完成仅授权这一次动作。
 * 收紧规则（更严）原则上不走代价；已武装改窗为防旁路仍过门槛。
 */
object BreathCostPolicy {
    const val INHALE_MS = 4_000L
    const val EXHALE_MS = 6_000L
    const val CYCLE_MS = INHALE_MS + EXHALE_MS

    const val DURATION_MS = 60_000L
    const val DURATION_SECONDS = 60

    /**
     * 日时长触顶后的延长档位（分钟）。
     * 须写意图后任选一档进入；当日每个 App 仅一次。
     */
    val DAILY_GRACE_OPTIONS_MINUTES = listOf(5, 10, 15)

    /** 默认选中档（与历史固定 10 分对齐） */
    const val DEFAULT_DAILY_GRACE_MINUTES = 10

    /** @deprecated 请用 [DEFAULT_DAILY_GRACE_MINUTES] 或 [DAILY_GRACE_OPTIONS_MINUTES] */
    const val DAILY_GRACE_MINUTES = DEFAULT_DAILY_GRACE_MINUTES

    fun clampGraceMinutes(requested: Int): Int =
        if (requested in DAILY_GRACE_OPTIONS_MINUTES) requested
        else DEFAULT_DAILY_GRACE_MINUTES
}
