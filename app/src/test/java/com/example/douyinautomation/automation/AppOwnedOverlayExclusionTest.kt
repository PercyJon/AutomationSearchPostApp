package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppOwnedOverlayExclusionTest {
    @Test
    fun `ocr blocks overlapping app overlay are excluded`() {
        AppOwnedOverlayExclusion.update(ScreenBounds(800, 100, 1000, 300))
        val visible = OcrTextBlock("关注", ScreenBounds(400, 700, 520, 760))
        val overlay = OcrTextBlock("展开自动化进度", ScreenBounds(820, 120, 980, 220))

        assertEquals(listOf(visible), AppOwnedOverlayExclusion.filterOcrBlocks(listOf(visible, overlay)))
        assertTrue(AppOwnedOverlayExclusion.excludes(overlay.bounds))
        assertFalse(AppOwnedOverlayExclusion.excludes(visible.bounds))
        AppOwnedOverlayExclusion.clear()
    }

    @Test
    fun `overlay bounds clear when service stops`() {
        AppOwnedOverlayExclusion.update(ScreenBounds(800, 100, 1000, 300))
        AppOwnedOverlayExclusion.clear()

        assertFalse(AppOwnedOverlayExclusion.excludes(ScreenBounds(820, 120, 980, 220)))
    }

    @Test
    fun `overlay remains visible but becomes non touchable during automation`() {
        assertTrue(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.WAITING_FOR_USER_RESULTS))
        assertTrue(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.SELECTING_USER_RESULT))
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF))
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.COMPLETED_TASK))
    }
}
