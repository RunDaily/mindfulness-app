package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBlockPolicyBreathTest {

    private val workFocus = PlanBlock(
        id = "work",
        title = "工作专注",
        startMinute = 9 * 60,
        endMinute = 12 * 60,
        daysMask = PeriodDays.EVERY_DAY,
        enabled = true,
        lockScope = PlanLockScope.MONITORED
    )

    @Test
    fun disabledPlanDoesNotRequireBreath() {
        val off = workFocus.copy(enabled = false)
        assertFalse(PlanBlockPolicy.requiresBreathToCommit(off, off.copy(endMinute = 10 * 60)))
        assertFalse(PlanBlockPolicy.requiresBreathToCommit(off, updated = null))
    }

    @Test
    fun enabledPlanOutsideWindowStillRequiresBreath() {
        // 窗外改掉也是旁路：不依赖此刻是否落在窗内
        assertTrue(PlanBlockPolicy.requiresBreathToCommit(workFocus, updated = null))
        assertTrue(
            PlanBlockPolicy.requiresBreathToCommit(
                workFocus,
                workFocus.copy(endMinute = 10 * 60 + 30)
            )
        )
    }

    @Test
    fun enabledStructuralEditRequiresBreath() {
        assertTrue(
            PlanBlockPolicy.requiresBreathToCommit(
                workFocus,
                workFocus.copy(lockScope = PlanLockScope.SPECIFIC, packageNames = listOf("a"))
            )
        )
    }

    @Test
    fun bothScopeCoversMonitoredAndExtra() {
        val both = workFocus.copy(
            lockScope = PlanLockScope.BOTH,
            packageNames = listOf("com.extra")
        )
        val monitored = setOf("com.tiktok")
        assertTrue(both.coversPackage("com.tiktok", monitored))
        assertTrue(both.coversPackage("com.extra", monitored))
        assertFalse(both.coversPackage("com.other", monitored))
        assertTrue(
            PlanBlockPolicy.watchedPackages(listOf(both), monitored) ==
                setOf("com.tiktok", "com.extra")
        )
    }

    @Test
    fun fromFlagsDerivesBoth() {
        assertTrue(PlanLockScope.fromFlags(true, listOf("a")) == PlanLockScope.BOTH)
        assertTrue(PlanLockScope.fromFlags(true, emptyList()) == PlanLockScope.MONITORED)
        assertTrue(PlanLockScope.fromFlags(false, listOf("a")) == PlanLockScope.SPECIFIC)
        assertTrue(PlanLockScope.fromFlags(false, emptyList()) == null)
    }

    @Test
    fun enabledTitleOnlyDoesNotRequireBreath() {
        assertFalse(
            PlanBlockPolicy.requiresBreathToCommit(
                workFocus,
                workFocus.copy(title = "改名")
            )
        )
    }

    @Test
    fun stopMonitorNeedsBreathWhenEnabledPlanCoversPackage() {
        val monitored = setOf("com.tiktok")
        assertTrue(
            PlanBlockPolicy.needsBreathToStopMonitoring(
                plans = listOf(workFocus),
                packageName = "com.tiktok",
                monitoredPackages = monitored,
                appPeriodLockEnabled = false,
                appPeriodWindows = emptyList()
            )
        )
        assertFalse(
            PlanBlockPolicy.needsBreathToStopMonitoring(
                plans = listOf(workFocus),
                packageName = "com.other",
                monitoredPackages = monitored,
                appPeriodLockEnabled = false,
                appPeriodWindows = emptyList()
            )
        )
    }

    @Test
    fun stopMonitorNeedsBreathWhenAppPeriodArmedOutsideWindow() {
        val sleep = PeriodWindow.defaultSleep() // 23:00–07:00；白天也算已武装
        assertTrue(
            PlanBlockPolicy.needsBreathToStopMonitoring(
                plans = emptyList(),
                packageName = "com.tiktok",
                monitoredPackages = setOf("com.tiktok"),
                appPeriodLockEnabled = true,
                appPeriodWindows = listOf(sleep)
            )
        )
        assertFalse(
            PlanBlockPolicy.needsBreathToStopMonitoring(
                plans = emptyList(),
                packageName = "com.tiktok",
                monitoredPackages = setOf("com.tiktok"),
                appPeriodLockEnabled = true,
                appPeriodWindows = listOf(sleep.copy(enabled = false))
            )
        )
    }
}
