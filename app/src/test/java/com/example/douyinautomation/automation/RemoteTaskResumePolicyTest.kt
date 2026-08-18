package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteTaskResumePolicyTest {
    @Test
    fun `fresh remote task can start without an anchor`() {
        assertTrue(RemoteTaskResumePolicy.canResumeExactly(progress(processed = 0, key = null)))
        assertFalse(RemoteTaskResumePolicy.requiresAnchor(progress(processed = 0, key = null)))
    }

    @Test
    fun `processed remote task requires a last user key`() {
        assertFalse(RemoteTaskResumePolicy.canResumeExactly(progress(processed = 4, key = null)))
        assertTrue(RemoteTaskResumePolicy.canResumeExactly(progress(processed = 4, key = "handle:abc")))
        assertTrue(RemoteTaskResumePolicy.requiresAnchor(progress(processed = 4, key = "handle:abc")))
    }

    private fun progress(processed: Int, key: String?): RemoteTaskProgress = RemoteTaskProgress(
        taskId = 7L,
        status = 2,
        totalCount = 20,
        processedCount = processed,
        pendingCount = (20 - processed).coerceAtLeast(0),
        successCount = processed,
        failedCount = 0,
        skippedCount = 0,
        completionPercent = processed * 5.0,
        lastUserKey = key,
        lastUserName = key,
        lastPageNumber = 1,
        lastPageFingerprint = null,
        checkpointVersion = 1,
    )
}
