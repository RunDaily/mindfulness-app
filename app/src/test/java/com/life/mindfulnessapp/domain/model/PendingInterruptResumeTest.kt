package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingInterruptResumeTest {

    private fun interrupt(
        purpose: String?,
        intentKind: IntentKind?,
        sessionLimitMinutes: Int = 0,
        endReason: String = UsageRecordEntity.EndReason.AWAY_COUNTDOWN,
        endedAt: Long = System.currentTimeMillis()
    ) = PendingInterrupt(
        packageName = "com.example.app",
        recordId = 1L,
        appName = "示例",
        endReason = endReason,
        purpose = purpose,
        intentKind = intentKind,
        sessionLimitMinutes = sessionLimitMinutes,
        durationSeconds = 60L,
        endedAt = endedAt
    )

    @Test
    fun namedIntent_offersStrongResume() {
        val p = interrupt("回消息", IntentKind.PURPOSEFUL)
        assertTrue(PendingInterrupt.isNamedIntentOrSearch("回消息", IntentKind.PURPOSEFUL))
        assertTrue(p.isStrongResumeEligible())
        assertEquals("回消息", p.gateResumePurposeLabel())
    }

    @Test
    fun search_offersStrongResume() {
        val p = interrupt("日式咖喱", IntentKind.SEARCH)
        assertTrue(PendingInterrupt.isNamedIntentOrSearch("日式咖喱", IntentKind.SEARCH))
        assertTrue(p.isStrongResumeEligible())
        assertEquals("日式咖喱", p.gateResumePurposeLabel())
    }

    @Test
    fun browseCasual_noStrongResume() {
        assertFalse(
            PendingInterrupt.isNamedIntentOrSearch(
                BrowseCasualIntent.LABEL,
                IntentKind.URGE,
                sessionLimitMinutes = 15
            )
        )
        assertFalse(
            interrupt(BrowseCasualIntent.LABEL, IntentKind.URGE, sessionLimitMinutes = 15)
                .isStrongResumeEligible()
        )
    }

    @Test
    fun browseLegacyLabel_noStrongResume() {
        assertFalse(
            PendingInterrupt.isNamedIntentOrSearch(
                BrowseCasualIntent.LEGACY_LABEL,
                IntentKind.URGE,
                sessionLimitMinutes = 10
            )
        )
    }

    @Test
    fun emptyPurpose_noStrongResume() {
        assertFalse(PendingInterrupt.isNamedIntentOrSearch("", IntentKind.PURPOSEFUL))
        assertFalse(interrupt(null, IntentKind.PURPOSEFUL).isStrongResumeEligible())
    }

    @Test
    fun manualEnd_noStrongResume() {
        assertFalse(
            interrupt("回消息", IntentKind.PURPOSEFUL, endReason = UsageRecordEntity.EndReason.MANUAL)
                .isStrongResumeEligible()
        )
    }

    @Test
    fun expired_noStrongResume() {
        val old = System.currentTimeMillis() - PendingInterrupt.RESUME_CONFIRM_MAX_AGE_MS - 1
        assertFalse(
            interrupt("回消息", IntentKind.PURPOSEFUL, endedAt = old).isStrongResumeEligible()
        )
    }

    @Test
    fun gateLabel_truncatesLongPurpose() {
        val long = "整理相册里去年夏天去海边拍的那一批照片"
        val label = interrupt(long, IntentKind.PURPOSEFUL).gateResumePurposeLabel()
        assertTrue(label.endsWith("…"))
        assertTrue(label.length <= 16)
    }
}
