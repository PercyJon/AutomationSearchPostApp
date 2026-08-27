package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTaskRecordOutcomeTest {
    @Test
    fun `real send and blank probe both count as messaged overlay progress`() {
        assertTrue(UserTaskRecord.Outcome.MESSAGE_SENT.countsAsMessaged())
        assertTrue(UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED.countsAsMessaged())
        assertTrue(UserTaskRecord.Outcome.PROFILE_OPENED.countsAsMessaged())
        assertFalse(UserTaskRecord.Outcome.IN_PROGRESS.countsAsMessaged())
        assertFalse(UserTaskRecord.Outcome.MESSAGE_SEND_FAILED.countsAsMessaged())
    }

    @Test
    fun `real send maps to remote success like a verified blank probe`() {
        assertEquals(
            RemoteTaskRecordStatus.SUCCESS,
            RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.MESSAGE_SENT),
        )
    }
}
