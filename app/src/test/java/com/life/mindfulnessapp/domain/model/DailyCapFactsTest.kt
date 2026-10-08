package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCapFactsTest {

    @Test
    fun usedIgnoresSystemEvenIfLarger() {
        // 系统虚高（含门口盖层）不得抬高已用；双参已废弃且忽略第二参
        @Suppress("DEPRECATION")
        assertEquals(18 * 60L, DailyCapFacts.usedSeconds(18 * 60L, 30 * 60L))
        assertEquals(18 * 60L, DailyCapFacts.usedSeconds(18 * 60L))
        assertEquals(32 * 60L, DailyCapFacts.usedSeconds(32 * 60L))
    }

    @Test
    fun recordExhaustsAgainstTheEffectiveLimit() {
        val used = DailyCapFacts.usedSeconds(30 * 60L)
        assertTrue(DailyCapFacts.exhausted(used, 30 * 60L))
        assertEquals(30, DailyCapFacts.wholeMinutes(used))
        assertNull(DailyCapFacts.remainingMinutes(used, 30 * 60L))
    }

    @Test
    fun graceCeilingRaisesTheLimit() {
        val used = DailyCapFacts.usedSeconds(35 * 60L)
        assertFalse(DailyCapFacts.exhausted(used, 40 * 60L))
        assertEquals(5, DailyCapFacts.remainingMinutes(used, 40 * 60L))
    }

    @Test
    fun overshootKeepsTheExtraMinute() {
        val used = DailyCapFacts.usedSeconds(32 * 60L)
        assertTrue(DailyCapFacts.exhausted(used, 30 * 60L))
        assertEquals(32, DailyCapFacts.wholeMinutes(used))
    }
}
