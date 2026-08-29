package com.example.douyinautomation.automation

import java.io.File
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DeviceLogStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun seqContinuesAndAckStopsRetransmit() {
        val seq = MemoryDeviceLogSeqStore()
        val store = DeviceLogStore(directory = folder.root, seqStore = seq, zoneId = ZoneOffset.UTC)
        val logger = DiagnosticLogger(logTag = "test")
        logger.persist = { store.append(it) }

        logger.info("first_event")
        logger.info("second_event", attributes = mapOf("token" to "secret-token"))
        val pending = store.pendingBatch()
        assertEquals(listOf(1L, 2L), pending.map { it.seq })
        assertEquals("[redacted]", pending[1].attributes["token"])
        assertFalse(pending.any { it.toJsonLine().contains("secret-token") })

        store.ack(1L)
        assertEquals(listOf(2L), store.pendingBatch().map { it.seq })
        store.ack(2L)
        assertTrue(store.pendingBatch().isEmpty())
    }

    @Test
    fun expiredDayFilesAreDeleted() {
        val seq = MemoryDeviceLogSeqStore()
        val store = DeviceLogStore(directory = folder.root, seqStore = seq, zoneId = ZoneOffset.UTC)
        val stale = File(folder.root, "2020-01-01.jsonl")
        stale.writeText("""{"seq":1,"ts":1,"level":"INFO","event":"old","message":null,"attributes":{}}""" + "\n")
        store.pruneExpiredFiles()
        assertFalse(stale.exists())
    }

    @Test
    fun agentLineIncludesTimestampAndOmitsBlankMessage() {
        val line = PersistedDeviceLog(
            seq = 3,
            timestampMillis = 0L,
            level = "INFO",
            event = "task_message_mode",
            message = null,
            attributes = mapOf("safety_probe" to "false"),
        ).renderForAgent(ZoneOffset.UTC)
        assertTrue(line.startsWith("1970-01-01T00:00:00Z INFO task_message_mode"))
        assertTrue(line.contains("safety_probe=false"))
        assertFalse(line.contains("https://"))
    }
}
