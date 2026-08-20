package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
