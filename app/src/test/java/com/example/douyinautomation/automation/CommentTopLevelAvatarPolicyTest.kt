package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentTopLevelAvatarPolicyTest {

    @Test
    fun columnLeftIsTheLeftmostAvatar() {
        assertEquals(48, CommentTopLevelAvatarPolicy.columnLeftPx(listOf(180, 48, 50)))
        assertNull(CommentTopLevelAvatarPolicy.columnLeftPx(emptyList()))
    }

    @Test
    fun sixteenDpToleranceUsesDensityNotRawPixels() {
        val density = 3f
        val column = 48
        val tolerance = CommentTopLevelAvatarPolicy.tolerancePx(density)
        assertEquals(48, tolerance)
        assertTrue(CommentTopLevelAvatarPolicy.isTopLevelAvatar(column, column, density))
        assertTrue(CommentTopLevelAvatarPolicy.isTopLevelAvatar(column + tolerance, column, density))
        assertFalse(CommentTopLevelAvatarPolicy.isTopLevelAvatar(column + tolerance + 1, column, density))
        // Nested-reply indent is ~40dp = 120px at density 3, well outside 16dp.
        assertFalse(CommentTopLevelAvatarPolicy.isTopLevelAvatar(180, column, density))
    }

    @Test
    fun nestedReplyIsDroppedWhileSiblingTopLevelRowsStay() {
        val yang = candidate("阳", avatarLeft = 48, avatarTop = 970)
        val reply = candidate("自己的小迷弟", avatarLeft = 180, avatarTop = 1280)
        val zero = candidate("Zero", avatarLeft = 48, avatarTop = 1620)
        val partition = CommentTopLevelAvatarPolicy.partition(
            candidates = listOf(yang, reply, zero),
            columnLeft = 48,
            density = 3f,
        )

        assertEquals(listOf("阳", "Zero"), partition.topLevel.map { it.authorText })
        assertEquals(listOf("自己的小迷弟"), partition.replies.map { it.authorText })
    }

    @Test
    fun frozenColumnIgnoresALaterReplyOnlyViewport() {
        val reply = candidate("自己的小迷弟", avatarLeft = 180, avatarTop = 1100)
        val partition = CommentTopLevelAvatarPolicy.partition(
            candidates = listOf(reply),
            columnLeft = 48,
            density = 3f,
        )

        assertTrue(partition.topLevel.isEmpty())
        assertEquals(1, partition.replies.size)
    }

    @Test
    fun leadingAvatarKeepsANonMatchingTopLevelFirstVisible() {
        val firstVisible = ScreenBounds(48, 970, 156, 1078)
        val later = candidate("Zero", avatarLeft = 48, avatarTop = 1620)
        val leading = CommentTopLevelAvatarPolicy.leadingTopLevelAvatar(
            firstVisible = firstVisible,
            topLevelCandidates = listOf(later),
            columnLeft = 48,
            density = 3f,
        )

        assertEquals(firstVisible, leading)
    }

    @Test
    fun leadingAvatarSkipsAnIndentedFirstVisibleReply() {
        val replyFirst = ScreenBounds(180, 1100, 288, 1208)
        val topLevel = candidate("Zero", avatarLeft = 48, avatarTop = 1400)
        val leading = CommentTopLevelAvatarPolicy.leadingTopLevelAvatar(
            firstVisible = replyFirst,
            topLevelCandidates = listOf(topLevel),
            columnLeft = 48,
            density = 3f,
        )

        assertEquals(topLevel.avatarBounds, leading)
    }

    private fun candidate(
        author: String,
        avatarLeft: Int,
        avatarTop: Int,
        size: Int = 108,
    ): CommentUserCandidate {
        val avatar = ScreenBounds(avatarLeft, avatarTop, avatarLeft + size, avatarTop + size)
        return CommentUserCandidate(
            authorText = author,
            commentText = "评论",
            authorBounds = ScreenBounds(avatar.right + 12, avatarTop, avatar.right + 200, avatarTop + 40),
            commentBounds = ScreenBounds(avatar.right + 12, avatarTop + 48, avatar.right + 400, avatarTop + 96),
            interactionBounds = avatar,
            matchedKeywords = emptyList(),
            identityKey = "comment-user:$author",
            source = CommentTextSource.ACCESSIBILITY,
            avatarBounds = avatar,
        )
    }
}
