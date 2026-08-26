package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileTaskPublishMapperTest {
    @Test
    fun `stable local ids reject drafts previews and remote numeric ids`() {
        assertTrue(MobileTaskPublishMapper.isStableLocalTaskId("11111111-2222-3333-4444-555555555555"))
        assertFalse(MobileTaskPublishMapper.isStableLocalTaskId("draft"))
        assertFalse(MobileTaskPublishMapper.isStableLocalTaskId("preview"))
        assertFalse(MobileTaskPublishMapper.isStableLocalTaskId("12345"))
        assertFalse(MobileTaskPublishMapper.isStableLocalTaskId(""))
    }

    @Test
    fun `snapshot publish omits message text and debug comment flags`() {
        val snapshot = TaskSnapshot(
            taskId = "11111111-2222-3333-4444-555555555555",
            taskName = "评论任务",
            presetVersion = "3",
            baseKeywords = listOf("室内设计师"),
            region = "广东",
            composedQueries = listOf("广东室内设计师"),
            normalizedBlockedKeywords = listOf("批发"),
            maxUsers = 4,
            messageTemplate = "真实私信不要上传",
            executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
            createdAtMillis = 1L,
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = CommentPrivateMessageSnapshot(
                entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE,
                targetUser = "室内设计师",
                matchKeywords = listOf("装修"),
                maxVideos = 1,
                maxUsersPerVideo = 4,
                skipPinnedVideos = true,
                dryRun = true,
                skipBlankProbe = true,
            ),
        )

        val request = MobileTaskPublishMapper.fromSnapshot(snapshot, deviceIdHash = "d".repeat(16))

        assertEquals("广东室内设计师", request.keyword)
        assertEquals("simulate_empty", request.sendMode)
        assertEquals(3, request.catalogVersion)
        assertEquals("SEARCH_TARGET_PROFILE", request.commentConfig?.entryMode)
        assertEquals(listOf("装修"), request.commentConfig?.matchKeywords)
        assertEquals("室内设计师", request.commentConfig?.targetUser)
        assertTrue(request.commentConfig?.skipPinnedVideos == true)
    }

    @Test
    fun `history publish maps terminal status without message text`() {
        val entry = TaskHistoryEntry(
            taskId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            taskName = "室内设计师-20260826-133339",
            queryCount = 1,
            maxUsers = 4,
            startedAtMillis = 1L,
            updatedAtMillis = 2L,
            status = TaskRunStatus.COMPLETED,
            handledCount = 4,
            skippedCount = 0,
            filteredCount = 0,
            duplicateCount = 0,
            searchQueries = listOf("室内设计师"),
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = CommentPrivateMessageSnapshot(
                entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE,
                targetUser = "室内设计师",
                matchKeywords = emptyList(),
                maxVideos = 1,
                maxUsersPerVideo = 4,
            ),
        )
        val request = MobileTaskPublishMapper.fromHistory(entry, deviceIdHash = "d".repeat(16))
        assertEquals(RemoteTaskStatus.COMPLETED, MobileTaskPublishMapper.remoteStatus(entry.status))
        assertEquals("室内设计师", request.keyword)
        assertEquals("simulate_empty", request.sendMode)
    }
}
