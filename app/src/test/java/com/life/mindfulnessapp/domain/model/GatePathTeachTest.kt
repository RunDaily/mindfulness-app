package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatePathTeachTest {

    @Test
    fun copy_isSettled() {
        assertEquals("我知道要做什么", GatePathTeach.INTENT_SUBTITLE)
        assertEquals("我知道要搜什么", GatePathTeach.SEARCH_SUBTITLE)
        assertEquals("我没有明确的目的", GatePathTeach.BROWSE_SUBTITLE)
    }

    @Test
    fun showsAllowed_tapersOverThreeDays() {
        assertEquals(3, GatePathTeach.showsAllowedOnDay(0))
        assertEquals(2, GatePathTeach.showsAllowedOnDay(1))
        assertEquals(1, GatePathTeach.showsAllowedOnDay(2))
        assertEquals(0, GatePathTeach.showsAllowedOnDay(3))
        assertEquals(0, GatePathTeach.showsAllowedOnDay(10))
    }

    @Test
    fun dayIndex_countsCalendarDays() {
        assertEquals(0, GatePathTeach.dayIndex("20261008", "20261008"))
        assertEquals(1, GatePathTeach.dayIndex("20261008", "20261009"))
        assertEquals(2, GatePathTeach.dayIndex("20261008", "20261010"))
        assertEquals(3, GatePathTeach.dayIndex("20261008", "20261011"))
    }

    @Test
    fun shouldShow_respectsDailyCap() {
        // Day 1: first 3
        assertTrue(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261008", 0))
        assertTrue(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261008", 2))
        assertFalse(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261008", 3))
        // Day 2: first 2
        assertTrue(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261009", 0))
        assertTrue(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261009", 1))
        assertFalse(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261009", 2))
        // Day 3: first 1
        assertTrue(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261010", 0))
        assertFalse(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261010", 1))
        // Day 4+: never
        assertFalse(GatePathTeach.shouldShowTeachingSubtitles("20261008", "20261011", 0))
    }
}
