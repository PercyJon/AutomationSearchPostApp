package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedTaskResumePolicyTest {

    @Test
    fun onlyVerifiedInitialNavigationSurfacesCanResumeInPlace() {
        val expectedPhases = mapOf(
            PageKind.HOME to AutomationPhase.WAITING_FOR_HOME,
            PageKind.SEARCH_ENTRY to AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS to AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
            PageKind.USER_RESULTS to AutomationPhase.WAITING_FOR_USER_RESULTS,
        )

        PageKind.values().forEach { page ->
            val actual = SavedTaskResumePolicy.phaseForInPlaceResume(
                forceInitialRestart = false,
                visiblePage = page,
            )

            expectedPhases[page]?.let { expected ->
                assertEquals("Expected $page to remain reusable", expected, actual)
            } ?: assertNull("Expected $page to restart from the initial flow", actual)
        }
    }

    @Test
    fun forcedRestartNeverReusesEvenAVerifiedNavigationSurface() {
        listOf(
            PageKind.HOME,
            PageKind.SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS,
            PageKind.USER_RESULTS,
        ).forEach { page ->
            assertNull(
                "Forced restart must not reuse $page",
                SavedTaskResumePolicy.phaseForInPlaceResume(
                    forceInitialRestart = true,
                    visiblePage = page,
                ),
            )
        }
    }

    @Test
    fun missingVisiblePageRestartsFromTheFrozenInitialFlow() {
        assertNull(
            SavedTaskResumePolicy.phaseForInPlaceResume(
                forceInitialRestart = false,
                visiblePage = null,
            ),
        )
    }
}
