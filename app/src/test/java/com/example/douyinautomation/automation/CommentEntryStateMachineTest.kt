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
    fun `profile switches to works tab once when another tab is selected`() {
        val machine = CommentEntryStateMachine()
        val works = NodeSnapshot(
            text = "作品 41",
            bounds = ScreenBounds(0, 1400, 540, 1520),
            isClickable = true,
            isSelected = false,
        )

        val selectWorks = machine.observe(
            CommentEntryObservation(
                page = PageKind.USER_PROFILE,
                hasFirstVideoTarget = true,
                worksTabTarget = works,
                worksTabSelected = false,
            ),
        )
        assertEquals(CommentEntryAction.OPEN_WORKS_TAB, selectWorks.action)

        val openVideo = machine.observe(
            CommentEntryObservation(
                page = PageKind.USER_PROFILE,
                hasFirstVideoTarget = true,
                worksTabTarget = works,
                worksTabSelected = true,
            ),
        )
        assertEquals(CommentEntryAction.OPEN_FIRST_VIDEO, openVideo.action)
    }

    @Test
    fun `profile with multiple tabs and works already selected opens video directly`() {
        val machine = CommentEntryStateMachine()
        val works = NodeSnapshot(
            text = "作品 52 ▼",
            bounds = ScreenBounds(0, 1400, 540, 1520),
            isClickable = true,
            isSelected = true,
        )

        val decision = machine.observe(
            CommentEntryObservation(
                page = PageKind.USER_PROFILE,
                hasFirstVideoTarget = true,
                worksTabTarget = works,
                worksTabSelected = true,
            ),
        )

        assertEquals(CommentEntryAction.OPEN_FIRST_VIDEO, decision.action)
    }

    @Test
    fun `works sort popup selects latest before looking for a video`() {
        val machine = CommentEntryStateMachine()
        val works = NodeSnapshot(
            text = "作品 41",
            bounds = ScreenBounds(0, 1400, 540, 1520),
            isClickable = true,
            isSelected = true,
        )
        machine.observe(
            CommentEntryObservation(
                page = PageKind.USER_PROFILE,
                hasFirstVideoTarget = true,
                worksTabTarget = works,
                worksTabSelected = true,
            ),
        )

        val latest = NodeSnapshot(
            text = "最新",
            bounds = ScreenBounds(360, 1350, 720, 1450),
            isClickable = true,
        )
        val decision = machine.observe(
            CommentEntryObservation(
                page = PageKind.UNKNOWN,
                worksSortLatestTarget = latest,
            ),
        )

        assertEquals(CommentEntryAction.DISMISS_WORKS_SORT, decision.action)
        assertEquals(CommentEntryStage.WAITING_FOR_VIDEO, decision.stage)
    }

    @Test
    fun `private or empty profile is skipped instead of waiting for a video`() {
        val machine = CommentEntryStateMachine()
        val decision = machine.observe(
            CommentEntryObservation(
                page = PageKind.USER_PROFILE,
                profileHasNoWorks = true,
            ),
        )
        assertEquals(CommentEntryAction.SKIP_PROFILE, decision.action)
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
    fun `verified comment action rail is enough to confirm a custom rendered video surface`() {
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(0),
                    className = "android.widget.ImageButton",
                    bounds = ScreenBounds(870, 760, 980, 870),
                    isClickable = true,
                ),
                NodeSnapshot(
                    hierarchyPath = listOf(1),
                    className = "android.widget.ImageButton",
                    bounds = ScreenBounds(870, 980, 980, 1090),
                    isClickable = true,
                ),
                NodeSnapshot(
                    hierarchyPath = listOf(2),
                    className = "android.widget.ImageButton",
                    bounds = ScreenBounds(870, 1200, 980, 1310),
                    isClickable = true,
                ),
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)
        val machine = CommentEntryStateMachine()
        machine.observe(CommentEntryObservation(PageKind.USER_PROFILE, hasFirstVideoTarget = true))
        val decision = machine.observe(observation)

        assertEquals(PageKind.UNKNOWN, observation.page)
        assertTrue(observation.hasCommentEntry)
        assertTrue(observation.hasVideoSurface)
        assertEquals(listOf(1), observation.commentButton?.hierarchyPath)
        assertEquals(CommentEntryAction.OPEN_COMMENTS, decision.action)
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

    @Test
    fun `signal detector accepts a non clickable works thumbnail for bounds fallback`() {
        val thumbnail = NodeSnapshot(
            hierarchyPath = listOf(4, 0, 0),
            className = "android.widget.ImageView",
            bounds = ScreenBounds(46, 1724, 507, 2185),
            isClickable = false,
        )
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品 701"),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：designer"),
                thumbnail,
            ),
        )

        val target = CommentEntrySignalDetector.firstVideoTarget(context)

        assertEquals(thumbnail.hierarchyPath, target?.hierarchyPath)
        assertEquals(thumbnail.bounds, target?.bounds)
    }

    @Test
    fun `pinned works tile is skipped when the task switch is enabled`() {
        val pinned = NodeSnapshot(
            hierarchyPath = listOf(4, 0, 0),
            className = "android.widget.ImageView",
            bounds = ScreenBounds(46, 1724, 507, 2185),
        )
        val latest = pinned.copy(
            hierarchyPath = listOf(4, 0, 1),
            bounds = ScreenBounds(530, 1724, 991, 2185),
        )
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品 41"),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：designer"),
                pinned,
                latest,
            ),
            ocrBlocks = listOf(OcrTextBlock("置顶", ScreenBounds(52, 1730, 130, 1790))),
        )

        val observation = CommentEntrySignalDetector.observe(context, skipPinnedVideos = true)

        assertEquals(latest.hierarchyPath, observation.firstVideoTarget?.hierarchyPath)
    }

    @Test
    fun `single works tab opens video directly without opening sort popup`() {
        val thumbnail = NodeSnapshot(
            hierarchyPath = listOf(4, 0, 0),
            className = "android.widget.ImageView",
            bounds = ScreenBounds(46, 1724, 507, 2185),
        )
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品", bounds = ScreenBounds(40, 1500, 500, 1620)),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：designer"),
                thumbnail,
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertEquals(null, observation.worksTabTarget)
        assertEquals(thumbnail.hierarchyPath, observation.firstVideoTarget?.hierarchyPath)
    }

    @Test
    fun `action bar tab label child is not counted as a second profile tab`() {
        val thumbnail = NodeSnapshot(
            hierarchyPath = listOf(4, 0, 0),
            className = "android.widget.ImageView",
            bounds = ScreenBounds(46, 1724, 507, 2185),
        )
        val tabRoot = NodeSnapshot(
            hierarchyPath = listOf(0),
            contentDescription = "作品 138,按钮,当前作品按最新发布排序",
            className = "androidx.appcompat.app.ActionBar\$Tab",
            bounds = ScreenBounds(0, 1130, 292, 1250),
            isClickable = true,
            isSelected = true,
            childCount = 1,
        )
        val tabLabel = NodeSnapshot(
            hierarchyPath = listOf(0, 0),
            text = "作品 138",
            contentDescription = "作品 138",
            viewIdResourceName = "android:id/text1",
            className = "android.widget.TextView",
            bounds = ScreenBounds(48, 1160, 193, 1221),
            isSelected = true,
        )
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                tabRoot,
                tabLabel,
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：designer"),
                thumbnail,
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertEquals(null, observation.worksTabTarget)
        assertEquals(thumbnail.hierarchyPath, observation.firstVideoTarget?.hierarchyPath)
    }

    @Test
    fun `douyin cover child can be selected when visibility is transient and tile parent is clickable`() {
        val thumbnail = NodeSnapshot(
            hierarchyPath = listOf(7, 0, 0),
            className = "android.widget.ImageView",
            viewIdResourceName = "com.ss.android.ugc.aweme:id/cover",
            bounds = ScreenBounds(0, 1196, 358, 1673),
            isVisibleToUser = false,
        )
        val tile = NodeSnapshot(
            hierarchyPath = listOf(7, 0),
            className = "android.view.View",
            viewIdResourceName = "com.ss.android.ugc.aweme:id/qb-",
            bounds = thumbnail.bounds,
            isClickable = true,
        )
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "作品 4"),
                NodeSnapshot(text = "发私信", isClickable = true),
                NodeSnapshot(text = "抖音号：1178711787"),
                thumbnail,
                tile,
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertTrue(observation.hasFirstVideoTarget)
        assertEquals(tile.hierarchyPath, observation.firstVideoTarget?.hierarchyPath)
    }
}
