package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTabCandidatePolicyTest {

    @Test
    fun acceptsExactFullyVisibleUserTabInsideTabStrip() {
        val candidate = node(text = "用户", left = 180, top = 110, right = 260, bottom = 170)
        val context = context(candidate, tabStrip = node(
            className = "android.widget.HorizontalScrollView",
            left = 0,
            top = 80,
            right = 860,
            bottom = 220,
        ))

        assertTrue(UserTabCandidatePolicy.isSafeCandidate(candidate, selection(candidate), context))
    }

    @Test
    fun rejectsLargeContentContainerWithUserLabel() {
        val candidate = node(text = "用户", left = 0, top = 360, right = 1_000, bottom = 1_300)

        assertFalse(UserTabCandidatePolicy.isSafeCandidate(candidate, selection(candidate), context(candidate)))
    }

    @Test
    fun rejectsAClippedTabOutsideVisibleFallbackViewport() {
        val candidate = node(text = "用户", left = 820, top = 110, right = 920, bottom = 170)

        assertFalse(UserTabCandidatePolicy.isSafeCandidate(candidate, selection(candidate), context(candidate)))
    }

    private fun context(candidate: NodeSnapshot, tabStrip: NodeSnapshot? = null) = ScreenContext(
        screenSize = ScreenSize(1_000, 2_000),
        nodes = listOfNotNull(tabStrip, candidate),
    )

    private fun selection(candidate: NodeSnapshot) = SelectionResult(
        request = DouyinSelectors.userTab,
        node = candidate,
        score = 1f,
        reasons = listOf("label=用户"),
    )

    private fun node(
        text: String? = null,
        className: String? = null,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = NodeSnapshot(
        text = text,
        className = className,
        bounds = ScreenBounds(left, top, right, bottom),
    )
}
