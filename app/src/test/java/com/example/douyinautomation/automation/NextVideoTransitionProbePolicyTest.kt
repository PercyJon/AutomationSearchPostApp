package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextVideoTransitionProbePolicyTest {
    @Test
    fun `changed UNKNOWN next-video surface gets bounded action-rail probe`() {
        assertTrue(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.UNKNOWN,
                hasVideoSurface = false,
                hasCommentEntry = false,
                changedFromPreviousVideo = true,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
    }

    @Test
    fun `unchanged UNKNOWN and unrelated pages do not get action-rail probe`() {
        assertFalse(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.UNKNOWN,
                hasVideoSurface = false,
                hasCommentEntry = false,
                changedFromPreviousVideo = false,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
        assertFalse(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.USER_PROFILE,
                hasVideoSurface = false,
                hasCommentEntry = false,
                changedFromPreviousVideo = true,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
    }

    @Test
    fun `existing HOME and verified-video routes retain bounded probing`() {
        assertTrue(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.HOME,
                hasVideoSurface = false,
                hasCommentEntry = false,
                changedFromPreviousVideo = false,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
        assertTrue(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.UNKNOWN,
                hasVideoSurface = true,
                hasCommentEntry = false,
                changedFromPreviousVideo = false,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
    }

    @Test
    fun `verified entry and exhausted budget never trigger another probe`() {
        assertFalse(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.UNKNOWN,
                hasVideoSurface = false,
                hasCommentEntry = true,
                changedFromPreviousVideo = true,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
            ),
        )
        assertFalse(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.UNKNOWN,
                hasVideoSurface = false,
                hasCommentEntry = false,
                changedFromPreviousVideo = true,
                ocrProbeCount = 2,
                ocrProbeLimit = 2,
            ),
        )
        assertFalse(
            NextVideoTransitionProbePolicy.shouldProbeActionRail(
                page = PageKind.HOME,
                hasVideoSurface = true,
                hasCommentEntry = false,
                changedFromPreviousVideo = true,
                ocrProbeCount = 0,
                ocrProbeLimit = 2,
                sheetOpen = true,
            ),
        )
    }

    @Test
    fun `swipe watchdog is replaced only once when probing begins`() {
        assertTrue(
            NextVideoTransitionProbePolicy.shouldReplaceSwipeWatchdog(
                actionRailProbeWillRun = true,
                watchdogAlreadyReplaced = false,
            ),
        )
        assertFalse(
            NextVideoTransitionProbePolicy.shouldReplaceSwipeWatchdog(
                actionRailProbeWillRun = true,
                watchdogAlreadyReplaced = true,
            ),
        )
        assertFalse(
            NextVideoTransitionProbePolicy.shouldReplaceSwipeWatchdog(
                actionRailProbeWillRun = false,
                watchdogAlreadyReplaced = false,
            ),
        )
    }
}
