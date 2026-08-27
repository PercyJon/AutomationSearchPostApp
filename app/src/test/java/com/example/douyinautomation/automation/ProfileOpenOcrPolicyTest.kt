package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOpenOcrPolicyTest {

    @Test
    fun bypassesUnknownPageOcrOnlyWhileOpeningProfileOrMessage() {
        assertTrue(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.WAITING_FOR_PROFILE))
        assertTrue(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.OPENING_MESSAGE_ENTRY))
        assertTrue(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE))
        assertFalse(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.WAITING_FOR_HOME))
        assertFalse(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.WAITING_FOR_USER_RESULTS))
        assertFalse(ProfileOpenOcrPolicy.shouldBypassUnknownPageOcr(AutomationPhase.VERIFYING_EMPTY_MESSAGE))
    }
}
