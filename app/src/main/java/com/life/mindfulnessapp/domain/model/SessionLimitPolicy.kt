package com.life.mindfulnessapp.domain.model

/**
 * 单次会话时长上限策略。
 *
 * 有效上限 = min(用户选择, 日剩余分钟, [MAX_SESSION_MINUTES])；
 * 日锁关闭时日剩余视为无上限。
 */
object SessionLimitPolicy {
    const val MAX_SESSION_MINUTES = 60
    const val MIN_SESSION_MINUTES = 1
    const val DEFAULT_SESSION_MINUTES = 15

    /**
     * @param dailyRemainingMinutes 今日剩余可用分钟；日锁关闭时传 [Int.MAX_VALUE]
     */
    fun clampSessionMinutes(
        requestedMinutes: Int,
        dailyRemainingMinutes: Int
    ): Int {
        val ceiling = minOf(MAX_SESSION_MINUTES, dailyRemainingMinutes.coerceAtLeast(MIN_SESSION_MINUTES))
        return requestedMinutes.coerceIn(MIN_SESSION_MINUTES, ceiling)
    }

    /** 单次可选的最大分钟数 */
    fun maxSelectableMinutes(dailyRemainingMinutes: Int): Int =
        minOf(MAX_SESSION_MINUTES, dailyRemainingMinutes.coerceAtLeast(MIN_SESSION_MINUTES))

    fun dailyRemainingMinutes(
        dailyLimitMinutes: Int,
        todayUsedSeconds: Long
    ): Int {
        if (dailyLimitMinutes <= 0) return Int.MAX_VALUE
        val usedMinutes = (todayUsedSeconds / 60L).toInt()
        return (dailyLimitMinutes - usedMinutes).coerceAtLeast(0)
    }

    /** 意图时长锁：单次最长可续 = 原时长的 1/3（向下取整） */
    fun maxExtensionMinutes(sessionLimitMinutes: Int): Int =
        sessionLimitMinutes.coerceAtLeast(0) / 3

    /**
     * 到点页「续一点时间」上限。
     * - 随意浏览：不超过今日随意浏览剩余（不限则退回原时长 1/3）
     * - 其它路径：原时长 1/3
     *
     * @param remainingBrowseMinutes 随意浏览剩余整分；null = 非随意浏览；不限时传 [Int.MAX_VALUE]
     */
    fun maxLimitReachedExtensionMinutes(
        sessionLimitMinutes: Int,
        remainingBrowseMinutes: Int? = null
    ): Int {
        val byThird = maxExtensionMinutes(sessionLimitMinutes)
        if (remainingBrowseMinutes == null) return byThird
        if (remainingBrowseMinutes == Int.MAX_VALUE) return byThird
        return remainingBrowseMinutes
            .coerceAtLeast(0)
            .coerceAtMost(MAX_SESSION_MINUTES)
    }

    /**
     * 续时三档：均分 [maxExtensionMinutes]，去重后可能不足 3 个。
     * 例：原 15 分 → 最长 5 → [1, 3, 5]；原 30 分 → 最长 10 → [3, 6, 10]。
     */
    fun extensionMinuteOptions(sessionLimitMinutes: Int): List<Int> {
        val max = maxExtensionMinutes(sessionLimitMinutes)
        if (max <= 0) return emptyList()
        if (max == 1) return listOf(1)
        val oneThird = (max / 3).coerceAtLeast(1)
        val twoThird = ((max * 2) / 3).coerceAtLeast(oneThird)
        return listOf(oneThird, twoThird, max)
            .distinct()
            .filter { it in MIN_SESSION_MINUTES..max }
    }
}
