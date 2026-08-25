package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteTaskSyncQueueTest {
    @Test
    fun `local outcomes map to backend statuses without sending content`() {
        assertEquals(RemoteTaskRecordStatus.PROCESSING, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.IN_PROGRESS))
        assertEquals(RemoteTaskRecordStatus.SUCCESS, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED))
        assertEquals(RemoteTaskRecordStatus.SUCCESS, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.PROFILE_OPENED))
        assertEquals(RemoteTaskRecordStatus.BLOCKED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.FILTERED_BY_KEYWORD))
        assertEquals(RemoteTaskRecordStatus.SKIPPED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.DUPLICATE_SKIPPED))
        assertEquals(RemoteTaskRecordStatus.FAILED, RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.MESSAGE_SEND_FAILED))
    }

    @Test
    fun `lifecycle statuses use the backend task contract`() {
        assertEquals(2, RemoteTaskStatus.RUNNING)
        assertEquals(3, RemoteTaskStatus.PAUSED)
        assertEquals(4, RemoteTaskStatus.COMPLETED)
        assertEquals(5, RemoteTaskStatus.FAILED)
        assertEquals(6, RemoteTaskStatus.CANCELLED)
    }
}
