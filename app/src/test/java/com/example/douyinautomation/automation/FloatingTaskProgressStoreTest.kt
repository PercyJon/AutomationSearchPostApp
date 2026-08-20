package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingTaskProgressStoreTest {
    @Test
    fun `start resets counters and exposes a running comment task`() {
        val store = FloatingTaskProgressStore()

        store.start(
            taskId = "comment-1",
            taskName = "红木评论",
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            total = 12,
            stageLabel = "读取评论",
        )

        val state = store.progress.value
        assertEquals("comment-1", state.taskId)
        assertEquals(AutomationTaskType.COMMENT_PRIVATE_MESSAGE, state.taskType)
        assertTrue(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(12, state.total)
        assertEquals("读取评论", state.stageLabel)
    }

    @Test
    fun `counter updates clamp negative values and preserve task metadata`() {
        val store = FloatingTaskProgressStore()
        store.start("id", "name", AutomationTaskType.COMMENT_PRIVATE_MESSAGE, total = 10)

        store.update(processed = -1, success = 2, failed = 1, skipped = 1, stageLabel = "验证")

        assertEquals(0, store.progress.value.processed)
        assertEquals(2, store.progress.value.success)
        assertEquals(1, store.progress.value.failed)
        assertEquals(1, store.progress.value.skipped)
        assertEquals("id", store.progress.value.taskId)
        assertEquals("验证", store.progress.value.stageLabel)
    }

    @Test
    fun `pause and resume are no-ops after stop`() {
        val store = FloatingTaskProgressStore()
        store.start("id", "name", AutomationTaskType.COMMENT_PRIVATE_MESSAGE, total = 3)

        store.pause()
        assertTrue(store.progress.value.isPaused)
        store.resume("继续")
        assertFalse(store.progress.value.isPaused)
        assertEquals("继续", store.progress.value.stageLabel)
        store.stop()
        store.resume("不应恢复")

        assertFalse(store.progress.value.isRunning)
        assertFalse(store.progress.value.isPaused)
        assertEquals("已停止", store.progress.value.stageLabel)
    }

    @Test
    fun `complete marks all known work processed and keeps counters`() {
        val store = FloatingTaskProgressStore()
        store.start("id", "name", AutomationTaskType.COMMENT_PRIVATE_MESSAGE, total = 4)
        store.update(processed = 2, success = 1, failed = 1)

        store.complete()

        val state = store.progress.value
        assertFalse(state.isRunning)
        assertEquals(4, state.processed)
        assertEquals(1, state.success)
        assertEquals(1, state.failed)
        assertEquals(100, state.completionPercent)
    }

    @Test
    fun `clear returns a hidden neutral state`() {
        val store = FloatingTaskProgressStore()
        store.start("id", "name", AutomationTaskType.COMMENT_PRIVATE_MESSAGE, total = 1)

        store.clear()

        assertEquals(FloatingTaskProgress(), store.progress.value)
    }
}
