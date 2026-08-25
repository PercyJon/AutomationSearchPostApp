package com.example.douyinautomation.automation

import kotlin.math.abs

/**
 * Pure gates for finishing one comment-task video and moving to the next feed item.
 *
 * These never authorize a tap. A new video is confirmed from the comment sheet's first-screen
 * row position (screen ratio), not from a whole-tree fingerprint. Accounting stays unchanged
 * until that first-screen viewport is seen.
 */
object NextVideoAdvancePolicy {

    /**
     * Topmost first-screen comment row on this profile player. 1067px / 2412px ≈ 0.44;
     * a list that continued to 1377px ≈ 0.57 is not a new video.
     */
    const val FIRST_SCREEN_MAX_ROW_TOP_RATIO = 0.52f

    /** Allowed drift from the first video's recorded first-screen row. */
    const val FIRST_SCREEN_ROW_TOP_DELTA_RATIO = 0.10f

    /** A later viewport on the same list sits further down the sheet. */
    const val CONTINUATION_MIN_DOWNWARD_DELTA_RATIO = 0.08f

    fun shouldDispatchCloseSheetBack(sheetOpen: Boolean): Boolean = sheetOpen

    fun canCapturePlayerFingerprint(sheetOpen: Boolean): Boolean = !sheetOpen

    fun minRowTopRatio(minRowTopPx: Int, screenHeight: Int): Float? {
        if (screenHeight <= 0 || minRowTopPx < 0 || minRowTopPx == Int.MAX_VALUE) return null
        return minRowTopPx.toFloat() / screenHeight.toFloat()
    }

    fun isFirstScreenCommentViewport(
        minRowTopRatio: Float,
        baselineRowTopRatio: Float?,
    ): Boolean {
        val baseline = baselineRowTopRatio
        if (baseline != null) {
            return abs(minRowTopRatio - baseline) <= FIRST_SCREEN_ROW_TOP_DELTA_RATIO
        }
        return minRowTopRatio <= FIRST_SCREEN_MAX_ROW_TOP_RATIO
    }

    fun isContinuationCommentViewport(
        minRowTopRatio: Float,
        previousMinRowTopRatio: Float?,
    ): Boolean {
        val previous = previousMinRowTopRatio
            ?: return minRowTopRatio > FIRST_SCREEN_MAX_ROW_TOP_RATIO
        return minRowTopRatio >= previous + CONTINUATION_MIN_DOWNWARD_DELTA_RATIO
    }

    fun shouldOpenCommentsAfterSwipe(
        sheetOpen: Boolean,
        hasVideoSurface: Boolean,
        hasCommentEntry: Boolean,
    ): Boolean = !sheetOpen && hasVideoSurface && hasCommentEntry

    /**
     * A closed-player tree that changed after the pager swipe plus a currently safe comment
     * entry is enough to open the sheet. Waiting for another fingerprint frame can let Douyin
     * auto-hide the rail before the entry is actioned.
     */
    fun shouldOpenCommentsAfterChangedPlayer(
        playerFingerprintChanged: Boolean,
        sheetOpen: Boolean,
        hasVideoSurface: Boolean,
        hasCommentEntry: Boolean,
    ): Boolean = playerFingerprintChanged &&
        shouldOpenCommentsAfterSwipe(sheetOpen, hasVideoSurface, hasCommentEntry)

    /**
     * One closed-sheet sample is not enough: the detector can report already_closed while the
     * sheet is still animating, then reopen. Swipe only after consecutive closed-player frames.
     */
    const val CLOSED_PLAYER_STABLE_SAMPLES = 3

    fun nextClosedSampleCount(sheetOpen: Boolean, previousClosedSamples: Int): Int =
        if (sheetOpen) 0 else previousClosedSamples + 1

    fun shouldDispatchFingerprintCloseBack(alreadyDispatchedCloseBack: Boolean): Boolean =
        !alreadyDispatchedCloseBack

    fun isStableClosedPlayer(
        consecutiveClosedSamples: Int,
        requiredSamples: Int,
        sheetOpen: Boolean,
        @Suppress("UNUSED_PARAMETER") hasVideoSurface: Boolean,
        @Suppress("UNUSED_PARAMETER") hasCommentEntry: Boolean,
    ): Boolean = !sheetOpen && consecutiveClosedSamples >= requiredSamples

    fun shouldRetryAfterContinuationViewport(
        firstScreen: Boolean,
        swipeAttempt: Int,
        maxSwipeAttempts: Int,
    ): Boolean = !firstScreen && swipeAttempt < maxSwipeAttempts

    /**
     * Confirm the next video on its first comment sheet. Pagination from the video just
     * finished must not skip this gate; callers reset that leftover scroll before the swipe.
     */
    fun shouldEvaluateNextVideoViewportGate(
        awaitingConfirmation: Boolean,
        commentListScrollCount: Int,
    ): Boolean = awaitingConfirmation && commentListScrollCount == 0

    /** Comment-list pagination belongs to the video being left, not the sheet after the swipe. */
    fun commentListScrollCountAfterLeavingVideo(): Int = 0

    /**
     * Closing the current sheet must not stay in READY_TO_READ. Accessibility events from the
     * disappearing panel would otherwise keep triggering comment OCR and stretch the 150ms
     * close poll to seconds.
     */
    fun shouldArmWaitingForVideoBeforeSheetClose(): Boolean = true

    data class ChangedPlayerSignatureSamples(
        val signature: Int?,
        val count: Int,
    )

    /**
     * A changed player tree must remain identical for two closed-player samples before it can
     * prove a new work. Comment-sheet row position is deliberately excluded because Douyin may
     * retain the sheet's scroll offset after a real pager transition.
     */
    fun nextChangedPlayerSignatureSamples(
        beforeSwipeSignature: Int?,
        currentSignature: Int?,
        sheetOpen: Boolean,
        previous: ChangedPlayerSignatureSamples,
    ): ChangedPlayerSignatureSamples {
        if (
            sheetOpen ||
            beforeSwipeSignature == null ||
            currentSignature == null ||
            currentSignature == beforeSwipeSignature
        ) {
            return ChangedPlayerSignatureSamples(signature = null, count = 0)
        }
        return if (previous.signature == currentSignature) {
            ChangedPlayerSignatureSamples(currentSignature, previous.count + 1)
        } else {
            ChangedPlayerSignatureSamples(currentSignature, count = 1)
        }
    }

    fun isChangedPlayerSignatureStable(samples: ChangedPlayerSignatureSamples): Boolean =
        samples.count >= 2

    /**
     * A confirmed new player can auto-hide its right rail between the fingerprint proof and the
     * next read. Only its central neutral media canvas may be tapped once to reveal controls.
     */
    fun shouldRevealHiddenEntryAfterConfirmedPlayer(
        playerChangedConfirmed: Boolean,
        sheetOpen: Boolean,
        hasCommentEntry: Boolean,
    ): Boolean = playerChangedConfirmed && !sheetOpen && !hasCommentEntry

    fun shouldRetrySwipe(
        confirmed: Boolean,
        consecutiveSheetOpen: Int,
        sheetOpenRetryThreshold: Int,
        swipeAttempt: Int,
        maxSwipeAttempts: Int,
    ): Boolean = !confirmed &&
        swipeAttempt < maxSwipeAttempts &&
        consecutiveSheetOpen >= sheetOpenRetryThreshold
}
