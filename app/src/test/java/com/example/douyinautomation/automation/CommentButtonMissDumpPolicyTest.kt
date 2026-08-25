package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentButtonMissDumpPolicyTest {
    @Test
    fun missingRailOnAClosedPlayerStillDumps() {
        assertTrue(
            CommentButtonMissDumpPolicy.shouldDump(
                hasVideoSurface = true,
                hasCommentEntry = false,
                commentSurfaceReady = false,
            ),
        )
    }

    @Test
    fun readyCommentSheetDoesNotDumpTheCoveredRail() {
        assertFalse(
            CommentButtonMissDumpPolicy.shouldDump(
                hasVideoSurface = true,
                hasCommentEntry = false,
                commentSurfaceReady = true,
            ),
        )
    }

    @Test
    fun verifiedCommentEntryAndNonVideoPagesDoNotDump() {
        assertFalse(
            CommentButtonMissDumpPolicy.shouldDump(
                hasVideoSurface = true,
                hasCommentEntry = true,
                commentSurfaceReady = false,
            ),
        )
        assertFalse(
            CommentButtonMissDumpPolicy.shouldDump(
                hasVideoSurface = false,
                hasCommentEntry = false,
                commentSurfaceReady = false,
            ),
        )
    }
}
