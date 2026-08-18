package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteTaskSyncQueueTest {
    @Test
    fun `local outcomes map to backend statuses without sending content`() {
        assertEquals(RemoteTaskRecordStatus.PROCESSING, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.IN_PROGRESS))
        assertEquals(RemoteTaskRecordStatus.SUCCESS, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED))
        assertEquals(RemoteTaskRecordStatus.BLOCKED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.FILTERED_BY_KEYWORD))
        assertEquals(RemoteTaskRecordStatus.SKIPPED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.DUPLICATE_SKIPPED))
        assertEquals(RemoteTaskRecordStatus.FAILED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.MESSAGE_SEND_FAILED))
    }
}

