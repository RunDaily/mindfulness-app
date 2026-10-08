package com.life.mindfulnessapp.domain.model

/**
 * 时长锁 · 日限额临近时的**系统真灰度**（整屏去饱和，无彩色）。
 *
 * 规则：
 * - 仅看**日限额**（配置基础额度）：剩余 ≤ 1/5 时首次武装
 * - 一旦今日已武装：续时长 / 再次进入仍保持灰度（偏好按日持久化）
 * - 实现见 [com.life.mindfulnessapp.display.DisplayGrayscaleController]（系统 daltonizer），
 *   不是半透明灰幕（灰幕去不掉底下彩色）
 */
object DailyLimitGrayWashPolicy {
    /** 剩余占比分母：1/5 */
    const val REMAINING_FRACTION = 5

    /** 阈值下限（秒）：短日限额时至少按 3 分钟起灰 */
    const val MIN_THRESHOLD_SECONDS = 3 * 60L

    /**
     * 开始变灰的剩余秒数阈值 = max(日限额/5, 3 分钟)，且不超过日限额本身。
     * [dailyLimitSeconds] 宜传配置基础日限额。
     */
    fun thresholdSeconds(dailyLimitSeconds: Long): Long {
        if (dailyLimitSeconds <= 0L) return 0L
        return maxOf(dailyLimitSeconds / REMAINING_FRACTION, MIN_THRESHOLD_SECONDS)
            .coerceAtMost(dailyLimitSeconds)
    }

    /** 是否应首次武装（今日尚未武装时） */
    fun shouldTrigger(dailyLimitSeconds: Long, dailyRemainingSeconds: Long): Boolean {
        if (dailyLimitSeconds <= 0L) return false
        val threshold = thresholdSeconds(dailyLimitSeconds)
        if (threshold <= 0L) return false
        return dailyRemainingSeconds.coerceAtLeast(0L) <= threshold
    }
}
