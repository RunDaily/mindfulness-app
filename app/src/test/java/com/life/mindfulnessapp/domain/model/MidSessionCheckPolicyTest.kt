package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidSessionCheckPolicyTest {

    @Test
    fun eligible_requiresIntentGate() {
        assertFalse(
            MidSessionCheckPolicy.isEligible(
                intentGate = false,
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 15
            )
        )
    }

    @Test
    fun eligible_requiresPracticeEnabled() {
        assertFalse(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 15,
                practiceEnabled = false
            )
        )
        assertTrue(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 15,
                practiceEnabled = true
            )
        )
    }

    @Test
    fun eligible_allowsSearch() {
        assertTrue(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.SEARCH,
                sessionLimitMinutes = 0
            )
        )
    }

    @Test
    fun eligible_skipsBrowseUrge() {
        assertFalse(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.URGE,
                sessionLimitMinutes = 15,
                purpose = BrowseCasualIntent.LABEL,
                hasSessionLimit = true
            )
        )
    }

    @Test
    fun eligible_skipsShortTimedSessions() {
        assertFalse(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 5
            )
        )
        assertTrue(
            MidSessionCheckPolicy.isEligible(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 10
            )
        )
    }

    @Test
    fun nextGap_followsRhythm() {
        assertEquals(5 * 60L, MidSessionCheckPolicy.nextGapSec(0, gapSec = 5 * 60L))
        assertEquals(10 * 60L, MidSessionCheckPolicy.nextGapSec(0, gapSec = 10 * 60L))
        assertEquals(5 * 60L, MidSessionCheckPolicy.nextGapSec(1, gapSec = 5 * 60L))
        assertEquals(10 * 60L, MidSessionCheckPolicy.nextGapSec(2, gapSec = 10 * 60L))
    }

    @Test
    fun shouldTrigger_respectsQuietWindow() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 3 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0
            )
        )
    }

    @Test
    fun shouldTrigger_atFirstGapFiveMinutes() {
        assertTrue(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 5 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0
            )
        )
    }

    @Test
    fun shouldTrigger_sparseAtTenMinutes() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 5 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0,
                gapSec = MidSessionCheckPolicy.GAP_SPARSE_SEC
            )
        )
        assertTrue(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 10 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0,
                gapSec = MidSessionCheckPolicy.GAP_SPARSE_SEC
            )
        )
    }

    @Test
    fun shouldTrigger_offWhenPracticeDisabled() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 5 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0,
                practiceEnabled = false
            )
        )
    }

    @Test
    fun shouldTrigger_searchAtFiveMinutes() {
        assertTrue(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.SEARCH,
                sessionSeconds = 5 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0
            )
        )
    }

    @Test
    fun shouldTrigger_skipsWhenSessionAlmostDone() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 10 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 11,
                sessionLimitSec = 11 * 60L
            )
        )
    }

    @Test
    fun shouldTrigger_skipsDailyUrgent() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerNow(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 5 * 60L,
                completedChecks = 0,
                lastCheckAtSec = 0L,
                sessionLimitMinutes = 0,
                dailyRemainingSec = 4 * 60L,
                timeLockEnabled = true
            )
        )
    }

    @Test
    fun enterAnchorHint_intentOnlyAtThirtySeconds() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 20L,
                alreadyShown = false
            )
        )
        assertTrue(
            MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 30L,
                alreadyShown = false
            )
        )
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                sessionSeconds = 30L,
                alreadyShown = true
            )
        )
    }

    @Test
    fun enterAnchorHint_skipsSearchAndBrowse() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
                intentGate = true,
                intentKind = IntentKind.SEARCH,
                sessionSeconds = 30L,
                alreadyShown = false
            )
        )
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
                intentGate = true,
                intentKind = IntentKind.URGE,
                sessionSeconds = 30L,
                alreadyShown = false,
                purpose = BrowseCasualIntent.LABEL,
                hasSessionLimit = true
            )
        )
    }

    @Test
    fun browseNearEndHint_alwaysFalse() {
        assertFalse(
            MidSessionCheckPolicy.shouldTriggerBrowseNearEndHint(
                intentGate = true,
                intentKind = IntentKind.URGE,
                purpose = BrowseCasualIntent.LABEL,
                hasSessionLimit = true,
                sessionRemainSec = 55L,
                alreadyShown = false
            )
        )
    }

    @Test
    fun deferSearchCapsule_firstThirtySeconds() {
        assertTrue(
            MidSessionCheckPolicy.shouldDeferSearchCapsule(
                intentGate = true,
                intentKind = IntentKind.SEARCH,
                purpose = "搜资料",
                hasSessionLimit = false,
                sessionSeconds = 10L
            )
        )
        assertFalse(
            MidSessionCheckPolicy.shouldDeferSearchCapsule(
                intentGate = true,
                intentKind = IntentKind.SEARCH,
                purpose = "搜资料",
                hasSessionLimit = false,
                sessionSeconds = 30L
            )
        )
        assertFalse(
            MidSessionCheckPolicy.shouldDeferSearchCapsule(
                intentGate = true,
                intentKind = IntentKind.PURPOSEFUL,
                purpose = "看数据",
                hasSessionLimit = false,
                sessionSeconds = 10L
            )
        )
    }
}
