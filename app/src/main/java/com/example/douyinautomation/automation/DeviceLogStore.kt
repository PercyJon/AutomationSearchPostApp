package com.example.douyinautomation.automation

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class DeviceLogStore(
    private val directory: File,
    private val seqStore: DeviceLogSeqStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    @Synchronized
    fun append(entry: DiagnosticEntry): PersistedDeviceLog {
        directory.mkdirs()
        pruneExpiredFilesLocked()
        val persisted = PersistedDeviceLog.fromEntry(seqStore.nextSeq(), entry)
        dayFile(persisted.timestampMillis).appendText(persisted.toJsonLine() + "\n", Charsets.UTF_8)
        return persisted
    }

    @Synchronized
    fun pendingBatch(limit: Int = MAX_BATCH_ENTRIES, maxBytes: Int = MAX_BATCH_BYTES): List<PersistedDeviceLog> {
        directory.mkdirs()
        pruneExpiredFilesLocked()
        val acked = seqStore.lastAckedSeq()
        val pending = mutableListOf<PersistedDeviceLog>()
        var bytes = 0
        dayFiles()
            .sortedBy { it.name }
            .forEach { file ->
                file.forEachLine(Charsets.UTF_8) { raw ->
                    if (pending.size >= limit || bytes >= maxBytes) return@forEachLine
                    val item = PersistedDeviceLog.fromJsonLine(raw) ?: return@forEachLine
                    if (item.seq <= acked) return@forEachLine
                    val encoded = item.toJsonLine()
                    if (pending.isNotEmpty() && bytes + encoded.length > maxBytes) return@forEachLine
                    pending += item
                    bytes += encoded.length
                }
            }
        return pending
    }

    @Synchronized
    fun ack(seq: Long) {
        if (seq < 1L) return
        seqStore.setAckedSeq(seq)
        val acked = seqStore.lastAckedSeq()
        dayFiles().forEach { file ->
            val remaining = file.readLines(Charsets.UTF_8).mapNotNull(PersistedDeviceLog::fromJsonLine)
            if (remaining.isEmpty() || remaining.all { it.seq <= acked }) {
                file.delete()
                return@forEach
            }
            if (remaining.any { it.seq <= acked }) {
                file.writeText(
                    remaining.filter { it.seq > acked }.joinToString("") { it.toJsonLine() + "\n" },
                    Charsets.UTF_8,
                )
            }
        }
        pruneExpiredFilesLocked()
    }

    @Synchronized
    fun lastAckedSeq(): Long = seqStore.lastAckedSeq()

    @Synchronized
    fun pruneExpiredFiles() {
        directory.mkdirs()
        pruneExpiredFilesLocked()
    }

    private fun pruneExpiredFilesLocked() {
        val cutoff = LocalDate.now(zoneId).minusDays(RETENTION_DAYS)
        dayFiles().forEach { file ->
            val day = runCatching { LocalDate.parse(file.name.removeSuffix(".jsonl"), DAY_FILE) }.getOrNull()
            if (day != null && day.isBefore(cutoff)) {
                file.delete()
            } else if (nowMillis() - file.lastModified() > TimeUnit.DAYS.toMillis(RETENTION_DAYS + 1)) {
                file.delete()
            }
        }
    }

    private fun dayFile(timestampMillis: Long): File {
        val day = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate().format(DAY_FILE)
        return File(directory, "$day.jsonl")
    }

    private fun dayFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".jsonl") }?.toList().orEmpty()

    companion object {
        const val RETENTION_DAYS = 5L
        const val MAX_BATCH_ENTRIES = 200
        const val MAX_BATCH_BYTES = 64 * 1024
        private val DAY_FILE = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
