package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoTaskRetentionPolicyTest {

    @Test
    fun immediateStartDoesNotKeepAPreviewId() {
        assertEquals(
            setOf("11111111-2222-3333-4444-555555555555"),
            TodoTaskRetentionPolicy.idsToDiscardOnStart(
                originalTaskId = "preview",
                boundTaskId = "11111111-2222-3333-4444-555555555555",
            ),
        )
    }

    @Test
    fun savedTodoLeavesTheListWhenItsRunStarts() {
        val savedId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        assertEquals(
            setOf(savedId),
            TodoTaskRetentionPolicy.idsToDiscardOnStart(
                originalTaskId = savedId,
                boundTaskId = savedId,
            ),
        )
    }

    @Test
    fun alreadyStartedDraftsAreHiddenFromTodo() {
        val started = TaskDraft(id = "task-started-1", name = "红木沙发")
        val waiting = TaskDraft(id = "task-waiting-2", name = "红木茶桌")
        val deleted = waiting.copy(deleted = true)

        assertFalse(
            TodoTaskRetentionPolicy.shouldRemainVisible(started, setOf(started.id)),
        )
        assertTrue(
            TodoTaskRetentionPolicy.shouldRemainVisible(waiting, setOf(started.id)),
        )
        assertFalse(
            TodoTaskRetentionPolicy.shouldRemainVisible(deleted, emptySet()),
        )
    }
}
