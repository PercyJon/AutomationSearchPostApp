package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression scenario for the three-task batch used during M2.5 acceptance.
 * The queue must preserve the operator's order and each snapshot must retain its own limit.
 */
class MultiTaskScenarioTest {
    private val catalog = SearchPresetCatalog(
        version = "test",
        items = emptyList(),
        updatedAtMillis = 0L,
        source = SearchPresetCatalog.Source.BUILT_IN,
    )

    @Test
    fun configuredTasksKeepKeywordOrderAndLimits() {
        val drafts = listOf(
            TaskDraft("foshan-redwood", "佛山红木家具", customKeywords = listOf("佛山红木家具"), maxUsers = 5),
            TaskDraft("foshan-sofa", "佛山沙发家具", customKeywords = listOf("佛山沙发家具"), maxUsers = 5),
            TaskDraft("redwood", "红木家具", customKeywords = listOf("红木家具"), maxUsers = 10),
        )
        val snapshots = drafts.map { it.toSnapshot(catalog, nowMillis = 1L) }
        val queue = SequentialTaskQueue<TaskSnapshot>()
        queue.replace(snapshots)

        val first = queue.poll()
        assertTrue(first != null)
        assertEquals("佛山红木家具", snapshots[0].composedQueries.single())
        assertEquals("佛山沙发家具", snapshots[1].composedQueries.single())
        assertEquals("红木家具", snapshots[2].composedQueries.single())
        assertEquals(listOf(5, 5, 10), snapshots.map(TaskSnapshot::maxUsers))
        assertEquals("佛山红木家具", first?.taskName)
        assertEquals("佛山沙发家具", queue.poll()?.taskName)
        assertEquals("红木家具", queue.poll()?.taskName)
        assertTrue(queue.isEmpty)
    }
}
