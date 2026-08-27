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
    fun `user quota only counts a verified private-message probe or a real send`() {
        assertTrue(UserTaskRecord.Outcome.MESSAGE_SENT.countsTowardUserQuota())
        assertTrue(UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.PROFILE_OPENED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.MESSAGE_SEND_FAILED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.FILTERED_BY_KEYWORD.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.DUPLICATE_SKIPPED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.IN_PROGRESS.countsTowardUserQuota())
    }

    @Test
    fun `user count quota only includes successful private messages`() {
        assertTrue(UserTaskRecord.Outcome.MESSAGE_SENT.countsTowardUserQuota())
        assertTrue(UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.PROFILE_OPENED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.MESSAGE_SEND_FAILED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.FILTERED_BY_KEYWORD.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.DUPLICATE_SKIPPED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED.countsTowardUserQuota())
        assertFalse(UserTaskRecord.Outcome.IN_PROGRESS.countsTowardUserQuota())
    }

    @Test
    fun `real send maps to remote success like a verified blank probe`() {
        assertEquals(
            RemoteTaskRecordStatus.SUCCESS,
            RemoteTaskRecordStatus.from(UserTaskRecord.Outcome.MESSAGE_SENT),
        )
    }
}
