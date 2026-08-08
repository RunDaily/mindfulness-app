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
}
