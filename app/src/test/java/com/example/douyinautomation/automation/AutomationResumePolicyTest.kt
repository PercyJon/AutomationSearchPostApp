package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationResumePolicyTest {

    @Test
    fun verifiedProfileResumesAtProfileWait() {
        val decision = AutomationResumePolicy.decide(detection(PageKind.USER_PROFILE))

        assertTrue(decision.allowed)
        assertEquals(AutomationPhase.WAITING_FOR_PROFILE, decision.phase)
    }

    @Test
    fun verifiedHomeResumesAtHomeWait() {
        val decision = AutomationResumePolicy.decide(detection(PageKind.HOME))

        assertTrue(decision.allowed)
        assertEquals(AutomationPhase.WAITING_FOR_HOME, decision.phase)
    }

    @Test
    fun directMessagePageIsSafeAndCompletable() {
        val decision = AutomationResumePolicy.decide(detection(PageKind.DIRECT_MESSAGE))

        assertTrue(decision.allowed)
        assertEquals(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, decision.phase)
    }

    @Test
    fun riskPageNeverResumesAutomatically() {
        val decision = AutomationResumePolicy.decide(detection(PageKind.HUMAN_INTERVENTION))

        assertFalse(decision.allowed)
        assertEquals(null, decision.phase)
    }

    @Test
    fun unknownPageNeverResumesAutomatically() {
        val decision = AutomationResumePolicy.decide(detection(PageKind.UNKNOWN))

        assertFalse(decision.allowed)
        assertEquals(null, decision.phase)
    }

    private fun detection(kind: PageKind) = PageDetection(
        kind = kind,
        confidence = 0.9f,
        reasons = listOf("test fixture"),
    )
}
