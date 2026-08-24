package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialHomeSurfacePolicyTest {

    @Test
    fun pendingStartupDefersMissingContextOnlyAtInitialHomePhase() {
        assertEquals(
            true,
            InitialHomeSurfacePolicy.shouldDeferMissingContextRecovery(
                phase = AutomationPhase.WAITING_FOR_HOME,
                initialClassificationPending = true,
            ),
        )
        assertEquals(
            false,
            InitialHomeSurfacePolicy.shouldDeferMissingContextRecovery(
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                initialClassificationPending = true,
            ),
        )
        assertEquals(
            false,
            InitialHomeSurfacePolicy.shouldDeferMissingContextRecovery(
                phase = AutomationPhase.WAITING_FOR_HOME,
                initialClassificationPending = false,
            ),
        )
    }

    @Test
    fun pendingStartupDefersOnlyUnknownHomeRecovery() {
        assertEquals(
            true,
            InitialHomeSurfacePolicy.shouldDeferUnknownRecovery(
                phase = AutomationPhase.WAITING_FOR_HOME,
                detectedPage = PageKind.UNKNOWN,
                initialClassificationPending = true,
            ),
        )
        assertEquals(
            false,
            InitialHomeSurfacePolicy.shouldDeferUnknownRecovery(
                phase = AutomationPhase.WAITING_FOR_HOME,
                detectedPage = PageKind.USER_PROFILE,
                initialClassificationPending = true,
            ),
        )
        assertEquals(
            false,
            InitialHomeSurfacePolicy.shouldDeferUnknownRecovery(
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                detectedPage = PageKind.UNKNOWN,
                initialClassificationPending = true,
            ),
        )
    }

    @Test
    fun unknownPageWithVerifiedSearchEntryBecomesHome() {
        val result = InitialHomeSurfacePolicy.normalize(
            detected = detection(PageKind.UNKNOWN),
            hasTransientOverlay = false,
            hasSearchEntryCandidate = true,
            reason = InitialHomeSurfacePolicy.OBSERVATION_REASON,
        )

        assertEquals(PageKind.HOME, result.kind)
        assertEquals(0.78f, result.confidence)
        assertEquals(listOf(InitialHomeSurfacePolicy.OBSERVATION_REASON), result.reasons)
    }

    @Test
    fun transientOverlayKeepsUnknownPageUnchanged() {
        val detected = detection(PageKind.UNKNOWN)

        val result = InitialHomeSurfacePolicy.normalize(
            detected = detected,
            hasTransientOverlay = true,
            hasSearchEntryCandidate = true,
            reason = InitialHomeSurfacePolicy.OBSERVATION_REASON,
        )

        assertEquals(detected, result)
    }

    @Test
    fun recognizedPageKeepsOriginalClassification() {
        val detected = detection(PageKind.USER_PROFILE)

        val result = InitialHomeSurfacePolicy.normalize(
            detected = detected,
            hasTransientOverlay = false,
            hasSearchEntryCandidate = true,
            reason = InitialHomeSurfacePolicy.RECOVERY_REASON,
        )

        assertEquals(detected, result)
    }

    private fun detection(kind: PageKind) = PageDetection(
        kind = kind,
        confidence = 0.42f,
        reasons = listOf("test fixture"),
    )
}
