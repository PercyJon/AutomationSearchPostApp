package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdatePolicyTest {
    @Test
    fun `idle and completed phases do not defer update`() {
        assertFalse(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.IDLE))
        assertFalse(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.COMPLETED_TASK))
        assertFalse(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.FAILED))
        assertFalse(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.COMPLETED_MESSAGE_SENT))
    }

    @Test
    fun `running and paused phases defer update`() {
        assertTrue(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.WAITING_FOR_HOME))
        assertTrue(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.VERIFYING_EMPTY_MESSAGE))
        assertTrue(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF))
        assertTrue(AppUpdatePolicy.shouldDeferForRunningTask(AutomationPhase.SUSPENDED_BEFORE_START))
    }

    @Test
    fun `force update is ignored while a task is deferred`() {
        assertTrue(AppUpdatePolicy.effectiveForce(serverForce = true, taskDeferred = false))
        assertFalse(AppUpdatePolicy.effectiveForce(serverForce = true, taskDeferred = true))
        assertFalse(AppUpdatePolicy.effectiveForce(serverForce = false, taskDeferred = false))
    }
}
