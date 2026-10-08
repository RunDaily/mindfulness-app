package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveRitualTest {

    @Test
    fun gateSubtitle_isFixed() {
        assertEquals("这一次，也很好", LeaveRitual.GATE_SUBTITLE)
    }

    @Test
    fun residualPool_isQuietAndShort() {
        assertTrue(LeaveRitual.RESIDUAL_POOL.size >= 3)
        LeaveRitual.RESIDUAL_POOL.forEach { line ->
            assertTrue(line.length <= 10)
            assertFalse(line.contains("棒"))
            assertFalse(line.contains("戒断"))
        }
    }

    @Test
    fun nextResidual_avoidsRepeat() {
        val first = LeaveRitual.nextResidual(seed = 0)
        val second = LeaveRitual.nextResidual(avoid = first, seed = 0)
        assertNotEquals(first, second)
        assertTrue(LeaveRitual.RESIDUAL_POOL.contains(second))
    }

    @Test
    fun milestone_fifthAndTens() {
        assertFalse(LeaveRitual.isMilestone(1))
        assertFalse(LeaveRitual.isMilestone(4))
        assertTrue(LeaveRitual.isMilestone(5))
        assertFalse(LeaveRitual.isMilestone(9))
        assertTrue(LeaveRitual.isMilestone(10))
        assertTrue(LeaveRitual.isMilestone(20))
    }
}
