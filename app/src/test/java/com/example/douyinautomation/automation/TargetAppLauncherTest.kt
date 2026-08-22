package com.example.douyinautomation.automation

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetAppLauncherTest {
    @Test
    fun `launch flags preserve an existing Douyin task instead of resetting it to home`() {
        val unrelatedFlag = Intent.FLAG_ACTIVITY_CLEAR_TOP
        val result = TargetAppLauncher.launchFlagsPreservingTask(
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or unrelatedFlag,
        )

        assertEquals(0, result and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        assertTrue(result and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(result and unrelatedFlag != 0)
    }
}
