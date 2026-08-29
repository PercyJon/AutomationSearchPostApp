package com.example.douyinautomation.automation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PersistedDeviceLog(
    val seq: Long,
    val timestampMillis: Long,
    val level: String,
    val event: String,
    val message: String?,
    val attributes: Map<String, String>,
) {
    fun toJsonLine(): String {
        val attrs = attributes.entries
            .sortedBy { it.key }
            .joinToString(",") { (key, value) -> "${jsonString(key)}:${jsonString(value)}" }
        return buildString {
            append('{')
            append("\"seq\":").append(seq)
            append(",\"ts\":").append(timestampMillis)
            append(",\"level\":").append(jsonString(level))
            append(",\"event\":").append(jsonString(event))
            append(",\"message\":").append(message?.let(::jsonString) ?: "null")
            append(",\"attributes\":{").append(attrs).append('}')
            append('}')
        }
    }

    fun renderForAgent(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val stamped = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).format(AGENT_TIMESTAMP)
        return buildString {
            append(stamped)
            append(' ')
            append(level)
            append(' ')
            append(event)
            message?.takeIf(String::isNotBlank)?.let {
                append(" — ")
                append(it)
            }
            if (attributes.isNotEmpty()) {
                append(" [")
                append(attributes.entries.joinToString(", ") { (key, value) -> "$key=$value" })
                append(']')
            }
        }
    }

    companion object {
        private val AGENT_TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun fromJsonLine(raw: String): PersistedDeviceLog? {
            val line = raw.trim()
            if (line.isEmpty()) return null
            return runCatching {
                val payload = parseObject(line)
                val seq = payload.long("seq") ?: return null
                if (seq < 1L) return null
                val attributes = linkedMapOf<String, String>()
                payload.obj("attributes")?.entries?.forEach { (key, value) ->
                    if (value is String) attributes[key] = value
                }
                PersistedDeviceLog(
                    seq = seq,
                    timestampMillis = payload.long("ts") ?: 0L,
                    level = payload.str("level")?.ifBlank { "INFO" } ?: "INFO",
                    event = payload.str("event")?.ifBlank { "unspecified" } ?: "unspecified",
                    message = payload.str("message")?.takeIf { it.isNotBlank() },
                    attributes = attributes,
                )
            }.getOrNull()
        }

        fun fromEntry(seq: Long, entry: DiagnosticEntry): PersistedDeviceLog = PersistedDeviceLog(
            seq = seq,
            timestampMillis = entry.timestampMillis,
            level = entry.level.name,
            event = entry.event,
            message = entry.message,
            attributes = entry.attributes,
        )
    }
}

interface DeviceLogSeqStore {
    fun nextSeq(): Long
    fun lastAckedSeq(): Long
    fun setAckedSeq(seq: Long)
}

class MemoryDeviceLogSeqStore : DeviceLogSeqStore {
    private var next = 1L
    private var acked = 0L

    @Synchronized
    override fun nextSeq(): Long = next++

    @Synchronized
    override fun lastAckedSeq(): Long = acked

    @Synchronized
    override fun setAckedSeq(seq: Long) {
        if (seq > acked) acked = seq
    }
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

private data class JsonObject(val entries: Map<String, Any?>) {
    fun str(key: String): String? = entries[key] as? String
    fun long(key: String): Long? = when (val value = entries[key]) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    fun obj(key: String): JsonObject? = entries[key] as? JsonObject
}

private fun parseObject(raw: String): JsonObject {
    val parser = JsonParser(raw)
    val value = parser.parseValue()
    parser.skipWhitespace()
    require(parser.done) { "trailing json" }
    return value as? JsonObject ?: error("expected object")
}

private class JsonParser(private val source: String) {
    private var index = 0
    val done: Boolean get() = index >= source.length

    fun parseValue(): Any? {
        skipWhitespace()
        if (done) error("unexpected end")
        return when (val ch = source[index]) {
            '{' -> parseObject()
            '"' -> parseString()
            'n' -> {
                expect("null")
                null
            }
            '-', in '0'..'9' -> parseNumber()
            else -> error("unexpected $ch")
        }
    }

    private fun parseObject(): JsonObject {
        expect("{")
        val entries = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonObject(entries)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(":")
            entries[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return JsonObject(entries)
                }
                else -> error("expected comma or brace")
            }
        }
    }

    private fun parseString(): String {
        expect("\"")
        val out = StringBuilder()
        while (!done) {
            when (val ch = source[index++]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (done) error("unterminated escape")
                    out.append(
                        when (val escaped = source[index++]) {
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                require(index + 4 <= source.length) { "bad unicode" }
                                val hex = source.substring(index, index + 4)
                                index += 4
                                hex.toInt(16).toChar()
                            }
                            else -> escaped
                        },
                    )
                }
                else -> out.append(ch)
            }
        }
        error("unterminated string")
    }

    private fun parseNumber(): Long {
        val start = index
        if (peek() == '-') index++
        while (!done && source[index] in '0'..'9') index++
        return source.substring(start, index).toLong()
    }

    fun skipWhitespace() {
        while (!done && source[index].isWhitespace()) index++
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun expect(token: String) {
        skipWhitespace()
        require(source.startsWith(token, index)) { "expected $token" }
        index += token.length
    }
}
