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
    fun `overlay stays tappable so pause resume and stop remain reachable`() {
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.WAITING_FOR_USER_RESULTS))
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.SELECTING_USER_RESULT))
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF))
        assertFalse(FloatingOverlayTouchPolicy.shouldDisableTouches(AutomationPhase.COMPLETED_TASK))
    }

    @Test
    fun `primary overlay action follows pause and resume phases`() {
        assertEquals(
            FloatingOverlayPrimaryAction.PAUSE,
            FloatingOverlayControlPolicy.primaryAction(AutomationPhase.WAITING_FOR_USER_RESULTS),
        )
        assertEquals(
            FloatingOverlayPrimaryAction.RESUME,
            FloatingOverlayControlPolicy.primaryAction(AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF),
        )
        assertEquals(
            FloatingOverlayPrimaryAction.RESUME,
            FloatingOverlayControlPolicy.primaryAction(AutomationPhase.SUSPENDED_BEFORE_START),
        )
        assertEquals(
            FloatingOverlayPrimaryAction.NONE,
            FloatingOverlayControlPolicy.primaryAction(AutomationPhase.STOPPED),
        )
        assertEquals("3/8", FloatingOverlayControlPolicy.progressLabel(3, 8))
        assertEquals("已私信 2", FloatingOverlayControlPolicy.detailLabel(2))
        assertEquals("队列 1/2 · 进行中", FloatingOverlayControlPolicy.stageLabel("进行中", "队列 1/2"))
    }

    @Test
    fun `pause does not steal Douyin by opening records`() {
        assertFalse(TaskRecordsOpenPolicy.shouldOpenRecordsTab(AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF))
        assertFalse(TaskRecordsOpenPolicy.shouldOpenRecordsTab(AutomationPhase.WAITING_FOR_PROFILE))
        assertTrue(TaskRecordsOpenPolicy.shouldOpenRecordsTab(AutomationPhase.STOPPED))
        assertTrue(TaskRecordsOpenPolicy.shouldOpenRecordsTab(AutomationPhase.FAILED))
        assertTrue(TaskRecordsOpenPolicy.shouldOpenRecordsTab(AutomationPhase.COMPLETED_TASK))
    }

    @Test
    fun `comment overlay resume hands profile back to comment runtime`() {
        assertTrue(
            CommentOverlayResumePolicy.shouldHandoffToCommentRuntime(
                isCommentPrivateMessageTask = true,
                page = PageKind.USER_PROFILE,
            ),
        )
        assertFalse(
            CommentOverlayResumePolicy.shouldHandoffToCommentRuntime(
                isCommentPrivateMessageTask = false,
                page = PageKind.USER_PROFILE,
            ),
        )
        assertFalse(
            CommentOverlayResumePolicy.shouldHandoffToCommentRuntime(
                isCommentPrivateMessageTask = true,
                page = PageKind.USER_RESULTS,
            ),
        )
    }

    @Test
    fun `unfreeze watchdog follows the frozen comment stage`() {
        assertEquals(
            "等待视频页面",
            CommentRuntimeFreezePolicy.watchdogAfterUnfreeze(CommentEntryStage.WAITING_FOR_VIDEO),
        )
        assertEquals(
            "等待用户主页内容稳定",
            CommentRuntimeFreezePolicy.watchdogAfterUnfreeze(CommentEntryStage.WAITING_FOR_PROFILE),
        )
        assertEquals(
            "等待评论区",
            CommentRuntimeFreezePolicy.watchdogAfterUnfreeze(CommentEntryStage.WAITING_FOR_COMMENTS),
        )
        assertTrue(
            CommentRuntimeFreezePolicy.shouldReplaceWatchdogBeforeClick(CommentEntryAction.OPEN_COMMENTS),
        )
        assertFalse(
            CommentRuntimeFreezePolicy.shouldReplaceWatchdogBeforeClick(CommentEntryAction.NONE),
        )
    }

    @Test
    fun `frozen comment runtime may resume on an unclassified Douyin surface`() {
        val unknown = PageDetection(PageKind.UNKNOWN, 0.2f, listOf("unclassified video"))
        val allowed = CommentOverlayResumePolicy.resumeDecision(
            commentRuntimeFrozen = true,
            detection = unknown,
            pausedPhase = AutomationPhase.WAITING_FOR_PROFILE,
        )
        assertTrue(allowed.allowed)
        assertEquals(AutomationPhase.WAITING_FOR_PROFILE, allowed.phase)

        val rejected = CommentOverlayResumePolicy.resumeDecision(
            commentRuntimeFrozen = false,
            detection = unknown,
            pausedPhase = AutomationPhase.WAITING_FOR_PROFILE,
        )
        assertFalse(rejected.allowed)
        assertEquals(null, rejected.phase)
    }

    @Test
    fun `frozen comment runtime still rejects risk and login pages`() {
        val risk = CommentOverlayResumePolicy.resumeDecision(
            commentRuntimeFrozen = true,
            detection = PageDetection(PageKind.HUMAN_INTERVENTION, 0.9f, listOf("risk")),
            pausedPhase = AutomationPhase.WAITING_FOR_PROFILE,
        )
        assertFalse(risk.allowed)
        val login = CommentOverlayResumePolicy.resumeDecision(
            commentRuntimeFrozen = true,
            detection = PageDetection(PageKind.LOGIN, 0.9f, listOf("login")),
            pausedPhase = AutomationPhase.WAITING_FOR_PROFILE,
        )
        assertFalse(login.allowed)
    }
}
