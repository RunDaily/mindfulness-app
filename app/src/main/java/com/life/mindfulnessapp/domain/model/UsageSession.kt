package com.life.mindfulnessapp.domain.model

/**
 * 当前正在进行的使用会话（内存中）
 *
 * 计时设计：
 *   - [startTime]：本次「前台段」的开始时间戳，每次从后台回到前台都会被重置为当时的时刻。
 *   - [accumulatedActiveSeconds]：之前所有「前台段」累计的有效使用秒数（后台时间已排除）。
 *   - [currentSessionSeconds]：当前段 + 历史段 = 真实的有效使用总时长（不含后台）。
 */
data class UsageSession(
    val recordId: Long,                       // 对应数据库记录 ID
    val packageName: String,
    val appName: String,
    val startTime: Long,                      // 当前「前台段」开始时间戳（毫秒）
    val dailyLimitSeconds: Long,              // 今日限制时长（秒）
    val dailyUsedSeconds: Long,               // 今日历史已用时长（秒，不含本次会话）
    val weeklyLimitSeconds: Long,             // 本周限制时长（秒）
    val weeklyUsedSeconds: Long,              // 本周历史已用时长（秒，不含本次会话）
    val accumulatedActiveSeconds: Long = 0L,  // 本次会话中前台段累计秒数（不含当前段）
    val purpose: String? = null,
    val isInBackground: Boolean = false,
    val backgroundSinceMs: Long = 0L,
    /**
     * 是否为「超限续记」会话。
     * 用户在超限页点击「知道了」后，若 App 仍在前台，系统会自动开启此类 session 继续计时，
     * 以确保超出限额后的实际使用时长也被完整记录。
     * 处于此状态的 session 不再触发超限提示页（避免反复弹出）。
     */
    val isOverLimitSession: Boolean = false
) {
    /**
     * 本次会话的有效前台使用时长（秒）= 历史段 + 当前段（后台时不增长）
     */
    val currentSessionSeconds: Long
        get() = if (isInBackground) {
            accumulatedActiveSeconds  // 在后台，只算历史累计，当前段不计入
        } else {
            accumulatedActiveSeconds + (System.currentTimeMillis() - startTime) / 1000
        }

    val todayTotalSeconds: Long
        get() = dailyUsedSeconds + currentSessionSeconds

    val weekTotalSeconds: Long
        get() = weeklyUsedSeconds + currentSessionSeconds

    val dailyRemainingSeconds: Long
        get() = if (dailyLimitSeconds > 0) {
            (dailyLimitSeconds - todayTotalSeconds).coerceAtLeast(0)
        } else Long.MAX_VALUE

    val isDailyLimitExceeded: Boolean
        get() = dailyLimitSeconds > 0 && todayTotalSeconds >= dailyLimitSeconds

    val isWeeklyLimitExceeded: Boolean
        get() = weeklyLimitSeconds > 0 && weekTotalSeconds >= weeklyLimitSeconds
}
