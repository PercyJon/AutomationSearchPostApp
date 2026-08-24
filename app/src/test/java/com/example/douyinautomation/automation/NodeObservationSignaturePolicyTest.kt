package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NodeObservationSignaturePolicyTest {

    @Test
    fun identicalSnapshotsHaveTheSameIncrementalSignature() {
        val snapshot = listOf(
            NodeSnapshot(
                text = "用户",
                contentDescription = "用户标签",
                bounds = ScreenBounds(320, 160, 460, 240),
                isSelected = true,
            ),
            NodeSnapshot(
                text = "关注",
                bounds = ScreenBounds(760, 860, 960, 940),
                isVisibleToUser = true,
            ),
        )

        assertEquals(
            NodeObservationSignaturePolicy.hash(snapshot),
            NodeObservationSignaturePolicy.hash(snapshot.map { it.copy() }),
        )
    }

    @Test
    fun changesToTheExistingSemanticOrGeometryFieldsChangeTheSignature() {
        val node = NodeSnapshot(
            text = "用户",
            contentDescription = "用户标签",
            bounds = ScreenBounds(320, 160, 460, 240),
            isSelected = false,
            isVisibleToUser = true,
        )
        val baseline = NodeObservationSignaturePolicy.hash(listOf(node))

        listOf(
            node.copy(text = "综合"),
            node.copy(contentDescription = "综合标签"),
            node.copy(bounds = ScreenBounds(320, 160, 461, 240)),
            node.copy(isSelected = true),
            node.copy(isVisibleToUser = false),
        ).forEach { changed ->
            assertNotEquals(baseline, NodeObservationSignaturePolicy.hash(listOf(changed)))
        }
    }

    @Test
    fun nodeOrderRemainsPartOfTheSignature() {
        val first = NodeSnapshot(text = "搜索", bounds = ScreenBounds(0, 0, 100, 80))
        val second = NodeSnapshot(text = "用户", bounds = ScreenBounds(100, 0, 200, 80))

        assertNotEquals(
            NodeObservationSignaturePolicy.hash(listOf(first, second)),
            NodeObservationSignaturePolicy.hash(listOf(second, first)),
        )
    }
}
