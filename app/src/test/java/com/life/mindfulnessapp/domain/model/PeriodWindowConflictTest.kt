package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodWindowConflictTest {

    private val sleep = PeriodWindow.defaultSleep()
    private val allDay = PeriodWindow.allDay()
    private val night = PeriodWindow(
        startMinute = 0,
        endMinute = 6 * 60,
        daysMask = PeriodDays.EVERY_DAY
    )
    private val workdays = PeriodWindow(
        startMinute = 9 * 60,
        endMinute = 18 * 60,
        daysMask = PeriodDays.WEEKDAYS
    )
    private val weekendsSameHours = PeriodWindow(
        startMinute = 9 * 60,
        endMinute = 18 * 60,
        daysMask = PeriodDays.WEEKENDS
    )

    @Test
    fun duplicateSameRange() {
        val copy = sleep.copy(id = "other")
        assertEquals(
            PeriodWindowConflict.Duplicate,
            PeriodLockPolicy.conflictWith(copy, listOf(sleep))
        )
    }

    @Test
    fun allDayOverlapsSleep() {
        assertEquals(
            PeriodWindowConflict.Overlap,
            PeriodLockPolicy.conflictWith(allDay, listOf(sleep))
        )
        assertTrue(PeriodLockPolicy.rangesOverlap(allDay, workdays))
    }

    @Test
    fun overnightOverlapsLateNight() {
        assertEquals(
            PeriodWindowConflict.Overlap,
            PeriodLockPolicy.conflictWith(night, listOf(sleep))
        )
    }

    @Test
    fun workdaysDoNotOverlapSleep() {
        assertNull(PeriodLockPolicy.conflictWith(workdays, listOf(sleep)))
        assertFalse(PeriodLockPolicy.rangesOverlap(sleep, workdays))
    }

    @Test
    fun sameClockDifferentDaysDoNotOverlap() {
        assertNull(PeriodLockPolicy.conflictWith(weekendsSameHours, listOf(workdays)))
        assertFalse(PeriodLockPolicy.rangesOverlap(workdays, weekendsSameHours))
    }

    @Test
    fun adjacentRangesDoNotOverlap() {
        val morning = PeriodWindow(startMinute = 9 * 60, endMinute = 12 * 60)
        val afternoon = PeriodWindow(startMinute = 12 * 60, endMinute = 18 * 60)
        assertFalse(PeriodLockPolicy.rangesOverlap(morning, afternoon))
        assertNull(PeriodLockPolicy.conflictWith(afternoon, listOf(morning)))
    }

    @Test
    fun everydayContainsWeekdays() {
        val everydayWork = workdays.copy(id = "everyday", daysMask = PeriodDays.EVERY_DAY)
        assertEquals(
            PeriodWindowConflict.Overlap,
            PeriodLockPolicy.conflictWith(everydayWork, listOf(workdays))
        )
    }

    @Test
    fun excludeIdIgnoresSelf() {
        assertNull(
            PeriodLockPolicy.conflictWith(
                candidate = sleep.copy(message = "新寄语"),
                existing = listOf(sleep, workdays),
                excludeId = sleep.id
            )
        )
    }

    @Test
    fun hasInternalOverlapDetectsNested() {
        assertTrue(PeriodLockPolicy.hasInternalOverlap(listOf(allDay, workdays)))
        assertFalse(PeriodLockPolicy.hasInternalOverlap(listOf(sleep, workdays)))
        assertFalse(PeriodLockPolicy.hasInternalOverlap(listOf(sleep, sleep.copy(id = "dup"))))
    }

    @Test
    fun doorCopyUsesWindowAndReopenFact() {
        val night = PeriodWindow.defaultSleep()
        val at2300 = at(23, 0)
        assertEquals("这段时间\n先不进去", PeriodLockPolicy.doorTitle(night))
        assertEquals("22:00–07:00", PeriodLockPolicy.doorHero(night))
        assertEquals("每天 · 07:00 打开", PeriodLockPolicy.doorWhenLine(night, at2300))

        val nearEnd = PeriodWindow(startMinute = 22 * 60, endMinute = 23 * 60)
        assertEquals("12 分后打开", PeriodLockPolicy.doorWhenLine(nearEnd, at(22, 48)))

        val day = PeriodWindow.allDay()
        assertEquals("今天\n先不进去", PeriodLockPolicy.doorTitle(day))
        assertEquals("全天", PeriodLockPolicy.doorHero(day))
        assertEquals("明天 0 点打开", PeriodLockPolicy.doorWhenLine(day, at2300))

        assertEquals(
            "工作日 · 18:00 打开",
            PeriodLockPolicy.doorWhenLine(workdays, at(10, 0))
        )

        assertEquals(
            "工作专注\n先不进去",
            PeriodLockPolicy.doorTitle(workdays, scheduleTitle = "工作专注")
        )
        assertEquals(
            "夜间\n先不进去",
            PeriodLockPolicy.doorTitle(day, scheduleTitle = " 夜间 ")
        )
    }

    private fun at(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
