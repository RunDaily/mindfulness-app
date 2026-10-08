package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLimitPolicyTest {

    @Test
    fun maxExtensionIsOneThird() {
        assertEquals(0, SessionLimitPolicy.maxExtensionMinutes(2))
        assertEquals(1, SessionLimitPolicy.maxExtensionMinutes(3))
        assertEquals(5, SessionLimitPolicy.maxExtensionMinutes(15))
    }

    @Test
    fun limitReachedExtensionUsesBrowseRemaining() {
        assertEquals(
            5,
            SessionLimitPolicy.maxLimitReachedExtensionMinutes(15, remainingBrowseMinutes = null)
        )
        assertEquals(
            12,
            SessionLimitPolicy.maxLimitReachedExtensionMinutes(15, remainingBrowseMinutes = 12)
        )
        assertEquals(
            0,
            SessionLimitPolicy.maxLimitReachedExtensionMinutes(15, remainingBrowseMinutes = 0)
        )
        // 不限：退回原时长 1/3
        assertEquals(
            5,
            SessionLimitPolicy.maxLimitReachedExtensionMinutes(
                15,
                remainingBrowseMinutes = Int.MAX_VALUE
            )
        )
    }
}
