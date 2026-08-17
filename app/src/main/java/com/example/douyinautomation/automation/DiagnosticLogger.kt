package com.example.douyinautomation.automation

import android.util.Log
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small, in-memory diagnostic log intended for the POC's on-device diagnostics screen.
 *
 * Keep event names and messages descriptive but static. Raw node text, OCR text, search
 * keywords, messages, account identifiers, and credentials must never be passed to this API.
 * Attribute values with a sensitive-looking key are redacted defensively before storage or
 * Logcat output.
 */
class DiagnosticLogger(
    private val logTag: String = DEFAULT_LOG_TAG,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val buffer = ArrayDeque<DiagnosticEntry>(maxEntries)
    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())

    /** A snapshot stream for diagnostics UI and test assertions. */
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    init {
        require(maxEntries > 0) { "maxEntries must be greater than zero" }
    }

    fun info(
        event: String,
        message: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
    ) = log(DiagnosticLevel.INFO, event, message, attributes)

    fun warn(
        event: String,
        message: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
    ) = log(DiagnosticLevel.WARN, event, message, attributes)

    fun error(
        event: String,
        message: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        val safeAttributes = if (throwable == null) {
            attributes
        } else {
            attributes + ("cause" to (throwable::class.java.simpleName ?: "Throwable"))
        }
        log(DiagnosticLevel.ERROR, event, message, safeAttributes)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEntry> = buffer.toList()

    @Synchronized
    private fun log(
        level: DiagnosticLevel,
        event: String,
        message: String?,
        attributes: Map<String, Any?>,
    ) {
        val entry = DiagnosticEntry(
            timestampMillis = nowMillis(),
            level = level,
            event = sanitizeEvent(event),
            message = sanitizeMessage(message),
            attributes = attributes
                .entries
                .sortedBy { it.key }
                .associate { (key, value) -> sanitizeKey(key) to sanitizeValue(key, value) },
        )

        if (buffer.size == maxEntries) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
        _entries.value = buffer.toList()

        val rendered = entry.renderForLogcat()
        when (level) {
            DiagnosticLevel.INFO -> Log.i(logTag, rendered)
            DiagnosticLevel.WARN -> Log.w(logTag, rendered)
            DiagnosticLevel.ERROR -> Log.e(logTag, rendered)
        }
        if (level != DiagnosticLevel.INFO) {
            DebugToast.showForEvent(entry.event)
        }
    }

    private fun sanitizeEvent(event: String): String =
        event.trim()
            .lowercase(Locale.US)
            .replace(NON_EVENT_CHARACTER, "_")
            .take(MAX_EVENT_LENGTH)
            .ifBlank { "unspecified" }

    private fun sanitizeKey(key: String): String =
        key.trim()
            .lowercase(Locale.US)
            .replace(NON_KEY_CHARACTER, "_")
            .take(MAX_KEY_LENGTH)
            .ifBlank { "value" }

    private fun sanitizeValue(key: String, value: Any?): String {
        if (value == null) return "null"
        if (SENSITIVE_KEY.containsMatchIn(key)) return REDACTED

        return when (value) {
            is Number,
            is Boolean,
            -> value.toString()

            else -> compact(value.toString(), MAX_VALUE_LENGTH)
        }
    }

    private fun sanitizeMessage(message: String?): String? =
        message
            ?.let { compact(it, MAX_MESSAGE_LENGTH) }
            ?.takeUnless { it.isBlank() }

    private fun compact(value: String, maxLength: Int): String =
        value.replace(WHITESPACE, " ")
            .trim()
            .let { compactValue ->
                when {
                    compactValue.isEmpty() -> ""
                    SENSITIVE_VALUE.containsMatchIn(compactValue) -> REDACTED
                    compactValue.length > maxLength -> compactValue.take(maxLength - 1) + "…"
                    else -> compactValue
                }
            }

    private fun DiagnosticEntry.renderForLogcat(): String = buildString {
        append(level.name)
        append(' ')
        append(event)
        message?.let {
            append(" — ")
            append(it)
        }
        if (attributes.isNotEmpty()) {
            append(" [")
            append(attributes.entries.joinToString(", ") { (key, value) -> "$key=$value" })
            append(']')
        }
    }

    companion object {
        const val DEFAULT_LOG_TAG = "DouyinAutomation"
        private const val DEFAULT_MAX_ENTRIES = 250
        private const val MAX_EVENT_LENGTH = 64
        private const val MAX_KEY_LENGTH = 48
        private const val MAX_MESSAGE_LENGTH = 160
        private const val MAX_VALUE_LENGTH = 96
        private const val REDACTED = "[redacted]"

        private val NON_EVENT_CHARACTER = Regex("[^a-z0-9_.-]+")
        private val NON_KEY_CHARACTER = Regex("[^a-z0-9_.-]+")
        private val WHITESPACE = Regex("\\s+")
        private val SENSITIVE_KEY = Regex(
            "(?i)(text|message|query|keyword|content|token|secret|password|authorization|cookie|" +
                "phone|email|account|user(name|id)?|profile|url)",
        )
        private val SENSITIVE_VALUE = Regex(
            "(?i)(bearer\\s+\\S+|https?://\\S+|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|" +
                "(?:\\+?\\d[ -]?){8,}\\d)",
        )
    }
}

data class DiagnosticEntry(
    val timestampMillis: Long,
    val level: DiagnosticLevel,
    val event: String,
    val message: String?,
    val attributes: Map<String, String>,
)

enum class DiagnosticLevel {
    INFO,
    WARN,
    ERROR,
}
