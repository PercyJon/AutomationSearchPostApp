package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitingForHomeUnknownDiagnosticsTest {
    private val selector = SelectorEngine()
    private val screen = ScreenSize(1080, 2412)

    @Test
    fun emptyTreeIsMissingNodes() {
        val report = WaitingForHomeUnknownDiagnostics.analyze(
            context = null,
            selector = selector,
            detectedPage = null,
            normalizedPage = null,
            stableObservations = 0,
            ocrSkippedReason = "none",
        )

        assertEquals(WaitingForHomeUnknownDiagnostics.Cause.MISSING_NODES, report.cause)
        assertEquals(0, report.nodeCount)
        assertFalse(report.toLogAttributes().values.any { it.toString().contains("首页视频") })
    }

    @Test
    fun wideLiveBannerIsOverlayRejected() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = unlabeledViews(count = 12) + NodeSnapshot(
                hierarchyPath = listOf(99),
                text = "正在直播",
                className = "android.widget.FrameLayout",
                bounds = ScreenBounds(20, 40, 1060, 220),
                isVisibleToUser = true,
            ),
        )

        val report = analyzeUnknown(context, ocrSkippedReason = "none")

        assertEquals(WaitingForHomeUnknownDiagnostics.Cause.OVERLAY_REJECTED, report.cause)
        assertEquals("live_banner", report.overlayKind)
        assertEquals("正在直播", report.overlayMarker)
        assertTrue(report.overlayRegion.startsWith("0.02,0.02"))
    }

    @Test
    fun commentTaskWithoutHomeLabelsIsOcrNotTriggered() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = unlabeledViews(count = 71),
        )

        val report = analyzeUnknown(context, ocrSkippedReason = "comment_task")

        assertEquals(WaitingForHomeUnknownDiagnostics.Cause.OCR_NOT_TRIGGERED, report.cause)
        assertEquals(71, report.nodeCount)
        assertEquals(0, report.homeHitCount)
        assertEquals(0, report.ocrBlockCount)
        assertEquals("comment_task", report.ocrSkippedReason)
        assertFalse(report.searchEntryFound)
        assertFalse(report.searchEntryStructuralFound)
        assertTrue(report.searchEntryReasons.contains("minimum score"))
        assertTrue(report.classCounts.contains("View:"))
    }

    @Test
    fun unlabeledTreeAfterOcrRanIsMissingSemantics() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = unlabeledViews(count = 40),
            ocrBlocks = listOf(OcrTextBlock(text = "广告合作", bounds = ScreenBounds(40, 800, 400, 860))),
        )

        val report = analyzeUnknown(context, ocrSkippedReason = "none")

        assertEquals(WaitingForHomeUnknownDiagnostics.Cause.MISSING_SEMANTICS, report.cause)
        assertEquals("none", report.homeTerms)
        assertEquals(1, report.ocrBlockCount)
    }

    @Test
    fun classifiedHomeWaitingForSecondSnapshotIsStableGate() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(text = "首页", bounds = ScreenBounds(40, 2280, 200, 2390)),
                NodeSnapshot(text = "推荐", bounds = ScreenBounds(240, 80, 400, 160)),
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "搜索",
                    bounds = ScreenBounds(920, 60, 1040, 180),
                    isClickable = true,
                ),
            ),
        )

        val report = WaitingForHomeUnknownDiagnostics.analyze(
            context = context,
            selector = selector,
            detectedPage = PageKind.HOME,
            normalizedPage = PageKind.HOME,
            stableObservations = 1,
            ocrSkippedReason = "not_needed",
        )

        assertEquals(WaitingForHomeUnknownDiagnostics.Cause.STABLE_GATE_NOT_PASSED, report.cause)
        assertTrue(report.homeHitCount >= 2)
        assertTrue(report.searchEntryFound)
        assertEquals("not_needed", report.ocrSkippedReason)
    }

    private fun analyzeUnknown(
        context: ScreenContext,
        ocrSkippedReason: String,
    ) = WaitingForHomeUnknownDiagnostics.analyze(
        context = context,
        selector = selector,
        detectedPage = PageKind.UNKNOWN,
        normalizedPage = PageKind.UNKNOWN,
        stableObservations = 0,
        ocrSkippedReason = ocrSkippedReason,
    )

    private fun unlabeledViews(count: Int): List<NodeSnapshot> =
        List(count) { index ->
            NodeSnapshot(
                hierarchyPath = listOf(index),
                className = "android.view.View",
                bounds = ScreenBounds(0, index * 8, 1080, index * 8 + 24),
                isVisibleToUser = true,
            )
        }
}
