package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentCandidateLedgerTest {
    @Test
    fun `overlapping comment viewports are counted once`() {
        val candidate = CommentUserCandidate(
            authorText = "家具小店",
            commentText = "价格多少",
            authorBounds = ScreenBounds(100, 400, 260, 440),
            commentBounds = ScreenBounds(100, 450, 520, 500),
            interactionBounds = ScreenBounds(100, 400, 260, 440),
            matchedKeywords = listOf("价格"),
            identityKey = "comment-user:furniture-shop",
            source = CommentTextSource.ACCESSIBILITY,
        )
        val extraction = CommentCandidateExtraction(candidates = listOf(candidate), fragments = emptyList())
        val ledger = CommentCandidateLedger()

        assertEquals(CommentLedgerUpdate(added = 1, duplicates = 0, total = 1), ledger.add(extraction))
        assertEquals(CommentLedgerUpdate(added = 0, duplicates = 1, total = 1), ledger.add(extraction))
        assertEquals(1, ledger.candidates.size)
    }
}
