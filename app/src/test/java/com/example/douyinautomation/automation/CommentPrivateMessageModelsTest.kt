package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentPrivateMessageModelsTest {
    @Test
    fun `pipe separated keywords are normalized and deduplicated`() {
        assertEquals(
            listOf("价格", "优惠", "定制"),
            CommentKeywordMatcher.parsePipeSeparated("价格 | 优惠|价格| 定制 "),
        )
    }

    @Test
    fun `empty match list means every comment`() {
        assertTrue(CommentKeywordMatcher.matches("任意评论", emptyList()))
        assertTrue(CommentKeywordMatcher.matches("想了解价格", listOf("价格")))
        assertFalse(CommentKeywordMatcher.matches("很好看", listOf("价格", "优惠")))
    }

    @Test
    fun `all keyword mode requires every normalized term in the comment body`() {
        assertTrue(
            CommentKeywordMatcher.matches(
                comment = "想了解价格和优惠活动",
                keywords = listOf("价格", "优惠"),
                matchMode = CommentKeywordMatchMode.ALL,
            ),
        )
        assertFalse(
            CommentKeywordMatcher.matches(
                comment = "想了解价格",
                keywords = listOf("价格", "优惠"),
                matchMode = CommentKeywordMatchMode.ALL,
            ),
        )
        assertTrue(
            CommentKeywordMatcher.matches(
                comment = "任意评论",
                keywords = emptyList(),
                matchMode = CommentKeywordMatchMode.ALL,
            ),
        )
    }

    @Test
    fun `extraction statistics retain counts without retaining comment text`() {
        val extraction = CommentCandidateExtraction(
            candidates = emptyList(),
            fragments = emptyList(),
            commentBodiesRead = 4,
            matchedCommentBodies = 2,
        )

        assertEquals(4, extraction.commentBodiesRead)
        assertEquals(2, extraction.matchedCommentBodies)
        assertEquals(0, extraction.actionableCandidateCount)
    }

    @Test
    fun `comment config requires target only for search entry`() {
        val searchErrors = CommentPrivateMessageConfig().validationErrors()
        assertTrue(searchErrors.any { it.contains("目标用户") })

        val currentProfileErrors = CommentPrivateMessageConfig(
            entryMode = CommentPrivateMessageEntryMode.CURRENT_PROFILE,
        ).validationErrors()
        assertTrue(currentProfileErrors.isEmpty())
    }

    @Test
    fun `comment task snapshot freezes task type and matching config`() {
        val snapshot = TaskDraft(
            id = "comment-task",
            name = "评论线索",
            customKeywords = listOf("红木家具"),
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = CommentPrivateMessageConfig(
                entryMode = CommentPrivateMessageEntryMode.CURRENT_PROFILE,
                matchKeywords = listOf("价格|优惠"),
                matchMode = CommentKeywordMatchMode.ALL,
                maxVideos = 3,
                maxUsersPerVideo = 8,
                skipPinnedVideos = true,
            ),
        ).toSnapshot(
            presets = SearchPresetCatalog("test", emptyList(), 0L),
            nowMillis = 1L,
        )

        assertEquals(AutomationTaskType.COMMENT_PRIVATE_MESSAGE, snapshot.taskType)
        assertEquals(CommentPrivateMessageEntryMode.CURRENT_PROFILE, snapshot.commentConfig?.entryMode)
        assertEquals(listOf("价格", "优惠"), snapshot.commentConfig?.matchKeywords)
        assertEquals(CommentKeywordMatchMode.ALL, snapshot.commentConfig?.matchMode)
        assertEquals(3, snapshot.commentConfig?.maxVideos)
        assertTrue(snapshot.commentConfig?.skipPinnedVideos == true)
        assertFalse(snapshot.commentConfig?.matchesComment("请问有优惠吗") ?: true)
    }

    @Test
    fun `profile task defaults remain unchanged`() {
        val snapshot = TaskDraft(
            id = "profile-task",
            name = "普通私信",
            customKeywords = listOf("红木家具"),
        ).toSnapshot(
            presets = SearchPresetCatalog("test", emptyList(), 0L),
            nowMillis = 1L,
        )

        assertEquals(AutomationTaskType.PROFILE_PRIVATE_MESSAGE, snapshot.taskType)
        assertEquals(null, snapshot.commentConfig)
    }

    @Test
    fun `current profile comment task does not require a search query`() {
        val snapshot = TaskDraft(
            id = "current-profile-comment-task",
            name = "",
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = CommentPrivateMessageConfig(
                entryMode = CommentPrivateMessageEntryMode.CURRENT_PROFILE,
            ),
        ).toSnapshot(
            presets = SearchPresetCatalog("test", emptyList(), 0L),
            nowMillis = 1L,
        )

        assertTrue(snapshot.taskName.startsWith("评论私信-"))
        assertTrue(snapshot.composedQueries.isEmpty())
    }
}
