package com.life.mindfulnessapp.domain.model

/**
 * 随意浏览冷却：按今日累计用量比例与连进次数抬档。
 *
 * - 累计 > 限额 1/2，或连进 ≥ 2 → 下次冷 5 分
 * - 累计 > 限额 2/3，或连进 ≥ 3 → 下次冷 10 分
 * - 两条件取更严；冷却守住后连进清零
 * - 随意浏览日限为 0（不限）时，比例按 [RATIO_BASE_WHEN_UNLIMITED] 计
 */
object BrowseCasualCooldown {
    const val TIER_SHORT_MINUTES = 5
    const val TIER_LONG_MINUTES = 10
    const val STREAK_SHORT = 2
    const val STREAK_LONG = 3
    const val RATIO_BASE_WHEN_UNLIMITED = BrowseCasualIntent.DEFAULT_DAILY_LIMIT_MINUTES

    data class Persisted(
        val dayKey: String = "",
        val streakCount: Int = 0,
        val cooldownUntilMs: Long = 0L,
    )

    data class GateSnapshot(
        /** 冷却未满时的剩余整分（向上取整，至少 1）；0 = 可进 */
        val remainingCooldownMinutes: Int,
        val cooldownUntilMs: Long,
    ) {
        val blocked: Boolean get() = remainingCooldownMinutes > 0
    }

    fun ratioBaseMinutes(browseDailyLimitMinutes: Int): Int =
        if (browseDailyLimitMinutes <= 0) RATIO_BASE_WHEN_UNLIMITED
        else browseDailyLimitMinutes

    /**
     * 下次进入前应施加的冷却分钟（0 / 5 / 10）。
     * @param usedBrowseMinutes 今日已刷整分
     * @param browseDailyLimitMinutes 随意浏览日限；0 = 不限
     * @param streakCount 冷却守住以来的连进次数（含刚结束的这一次）
     */
    fun cooldownMinutesFor(
        usedBrowseMinutes: Int,
        browseDailyLimitMinutes: Int,
        streakCount: Int,
    ): Int {
        val used = usedBrowseMinutes.coerceAtLeast(0)
        val streak = streakCount.coerceAtLeast(0)
        val base = ratioBaseMinutes(browseDailyLimitMinutes)
        val byRatio = when {
            used * 3 > base * 2 -> TIER_LONG_MINUTES
            used * 2 > base -> TIER_SHORT_MINUTES
            else -> 0
        }
        val byStreak = when {
            streak >= STREAK_LONG -> TIER_LONG_MINUTES
            streak >= STREAK_SHORT -> TIER_SHORT_MINUTES
            else -> 0
        }
        return maxOf(byRatio, byStreak)
    }

    fun remainingCooldownMinutes(cooldownUntilMs: Long, nowMs: Long): Int {
        val left = cooldownUntilMs - nowMs
        if (left <= 0L) return 0
        return ((left + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    /** 跨日或冷却已守住时，连进归零。 */
    fun effectiveStreak(persisted: Persisted, todayKey: String, nowMs: Long): Int {
        if (persisted.dayKey != todayKey) return 0
        if (persisted.cooldownUntilMs > 0L && nowMs >= persisted.cooldownUntilMs) return 0
        return persisted.streakCount.coerceAtLeast(0)
    }

    fun gateSnapshot(persisted: Persisted, todayKey: String, nowMs: Long): GateSnapshot {
        if (persisted.dayKey != todayKey) {
            return GateSnapshot(remainingCooldownMinutes = 0, cooldownUntilMs = 0L)
        }
        val until = persisted.cooldownUntilMs
        val remain = remainingCooldownMinutes(until, nowMs)
        return GateSnapshot(
            remainingCooldownMinutes = remain,
            cooldownUntilMs = if (remain > 0) until else 0L
        )
    }

    /** 放行进入：冷却已满则清连进，再 +1。 */
    fun afterSuccessfulEnter(
        persisted: Persisted,
        todayKey: String,
        nowMs: Long,
    ): Persisted {
        val streak = effectiveStreak(persisted, todayKey, nowMs) + 1
        return Persisted(
            dayKey = todayKey,
            streakCount = streak,
            cooldownUntilMs = 0L
        )
    }

    /**
     * 到点「续一点时间」：算一次连刷，连进 +1。
     * 会话仍在进行，不武装冷却；收口时由 [afterSessionEnded] 按抬高后的连进算。
     */
    fun afterExtend(
        persisted: Persisted,
        todayKey: String,
        nowMs: Long,
    ): Persisted = afterSuccessfulEnter(persisted, todayKey, nowMs)

    /** 会话结束：按累计与连进武装下次冷却。 */
    fun afterSessionEnded(
        persisted: Persisted,
        todayKey: String,
        nowMs: Long,
        usedBrowseMinutes: Int,
        browseDailyLimitMinutes: Int,
    ): Persisted {
        val streak = if (persisted.dayKey == todayKey) {
            persisted.streakCount.coerceAtLeast(0)
        } else {
            0
        }
        val cdMin = cooldownMinutesFor(
            usedBrowseMinutes = usedBrowseMinutes,
            browseDailyLimitMinutes = browseDailyLimitMinutes,
            streakCount = streak
        )
        return Persisted(
            dayKey = todayKey,
            streakCount = streak,
            cooldownUntilMs = if (cdMin > 0) nowMs + cdMin * 60_000L else 0L
        )
    }
}
