package com.life.mindfulnessapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionPathTest {

    @Test
    fun resolve_search() {
        assertEquals(
            CompanionPath.SEARCH,
            CompanionPath.resolve(IntentKind.SEARCH, "退票规则", false)
        )
    }

    @Test
    fun resolve_browseUrge() {
        assertEquals(
            CompanionPath.BROWSE,
            CompanionPath.resolve(IntentKind.URGE, BrowseCasualIntent.LABEL, true)
        )
    }

    @Test
    fun resolve_browseLegacyLabel() {
        assertEquals(
            CompanionPath.BROWSE,
            CompanionPath.resolve(IntentKind.URGE, BrowseCasualIntent.LEGACY_LABEL, true)
        )
        assertEquals(
            BrowseCasualIntent.LABEL,
            BrowseCasualIntent.canonicalLabelOrNull("刷一刷")
        )
        assertEquals(
            BrowseCasualIntent.LABEL,
            BrowseCasualIntent.canonicalLabelOrNull(BrowseCasualIntent.LEGACY_LABEL)
        )
    }

    @Test
    fun resolve_intent() {
        assertEquals(
            CompanionPath.INTENT,
            CompanionPath.resolve(IntentKind.PURPOSEFUL, "回消息", false)
        )
    }

    @Test
    fun markStyle_matchesPath() {
        assertEquals(CapsuleMarkStyle.APP, CapsuleMarkStyle.from(CompanionPath.INTENT))
        assertEquals(CapsuleMarkStyle.TIMER, CapsuleMarkStyle.from(CompanionPath.BROWSE))
        assertEquals(CapsuleMarkStyle.SEARCH, CapsuleMarkStyle.from(CompanionPath.SEARCH))
    }

    @Test
    fun anchorHint_copy() {
        assertEquals(
            "你刚才说：回消息",
            SessionAwarenessCopy.anchorHint(CompanionPath.INTENT, "回消息")
        )
        assertEquals(
            "搜到了吗？",
            SessionAwarenessCopy.anchorHint(CompanionPath.SEARCH, "退票")
        )
        assertEquals(
            "还剩 1 分钟",
            SessionAwarenessCopy.anchorHint(CompanionPath.BROWSE, remainSec = 55L)
        )
    }
}
