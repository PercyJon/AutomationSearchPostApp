package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorEngineTest {
    private val engine = SelectorEngine()

    @Test
    fun `Chinese private-message label selects the clickable action in the preferred region`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2400),
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(0),
                    text = "发私信",
                    bounds = ScreenBounds(700, 420, 1000, 520),
                    isClickable = true,
                ),
                NodeSnapshot(
                    hierarchyPath = listOf(1),
                    text = "Message settings",
                    bounds = ScreenBounds(40, 2100, 400, 2220),
                    isClickable = true,
                ),
            ),
        )

        val result = engine.select(context, DouyinSelectors.privateMessageEntry)

        assertTrue(result.found)
        assertEquals(listOf(0), result.node?.hierarchyPath)
        assertTrue(result.reasons.any { it.contains("preferred normalised region") })
    }

    @Test
    fun `English user tab supports cross-language selector matching`() {
        val context = ScreenContext(
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(2),
                    text = "Users",
                    bounds = ScreenBounds(400, 190, 600, 275),
                    isClickable = true,
                ),
            ),
        )

        val result = engine.select(context, DouyinSelectors.userTab)

        assertTrue(result.found)
        assertEquals("Users", result.node?.text)
    }

    @Test
    fun `user selector rejects a clickable search field without a user label`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2412),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(0),
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/search",
                        className = "android.widget.EditText",
                        bounds = ScreenBounds(132, 108, 754, 228),
                        isClickable = true,
                        isEditable = true,
                    ),
                ),
            ),
            DouyinSelectors.userTab,
        )

        assertFalse(result.found)
    }

    @Test
    fun `search submit selector never chooses the editable search field`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2412),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(0),
                        text = "是小瑜瑜呀~",
                        hintText = "搜索",
                        className = "android.widget.EditText",
                        bounds = ScreenBounds(132, 108, 754, 228),
                        isClickable = true,
                        isEditable = true,
                    ),
                    NodeSnapshot(
                        hierarchyPath = listOf(1),
                        text = "搜索",
                        contentDescription = "搜索",
                        className = "android.widget.TextView",
                        bounds = ScreenBounds(864, 96, 1032, 240),
                    ),
                    NodeSnapshot(
                        hierarchyPath = listOf(2),
                        contentDescription = "搜索",
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(760, 96, 840, 240),
                    ),
                ),
            ),
            SelectorRequest(
                name = "search-submit-test",
                labels = DouyinLabels.search,
                contentDescriptionLabels = DouyinLabels.search,
                classNameTokens = listOf("Button", "TextView"),
                requireClassNameToken = true,
                requireEditable = false,
                preferredRegion = NormalizedRect(0.68f, 0f, 1f, 0.30f),
                minimumScore = 0.30f,
            ),
        )

        assertTrue(result.found)
        assertEquals(listOf(1), result.node?.hierarchyPath)
    }

    @Test
    fun `unlabeled home search icon is selected by clickable class and region`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2412),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(0),
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(944, 96, 1032, 210),
                        isClickable = true,
                    ),
                    NodeSnapshot(
                        hierarchyPath = listOf(1),
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(40, 2100, 160, 2220),
                        isClickable = true,
                    ),
                ),
            ),
            DouyinSelectors.searchEntryStructural,
        )

        assertTrue(result.found)
        assertEquals(listOf(0), result.node?.hierarchyPath)
        assertTrue(result.reasons.any { it.contains("preferred normalised region") })
    }

    @Test
    fun `visible Chinese user tab is selected after horizontal reveal`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2412),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(7, 0),
                        text = "用户",
                        className = "android.widget.Button",
                        bounds = ScreenBounds(430, 275, 520, 336),
                        isClickable = true,
                        isSelected = true,
                    ),
                ),
            ),
            DouyinSelectors.userTab,
        )

        assertTrue(result.found)
        assertEquals("用户", result.node?.text)
    }

    @Test
    fun `normalised geometry produces the same selector result across resolutions`() {
        val request = SelectorRequest(
            name = "top-search",
            labels = listOf("搜索", "search"),
            requireClickable = true,
            preferredRegion = NormalizedRect(0f, 0f, 1f, 0.25f),
        )
        val hd = ScreenContext(
            screenSize = ScreenSize(1080, 2400),
            nodes = listOf(
                NodeSnapshot(text = "搜索", bounds = ScreenBounds(800, 120, 1020, 220), isClickable = true),
            ),
        )
        val qhd = ScreenContext(
            screenSize = ScreenSize(1440, 3200),
            nodes = listOf(
                NodeSnapshot(text = "Search", bounds = ScreenBounds(1066, 160, 1360, 293), isClickable = true),
            ),
        )

        val hdResult = engine.select(hd, request)
        val qhdResult = engine.select(qhd, request)

        assertTrue(hdResult.found)
        assertTrue(qhdResult.found)
        assertTrue(hdResult.score >= 0.8f)
        assertTrue(qhdResult.score >= 0.8f)
    }

    @Test
    fun `invisible candidate is rejected by default`() {
        val result = engine.select(
            ScreenContext(
                nodes = listOf(NodeSnapshot(text = "搜索", isClickable = true, isVisibleToUser = false)),
            ),
            DouyinSelectors.searchEntry,
        )

        assertFalse(result.found)
    }

    @Test
    fun `semantic label can resolve to its nearest clickable ancestor`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2400),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(3),
                        bounds = ScreenBounds(660, 400, 1020, 560),
                        isClickable = true,
                    ),
                    NodeSnapshot(
                        hierarchyPath = listOf(3, 0),
                        text = "发私信",
                        bounds = ScreenBounds(700, 425, 980, 520),
                    ),
                ),
            ),
            DouyinSelectors.privateMessageEntry,
        )

        assertTrue(result.found)
        assertEquals(listOf(3), result.node?.hierarchyPath)
        assertTrue(result.reasons.any { it.contains("clickable ancestor") })
    }

    @Test
    fun `stale hierarchy can relocate the same Douyin video tile by stable view id`() {
        val target = NodeSnapshot(
            hierarchyPath = listOf(4, 2, 0),
            viewIdResourceName = "com.ss.android.ugc.aweme:id/qb-",
            className = "android.view.View",
            bounds = ScreenBounds(0, 1253, 358, 1730),
            isClickable = true,
        )
        val fresh = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(2, 7, 1),
                    viewIdResourceName = "com.ss.android.ugc.aweme:id/qb-",
                    className = "android.view.View",
                    bounds = ScreenBounds(0, 1258, 358, 1735),
                    isClickable = true,
                ),
                NodeSnapshot(
                    hierarchyPath = listOf(2, 7, 2),
                    viewIdResourceName = "com.ss.android.ugc.aweme:id/qb-",
                    className = "android.view.View",
                    bounds = ScreenBounds(361, 1258, 719, 1735),
                    isClickable = true,
                ),
            ),
        )

        val relocated = engine.relocateSnapshot(fresh, target)

        assertEquals(listOf(2, 7, 1), relocated?.hierarchyPath)
    }

    @Test
    fun `scrollable result tab strip is selected only in the top region`() {
        val result = engine.select(
            ScreenContext(
                screenSize = ScreenSize(1080, 2400),
                nodes = listOf(
                    NodeSnapshot(
                        hierarchyPath = listOf(0),
                        className = "android.widget.HorizontalScrollView",
                        bounds = ScreenBounds(0, 120, 1080, 760),
                        isScrollable = true,
                    ),
                    NodeSnapshot(
                        hierarchyPath = listOf(1),
                        className = "android.widget.HorizontalScrollView",
                        bounds = ScreenBounds(0, 1400, 1080, 1800),
                        isScrollable = true,
                    ),
                ),
            ),
            DouyinSelectors.searchResultTabStrip,
        )

        assertTrue(result.found)
        assertEquals(listOf(0), result.node?.hierarchyPath)
    }
}
