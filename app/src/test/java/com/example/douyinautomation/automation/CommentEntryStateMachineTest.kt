package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentEntryStateMachineTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `live room is exited then live item is swiped without opening it`() {
        val machine = CommentEntryStateMachine()

        val exit = machine.observe(CommentEntryObservation(PageKind.LIVE_ROOM_SESSION))
        assertEquals(CommentEntryAction.EXIT_LIVE_ROOM, exit.action)

        val swipe = machine.observe(CommentEntryObservation(PageKind.LIVE_ROOM))
        assertEquals(CommentEntryAction.SWIPE_LIVE_ROOM, swipe.action)
    }

    @Test
    fun `profile observation opens first video only after structural target is present`() {
        val machine = CommentEntryStateMachine()

        val waiting = machine.observe(CommentEntryObservation(PageKind.USER_PROFILE))
        assertEquals(CommentEntryAction.NONE, waiting.action)

        val open = machine.observe(
            CommentEntryObservation(PageKind.USER_PROFILE, hasFirstVideoTarget = true),
        )
        assertEquals(CommentEntryStage.WAITING_FOR_VIDEO, open.stage)
        assertEquals(CommentEntryAction.OPEN_FIRST_VIDEO, open.action)
    }

    @Test
    fun `video must expose a comment entry before opening comments`() {
        val machine = CommentEntryStateMachine()
        machine.observe(CommentEntryObservation(PageKind.USER_PROFILE, hasFirstVideoTarget = true))

        val waiting = machine.observe(
            CommentEntryObservation(page = PageKind.UNKNOWN, hasVideoSurface = true),
        )
        assertEquals(CommentEntryAction.NONE, waiting.action)

        val open = machine.observe(
            CommentEntryObservation(
                page = PageKind.UNKNOWN,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )
        assertEquals(CommentEntryStage.WAITING_FOR_COMMENTS, open.stage)
        assertEquals(CommentEntryAction.OPEN_COMMENTS, open.action)
    }

    @Test
    fun `next video transition reopens the route from a verified video surface`() {
        val machine = CommentEntryStateMachine()

        machine.prepareNextVideo()
        val decision = machine.observe(
            CommentEntryObservation(
                page = PageKind.UNKNOWN,
                hasVideoSurface = true,
                hasCommentEntry = true,
            ),
        )

        assertEquals(CommentEntryStage.WAITING_FOR_COMMENTS, decision.stage)
        assertEquals(CommentEntryAction.OPEN_COMMENTS, decision.action)
    }

    @Test
    fun `comment surface readiness is required before reading`() {
        val machine = CommentEntryStateMachine()
        machine.observe(CommentEntryObservation(PageKind.USER_PROFILE, hasFirstVideoTarget = true))
        machine.observe(CommentEntryObservation(PageKind.UNKNOWN, hasVideoSurface = true, hasCommentEntry = true))

        val waiting = machine.observe(
            CommentEntryObservation(
                page = PageKind.UNKNOWN,
                hasCommentEntry = true,
                commentSurface = CommentSurfaceDetection(false, 0.4f, listOf("loading")),
            ),
        )
        assertEquals(CommentEntryAction.NONE, waiting.action)

        val ready = machine.observe(
            CommentEntryObservation(
                page = PageKind.UNKNOWN,
                hasCommentEntry = true,
                commentSurface = CommentSurfaceDetection(true, 0.92f, listOf("composer")),
            ),
        )
        assertEquals(CommentEntryStage.READY_TO_READ, ready.stage)
        assertEquals(CommentEntryAction.READ_COMMENTS, ready.action)
    }

    @Test
    fun `risk and login always pause instead of attempting a click`() {
        val machine = CommentEntryStateMachine()

        val risk = machine.observe(CommentEntryObservation(PageKind.HUMAN_INTERVENTION))
        assertEquals(CommentEntryStage.PAUSED_FOR_MANUAL_HANDOFF, risk.stage)
        assertEquals(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, risk.action)

        machine.reset()
        val login = machine.observe(CommentEntryObservation(PageKind.LOGIN))
        assertEquals(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, login.action)
        assertTrue(login.reason.contains("登录"))
    }

    @Test
    fun `timeout stops the route and can be reset for a new profile`() {
        val machine = CommentEntryStateMachine()

        val failed = machine.timeout("评论区加载超时")
        assertEquals(CommentEntryStage.FAILED, failed.stage)
        assertEquals(CommentEntryAction.STOP, failed.action)

        machine.reset()
        assertEquals(CommentEntryStage.WAITING_FOR_PROFILE, machine.stage)
    }

    @Test
    fun `signal detector recognizes a profile image target without using coordinates`() {
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品 33"),
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    bounds = ScreenBounds(40, 900, 520, 1420),
                    isClickable = true,
                ),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "佛山红木家具"),
                NodeSnapshot(text = "抖音号：demo"),
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertEquals(PageKind.USER_PROFILE, observation.page)
        assertTrue(observation.hasFirstVideoTarget)
    }

    @Test
    fun `signal detector exposes the selected first video node`() {
        val first = NodeSnapshot(
            hierarchyPath = listOf(2, 0),
            className = "android.widget.ImageView",
            bounds = ScreenBounds(40, 900, 520, 1420),
            isClickable = true,
        )
        val second = first.copy(
            hierarchyPath = listOf(2, 1),
            bounds = ScreenBounds(540, 1000, 1000, 1300),
        )
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品 33"),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：demo"),
                second,
                first,
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertEquals(first.hierarchyPath, observation.firstVideoTarget?.hierarchyPath)
    }
}
