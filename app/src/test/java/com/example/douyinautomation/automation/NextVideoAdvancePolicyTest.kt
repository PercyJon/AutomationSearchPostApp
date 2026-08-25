package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextVideoAdvancePolicyTest {

    @Test
    fun closeSheetBackOnlyWhenTheCommentSheetIsOpen() {
        assertTrue(NextVideoAdvancePolicy.shouldDispatchCloseSheetBack(sheetOpen = true))
        assertFalse(NextVideoAdvancePolicy.shouldDispatchCloseSheetBack(sheetOpen = false))
    }

    @Test
    fun playerFingerprintIsCapturedOnlyOnAClosedSheet() {
        assertTrue(NextVideoAdvancePolicy.canCapturePlayerFingerprint(sheetOpen = false))
        assertFalse(NextVideoAdvancePolicy.canCapturePlayerFingerprint(sheetOpen = true))
    }

    @Test
    fun commentsOpenAfterSwipeOnlyOnAClosedPlayerWithEntry() {
        assertTrue(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterSwipe(
                sheetOpen = false,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterSwipe(
                sheetOpen = true,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterSwipe(
                sheetOpen = false,
                hasVideoSurface = true,
                hasCommentEntry = false,
            ),
        )
    }

    @Test
    fun changedPlayerWithSafeEntryOpensBeforeTheRailCanAutoHide() {
        assertTrue(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterChangedPlayer(
                playerFingerprintChanged = true,
                sheetOpen = false,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterChangedPlayer(
                playerFingerprintChanged = false,
                sheetOpen = false,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldOpenCommentsAfterChangedPlayer(
                playerFingerprintChanged = true,
                sheetOpen = true,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
    }

    @Test
    fun firstScreenRowRatioAcceptsTheOriginalViewportAndRejectsAScrolledList() {
        val baseline = 1067f / 2412f
        assertTrue(
            NextVideoAdvancePolicy.isFirstScreenCommentViewport(
                minRowTopRatio = baseline,
                baselineRowTopRatio = baseline,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.isFirstScreenCommentViewport(
                minRowTopRatio = 1377f / 2412f,
                baselineRowTopRatio = baseline,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.isFirstScreenCommentViewport(
                minRowTopRatio = 0f,
                baselineRowTopRatio = baseline,
            ),
        )
    }

    @Test
    fun firstScreenRowRatioUsesTheNextActionableRowNotAProcessedLeadingRow() {
        val baseline = 1067f / 2412f
        assertFalse(
            NextVideoAdvancePolicy.isFirstScreenCommentViewport(
                minRowTopRatio = 1377f / 2412f,
                baselineRowTopRatio = baseline,
            ),
        )
    }

    @Test
    fun closedPlayerRequiresConsecutiveSamplesAndDoesNotExtraBackOnFlicker() {
        assertEquals(0, NextVideoAdvancePolicy.nextClosedSampleCount(sheetOpen = true, previousClosedSamples = 2))
        assertEquals(3, NextVideoAdvancePolicy.nextClosedSampleCount(sheetOpen = false, previousClosedSamples = 2))
        assertFalse(
            NextVideoAdvancePolicy.isStableClosedPlayer(
                consecutiveClosedSamples = 1,
                requiredSamples = 3,
                sheetOpen = false,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertTrue(
            NextVideoAdvancePolicy.isStableClosedPlayer(
                consecutiveClosedSamples = 3,
                requiredSamples = 3,
                sheetOpen = false,
                hasVideoSurface = false,
                hasCommentEntry = false,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldDispatchFingerprintCloseBack(alreadyDispatchedCloseBack = true),
        )
        assertTrue(
            NextVideoAdvancePolicy.shouldDispatchFingerprintCloseBack(alreadyDispatchedCloseBack = false),
        )
    }

    @Test
    fun swipeRetriesOnlyWhenTheCommentSheetStaysOpen() {
        assertTrue(
            NextVideoAdvancePolicy.shouldRetrySwipe(
                confirmed = false,
                consecutiveSheetOpen = 2,
                sheetOpenRetryThreshold = 2,
                swipeAttempt = 1,
                maxSwipeAttempts = 2,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRetrySwipe(
                confirmed = false,
                consecutiveSheetOpen = 0,
                sheetOpenRetryThreshold = 2,
                swipeAttempt = 1,
                maxSwipeAttempts = 2,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRetrySwipe(
                confirmed = true,
                consecutiveSheetOpen = 2,
                sheetOpenRetryThreshold = 2,
                swipeAttempt = 1,
                maxSwipeAttempts = 2,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRetrySwipe(
                confirmed = false,
                consecutiveSheetOpen = 2,
                sheetOpenRetryThreshold = 2,
                swipeAttempt = 2,
                maxSwipeAttempts = 2,
            ),
        )
    }

    @Test
    fun continuationViewportRetriesUntilTheSwipeBudgetIsSpent() {
        assertTrue(
            NextVideoAdvancePolicy.shouldRetryAfterContinuationViewport(
                firstScreen = false,
                swipeAttempt = 1,
                maxSwipeAttempts = 2,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRetryAfterContinuationViewport(
                firstScreen = true,
                swipeAttempt = 1,
                maxSwipeAttempts = 2,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRetryAfterContinuationViewport(
                firstScreen = false,
                swipeAttempt = 2,
                maxSwipeAttempts = 2,
            ),
        )
    }

    @Test
    fun stableChangedClosedPlayerFingerprintOverridesRetainedCommentScrollPosition() {
        val first = NextVideoAdvancePolicy.nextChangedPlayerSignatureSamples(
            beforeSwipeSignature = 100,
            currentSignature = 200,
            sheetOpen = false,
            previous = NextVideoAdvancePolicy.ChangedPlayerSignatureSamples(null, 0),
        )
        assertFalse(NextVideoAdvancePolicy.isChangedPlayerSignatureStable(first))

        val second = NextVideoAdvancePolicy.nextChangedPlayerSignatureSamples(
            beforeSwipeSignature = 100,
            currentSignature = 200,
            sheetOpen = false,
            previous = first,
        )
        assertTrue(NextVideoAdvancePolicy.isChangedPlayerSignatureStable(second))

        val openSheet = NextVideoAdvancePolicy.nextChangedPlayerSignatureSamples(
            beforeSwipeSignature = 100,
            currentSignature = 200,
            sheetOpen = true,
            previous = second,
        )
        assertFalse(NextVideoAdvancePolicy.isChangedPlayerSignatureStable(openSheet))
    }

    @Test
    fun confirmedPlayerMayRevealOnlyItsHiddenCommentEntry() {
        assertTrue(
            NextVideoAdvancePolicy.shouldRevealHiddenEntryAfterConfirmedPlayer(
                playerChangedConfirmed = true,
                sheetOpen = false,
                hasCommentEntry = false,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRevealHiddenEntryAfterConfirmedPlayer(
                playerChangedConfirmed = false,
                sheetOpen = false,
                hasCommentEntry = false,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRevealHiddenEntryAfterConfirmedPlayer(
                playerChangedConfirmed = true,
                sheetOpen = true,
                hasCommentEntry = false,
            ),
        )
        assertFalse(
            NextVideoAdvancePolicy.shouldRevealHiddenEntryAfterConfirmedPlayer(
                playerChangedConfirmed = true,
                sheetOpen = false,
                hasCommentEntry = true,
            ),
        )
    }
}
