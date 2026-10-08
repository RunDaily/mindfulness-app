package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseCasualCooldownTest {

    @Test
    fun ratioHalfTriggersFive() {
        // 限额 30：>15 → 5；>20 → 10
        assertEquals(0, BrowseCasualCooldown.cooldownMinutesFor(15, 30, 1))
        assertEquals(5, BrowseCasualCooldown.cooldownMinutesFor(16, 30, 1))
        assertEquals(10, BrowseCasualCooldown.cooldownMinutesFor(21, 30, 1))
    }

    @Test
    fun streakTwoAndThree() {
        assertEquals(0, BrowseCasualCooldown.cooldownMinutesFor(0, 30, 1))
        assertEquals(5, BrowseCasualCooldown.cooldownMinutesFor(0, 30, 2))
        assertEquals(10, BrowseCasualCooldown.cooldownMinutesFor(0, 30, 3))
    }

    @Test
    fun takesStricterTier() {
        // 比例已到 10，连进只有 2
        assertEquals(10, BrowseCasualCooldown.cooldownMinutesFor(21, 30, 2))
        // 连进 3，用量未过半
        assertEquals(10, BrowseCasualCooldown.cooldownMinutesFor(5, 30, 3))
    }

    @Test
    fun unlimitedUsesDefaultBase() {
        assertEquals(
            BrowseCasualIntent.DEFAULT_DAILY_LIMIT_MINUTES,
            BrowseCasualCooldown.ratioBaseMinutes(0)
        )
        assertEquals(5, BrowseCasualCooldown.cooldownMinutesFor(16, 0, 1))
    }

    @Test
    fun remainingCooldownCeilsMinutes() {
        val now = 1_000_000L
        assertEquals(0, BrowseCasualCooldown.remainingCooldownMinutes(now, now))
        assertEquals(1, BrowseCasualCooldown.remainingCooldownMinutes(now + 1, now))
        assertEquals(1, BrowseCasualCooldown.remainingCooldownMinutes(now + 60_000L, now))
        assertEquals(2, BrowseCasualCooldown.remainingCooldownMinutes(now + 60_001L, now))
    }

    @Test
    fun honorCooldownResetsStreakOnEnter() {
        val today = "20261008"
        val now = 10_000_000L
        val cooled = BrowseCasualCooldown.Persisted(
            dayKey = today,
            streakCount = 3,
            cooldownUntilMs = now - 1L
        )
        val next = BrowseCasualCooldown.afterSuccessfulEnter(cooled, today, now)
        assertEquals(1, next.streakCount)
        assertEquals(0L, next.cooldownUntilMs)
    }

    @Test
    fun afterExtendBumpsStreakLikeEnter() {
        val today = "20261008"
        val now = 10_000_000L
        val afterEnter = BrowseCasualCooldown.Persisted(today, streakCount = 1, cooldownUntilMs = 0L)
        val afterExtend = BrowseCasualCooldown.afterExtend(afterEnter, today, now)
        assertEquals(2, afterExtend.streakCount)
        assertEquals(0L, afterExtend.cooldownUntilMs)
    }

    @Test
    fun afterSessionArmsCooldown() {
        val today = "20261008"
        val now = 10_000_000L
        val afterEnter = BrowseCasualCooldown.Persisted(today, streakCount = 2, cooldownUntilMs = 0L)
        val armed = BrowseCasualCooldown.afterSessionEnded(
            persisted = afterEnter,
            todayKey = today,
            nowMs = now,
            usedBrowseMinutes = 5,
            browseDailyLimitMinutes = 30
        )
        assertEquals(2, armed.streakCount)
        assertEquals(now + 5 * 60_000L, armed.cooldownUntilMs)
        val gate = BrowseCasualCooldown.gateSnapshot(armed, today, now + 1)
        assertTrue(gate.blocked)
        assertEquals(5, gate.remainingCooldownMinutes)
        assertFalse(
            BrowseCasualCooldown.gateSnapshot(armed, today, now + 5 * 60_000L).blocked
        )
    }

    @Test
    fun newDayClearsGateBlock() {
        val persisted = BrowseCasualCooldown.Persisted(
            dayKey = "20261007",
            streakCount = 3,
            cooldownUntilMs = Long.MAX_VALUE
        )
        val gate = BrowseCasualCooldown.gateSnapshot(persisted, "20261008", 1L)
        assertFalse(gate.blocked)
        assertEquals(0, BrowseCasualCooldown.effectiveStreak(persisted, "20261008", 1L))
    }
}
