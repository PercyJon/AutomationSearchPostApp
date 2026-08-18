package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserResultIdentityTest {
    @Test
    fun `uses row accessibility text before action labels`() {
        val context = context(
            NodeSnapshot(
                hierarchyPath = listOf(0, 1),
                text = "红木沙发工厂",
                bounds = ScreenBounds(220, 420, 700, 500),
            ),
            NodeSnapshot(
                hierarchyPath = listOf(0, 2),
                text = "关注",
                contentDescription = "关注按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
        )
        val match = StructuralUserRowDetector.find(context)

        val identity = UserResultIdentityExtractor.extract(context, requireNotNull(match))

        assertEquals(UserResultIdentity.Source.ACCESSIBILITY, identity?.source)
        assertEquals("红木沙发工厂", identity?.key)
    }

    @Test
    fun `falls back to OCR blocks inside the selected row`() {
        val context = context(
            NodeSnapshot(
                hierarchyPath = listOf(0, 2),
                text = "关注",
                contentDescription = "关注按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("红木沙发工厂", ScreenBounds(220, 420, 700, 500)),
                OcrTextBlock("抖音号:abc123", ScreenBounds(220, 510, 700, 560)),
            ),
        )
        val match = StructuralUserRowDetector.find(context)

        val identity = UserResultIdentityExtractor.extract(context, requireNotNull(match))

        assertEquals(UserResultIdentity.Source.OCR, identity?.source)
        assertEquals("handle:abc123", identity?.key)
        assertEquals("abc123", identity?.accountHandle)
        assertEquals("红木沙发工厂", identity?.displayName)
    }

    @Test
    fun `merges row OCR metadata with semantic display name for filters`() {
        val context = context(
            NodeSnapshot(
                hierarchyPath = listOf(0, 2, 1),
                text = "红木家具旗舰店",
                bounds = ScreenBounds(220, 420, 700, 500),
            ),
            NodeSnapshot(
                hierarchyPath = listOf(0, 2, 2),
                text = "关注",
                contentDescription = "关注按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("佛山市某某工厂", ScreenBounds(220, 510, 700, 560)),
            ),
        )
        val match = StructuralUserRowDetector.find(context)

        val identity = UserResultIdentityExtractor.extract(context, requireNotNull(match))
        val evaluation = BlockedKeywordEvaluator.evaluate(
            UserResultText(
                displayName = identity?.displayName,
                accountHandle = identity?.accountHandle,
                rowMetadata = identity?.stableMetadata?.toList().orEmpty(),
                ocrText = identity?.visibleTokens?.toList().orEmpty(),
            ),
            listOf("厂"),
        )

        assertEquals(UserResultIdentity.Source.ACCESSIBILITY, identity?.source)
        assertTrue(evaluation.blocked)
    }

    @Test
    fun `does not create identity from a row action alone`() {
        val context = context(
            NodeSnapshot(
                hierarchyPath = listOf(0, 2),
                text = "关注",
                contentDescription = "关注按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
        )
        val match = StructuralUserRowDetector.find(context)

        assertNull(UserResultIdentityExtractor.extract(context, requireNotNull(match)))
    }

    private fun context(
        vararg nodes: NodeSnapshot,
        ocrBlocks: List<OcrTextBlock> = emptyList(),
    ): ScreenContext {
        val row = NodeSnapshot(
            hierarchyPath = listOf(0),
            bounds = ScreenBounds(0, 375, 1056, 627),
        )
        return ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(row) + nodes,
            ocrBlocks = ocrBlocks,
        )
    }
}
