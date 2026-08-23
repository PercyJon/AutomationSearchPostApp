package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentCandidateResumeLedgerTest {
    @Test
    fun `restored fingerprints prevent a repeated candidate after process recreation`() {
        assertEquals("comment-user:Aa".hashCode(), "comment-user:BB".hashCode())
        val firstRun = CommentCandidateResumeLedger()
        val persisted = requireNotNull(firstRun.markProcessed("comment-user:Aa"))
        assertNull(firstRun.markProcessed("comment-user:Aa"))

        val resumedRun = CommentCandidateResumeLedger()
        resumedRun.restore(setOf(persisted))

        assertTrue(resumedRun.contains("comment-user:Aa"))
        assertNull(resumedRun.markProcessed("comment-user:Aa"))
        assertFalse(resumedRun.contains("comment-user:BB"))
        assertNotNull(resumedRun.markProcessed("comment-user:BB"))
        assertEquals(2, resumedRun.snapshot().size)
    }
}
