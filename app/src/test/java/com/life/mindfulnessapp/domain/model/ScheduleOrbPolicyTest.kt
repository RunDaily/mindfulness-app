package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleOrbPolicyTest {

    @Test
    fun activeGlanceOnlyWhenLockedNow() {
        val work = PlanBlock(
            id = "work",
            title = "工作专注",
            startMinute = 9 * 60,
            endMinute = 12 * 60,
            daysMask = PeriodDays.EVERY_DAY,
            lockScope = PlanLockScope.SPECIFIC,
            packageNames = listOf("com.demo.a", "com.demo.b"),
        )
        assertNull(ScheduleOrbPolicy.activeGlance(listOf(work), nowMillis = at(8, 30)))
        val active = ScheduleOrbPolicy.activeGlance(
            plans = listOf(work),
            monitoredPackages = emptySet(),
            appLabel = { pkg -> if (pkg == "com.demo.a") "抖音" else "微博" },
            nowMillis = at(10, 0),
        )
        assertEquals("工作专注", active?.title)
        assertEquals("work", active?.planId)
        assertEquals("09:00 – 12:00", active?.timeLabel)
        assertEquals(2, active?.lockedApps?.size)
        assertEquals("抖音", active?.lockedApps?.first()?.appName)
        assertEquals(0, active?.lockedAppOverflow)
    }

    @Test
    fun monitoredScopeListsEnabledApps() {
        val night = PlanBlock(
            id = "night",
            title = "夜间",
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            daysMask = PeriodDays.EVERY_DAY,
            lockScope = PlanLockScope.MONITORED,
        )
        val active = ScheduleOrbPolicy.activeGlance(
            plans = listOf(night),
            monitoredPackages = setOf("com.a", "com.b", "com.c", "com.d", "com.e"),
            appLabel = { it.removePrefix("com.") },
            nowMillis = at(23, 0),
        )
        assertEquals("22:00 – 07:00", active?.timeLabel)
        assertEquals(ScheduleOrbPolicy.MAX_SHOWN_APPS, active?.lockedApps?.size)
        assertEquals(1, active?.lockedAppOverflow)
        assertNull(active?.emptyScopeHint)
    }

    @Test
    fun monitoredEmptyShowsHint() {
        val night = PlanBlock(
            id = "night",
            title = "夜间",
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            daysMask = PeriodDays.EVERY_DAY,
            lockScope = PlanLockScope.MONITORED,
        )
        val active = ScheduleOrbPolicy.activeGlance(
            plans = listOf(night),
            monitoredPackages = emptySet(),
            nowMillis = at(23, 0),
        )
        assertTrue(active?.lockedApps.isNullOrEmpty())
        assertEquals("监控中的 App", active?.emptyScopeHint)
    }

    private fun at(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
