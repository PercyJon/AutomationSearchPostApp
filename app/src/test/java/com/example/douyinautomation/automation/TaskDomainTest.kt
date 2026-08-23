package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskDomainTest {
    @Test
    fun `checkpoint cursor accepts current profile comment tasks without search queries`() {
        val snapshot = TaskDraft(
            id = "current-profile-checkpoint",
            name = "",
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = CommentPrivateMessageConfig(
                entryMode = CommentPrivateMessageEntryMode.CURRENT_PROFILE,
            ),
        ).toSnapshot(
            presets = SearchPresetCatalog("test", emptyList(), 0L),
            nowMillis = 1L,
        )

        assertEquals(0, TaskCheckpointQueryIndexPolicy.normalize(snapshot, queryIndex = 0))
        assertEquals(0, TaskCheckpointQueryIndexPolicy.normalize(snapshot, queryIndex = 3))
    }

    @Test
    fun `region is prefixed once and duplicate queries are removed`() {
        val queries = QueryComposer.composeAll(
            region = "广东",
            baseKeywords = listOf("红木家具", "广东红木家具", "红 木 家具"),
        )

        assertEquals(listOf("广东红木家具"), queries.map { it.query })
    }

    @Test
    fun `blocked keyword matches name metadata and OCR`() {
        val result = BlockedKeywordEvaluator.evaluate(
            text = UserResultText(
                displayName = "北流市某某红木工厂",
                rowMetadata = listOf("店铺账号"),
                ocrText = listOf("源头工厂直供"),
            ),
            blockedKeywords = listOf(" 工厂 ", "批发"),
        )

        assertTrue(result.blocked)
        assertEquals(listOf("工厂"), result.matchedKeywords)
        assertTrue(UserTextField.DISPLAY_NAME in result.matches.single().fields)
        assertTrue(UserTextField.OCR_TEXT in result.matches.single().fields)
    }

    @Test
    fun `unrelated user is not blocked`() {
        val result = BlockedKeywordEvaluator.evaluate(
            text = UserResultText(
                displayName = "东阳红木叶间",
                rowMetadata = listOf("东阳市隆禧红木家具厂"),
            ),
            blockedKeywords = listOf("工厂"),
        )

        assertFalse(result.blocked)
    }

    @Test
    fun `task snapshot keeps final query and rule version`() {
        val draft = TaskDraft(
            id = "task-1",
            name = "广东红木客户",
            presetIds = listOf("redwood-furniture"),
            region = "广东",
            blockedKeywords = listOf("工厂"),
            maxUsers = 10,
        )
        val snapshot = draft.toSnapshot(
            presets = SearchPresetCatalog(
                version = "remote-7",
                items = listOf(SearchPreset("redwood-furniture", "红木家具", "红木家具")),
                updatedAtMillis = 1L,
            ),
            nowMillis = 2L,
        )

        assertEquals(listOf("广东红木家具"), snapshot.composedQueries)
        assertEquals(listOf("工厂"), snapshot.normalizedBlockedKeywords)
        assertEquals("remote-7", snapshot.presetVersion)
        assertEquals(2L, snapshot.createdAtMillis)
    }

    @Test
    fun `invalid real-send draft requires a message template`() {
        val errors = TaskDraft(
            id = "task-1",
            name = "test",
            customKeywords = listOf("红木沙发"),
            executionMode = TaskExecutionMode.REAL_SEND_REQUIRES_CONFIRMATION,
        ).validationErrors()

        assertTrue(errors.any { it.contains("消息模板") })
    }

    @Test
    fun `blank task name is generated from first query and timestamp`() {
        val snapshot = TaskDraft(
            id = "task-1",
            name = "",
            customKeywords = listOf("红木沙发"),
        ).toSnapshot(
            presets = SearchPresetCatalog(
                version = "test",
                items = emptyList(),
                updatedAtMillis = 1L,
            ),
            nowMillis = 0L,
        )

        assertTrue(snapshot.taskName.startsWith("红木沙发-"))
        assertTrue(snapshot.taskName.length > "红木沙发-".length)
    }

    @Test
    fun `task query cursor advances in frozen order and stops at the end`() {
        val cursor = TaskQueryCursor(listOf("广东红木家具", "广东实木餐桌"))

        assertEquals("广东红木家具", cursor.current)
        assertTrue(cursor.hasNext)
        val next = cursor.next()!!
        assertEquals("广东实木餐桌", next.current)
        assertFalse(next.hasNext)
        assertEquals(null, next.next())
    }

    @Test
    fun `retry snapshot gets a new task identity and always uses safe probe`() {
        val history = TaskHistoryEntry(
            taskId = "old-task",
            taskName = "佛山红木家具",
            queryCount = 1,
            maxUsers = 30,
            startedAtMillis = 1L,
            updatedAtMillis = 2L,
            status = TaskRunStatus.FAILED,
            handledCount = 2,
            skippedCount = 1,
            filteredCount = 1,
            duplicateCount = 0,
            searchQueries = listOf("佛山红木家具"),
            region = "佛山",
            blockedKeywords = listOf("厂", "厂"),
            messageTemplate = "真实内容不应被继承",
            executionMode = TaskExecutionMode.REAL_SEND_REQUIRES_CONFIRMATION,
        )

        val snapshot = history.toRetrySnapshot("new-task", nowMillis = 3L)!!

        assertEquals("new-task", snapshot.taskId)
        assertEquals("佛山红木家具（重试）", snapshot.taskName)
        assertEquals(listOf("佛山红木家具"), snapshot.composedQueries)
        assertEquals(listOf("厂"), snapshot.normalizedBlockedKeywords)
        assertEquals(TaskExecutionMode.SAFE_BLANK_PROBE, snapshot.executionMode)
        assertEquals(null, snapshot.messageTemplate)
    }

    @Test
    fun `retry snapshot rejects history with no usable queries`() {
        val history = TaskHistoryEntry(
            taskId = "old-task",
            taskName = "空任务",
            queryCount = 0,
            maxUsers = 20,
            startedAtMillis = 1L,
            updatedAtMillis = 2L,
            status = TaskRunStatus.FAILED,
            handledCount = 0,
            skippedCount = 0,
            filteredCount = 0,
            duplicateCount = 0,
        )

        assertEquals(null, history.toRetrySnapshot("new-task", nowMillis = 3L))
    }
}
