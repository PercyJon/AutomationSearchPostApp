package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskQueueTest {
    @Test
    fun pollsItemsInInsertionOrder() {
        val queue = SequentialTaskQueue<Int>()
        queue.replace(listOf(1, 2, 3))

        assertEquals(1, queue.poll())
        assertEquals(2, queue.poll())
        assertEquals(3, queue.poll())
        assertTrue(queue.isEmpty)
    }

    @Test
    fun replaceDropsPreviousPendingItems() {
        val queue = SequentialTaskQueue<String>()
        queue.addAll(listOf("old"))
        queue.replace(listOf("new-1", "new-2"))

        assertEquals(listOf("new-1", "new-2"), queue.asList())
    }

    @Test
    fun `queue policy keeps B end tasks separate from search profile comment tasks`() {
        val bEnd = snapshot(id = "b-end")
        val comment = snapshot(
            id = "comment",
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = commentConfig(CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE),
        )

        assertEquals(
            LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
            LocalTaskQueuePolicy.validate(listOf(bEnd)),
        )
        assertEquals(
            LocalTaskQueueType.COMMENT_SEARCH_PROFILE,
            LocalTaskQueuePolicy.validate(listOf(comment)),
        )
        assertNull(LocalTaskQueuePolicy.validate(listOf(bEnd, comment)))
    }

    @Test
    fun `queue policy rejects current profile comment tasks`() {
        val currentProfile = snapshot(
            id = "current-profile",
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = commentConfig(CommentPrivateMessageEntryMode.CURRENT_PROFILE),
        )

        assertNull(LocalTaskQueuePolicy.validate(listOf(currentProfile)))
    }

    @Test
    fun `queue session preserves order while paused then completes at the last task`() {
        val session = LocalTaskQueueSession(
            queueId = "queue",
            queueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
            tasks = listOf(snapshot("first"), snapshot("second")),
            updatedAtMillis = 1L,
        )

        val paused = session.pause(nowMillis = 2L, phase = AutomationPhase.WAITING_FOR_USER_RESULTS)
        val resumed = paused.resume(nowMillis = 3L)
        val second = resumed.advance(nowMillis = 4L)
        val completed = second.advance(nowMillis = 5L)

        assertEquals(LocalTaskQueueStatus.PAUSED, paused.status)
        assertEquals("first", resumed.activeTask.taskId)
        assertEquals("second", second.activeTask.taskId)
        assertEquals(LocalTaskQueueStatus.COMPLETED, completed.status)
        assertEquals(1, completed.activeTaskIndex)
    }

    @Test
    fun `B end queue restarts only when the paused page no longer matches its phase`() {
        assertTrue(
            !LocalTaskQueueResumePolicy.requiresInitialRestart(
                queueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
                pausedPhase = AutomationPhase.WAITING_FOR_USER_RESULTS,
                visiblePage = PageKind.USER_RESULTS,
            ),
        )
        assertTrue(
            LocalTaskQueueResumePolicy.requiresInitialRestart(
                queueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
                pausedPhase = AutomationPhase.WAITING_FOR_USER_RESULTS,
                visiblePage = PageKind.HOME,
            ),
        )
        assertTrue(
            LocalTaskQueueResumePolicy.requiresInitialRestart(
                queueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
                pausedPhase = AutomationPhase.WAITING_FOR_USER_RESULTS,
                visiblePage = PageKind.OUTSIDE_TARGET,
            ),
        )
    }

    @Test
    fun `comment search queue always restarts from the frozen initial flow`() {
        assertTrue(
            LocalTaskQueueResumePolicy.requiresInitialRestart(
                queueType = LocalTaskQueueType.COMMENT_SEARCH_PROFILE,
                pausedPhase = AutomationPhase.WAITING_FOR_PROFILE,
                visiblePage = PageKind.USER_PROFILE,
            ),
        )
    }

    private fun snapshot(
        id: String,
        taskType: AutomationTaskType = AutomationTaskType.PROFILE_PRIVATE_MESSAGE,
        commentConfig: CommentPrivateMessageSnapshot? = null,
    ) = TaskSnapshot(
        taskId = id,
        taskName = id,
        presetVersion = "test",
        baseKeywords = listOf("keyword"),
        region = "",
        composedQueries = listOf("keyword"),
        normalizedBlockedKeywords = emptyList(),
        maxUsers = 1,
        messageTemplate = null,
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
        createdAtMillis = 0L,
        taskType = taskType,
        commentConfig = commentConfig,
    )

    private fun commentConfig(entryMode: CommentPrivateMessageEntryMode) =
        CommentPrivateMessageSnapshot(
            entryMode = entryMode,
            targetUser = "designer",
            matchKeywords = emptyList(),
            matchMode = CommentKeywordMatchMode.ANY,
            maxVideos = 1,
            maxUsersPerVideo = 1,
            skipPinnedVideos = false,
            dryRun = false,
        )
}
