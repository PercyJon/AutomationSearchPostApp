package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Commands emitted by the explicitly-operated diagnostics UI. */
sealed interface AutomationCommand {
    data class Start(
        val keyword: String,
        /** Kept for compatibility with the M1 UI; M2 does not send this value to Douyin. */
        val message: String = "",
        /** M2 default: submit one space and require Douyin's blank-message notice. */
        val safetyProbe: Boolean = true,
        /** Optional M3 task snapshot; when absent the legacy single-keyword POC remains valid. */
        val taskSnapshot: TaskSnapshot? = null,
    ) : AutomationCommand
    /** Explicitly sends one operator-provided message on the currently verified chat page. */
    data class SendMessage(val message: String) : AutomationCommand
    data object Pause : AutomationCommand
    data object Resume : AutomationCommand
    data object Stop : AutomationCommand
    data object CaptureDiagnostics : AutomationCommand
    data object DumpNodeTree : AutomationCommand
}

/** M2 advances through users using a blank-message safety probe; no real message is sent by the UI. */
enum class AutomationPhase {
    IDLE,
    SERVICE_READY,
    LAUNCHING_TARGET,
    WAITING_FOR_HOME,
    WAITING_FOR_SEARCH_ENTRY,
    OPENING_SEARCH,
    ENTERING_KEYWORD,
    WAITING_FOR_SEARCH_RESULTS,
    SELECTING_USER_TAB,
    WAITING_FOR_USER_RESULTS,
    SELECTING_USER_RESULT,
    WAITING_FOR_PROFILE,
    OPENING_MESSAGE_ENTRY,
    WAITING_FOR_DIRECT_MESSAGE,
    COMPLETED_AT_MESSAGE_PAGE,
    VERIFYING_EMPTY_MESSAGE,
    WAITING_FOR_EMPTY_MESSAGE_RESULT,
    COMPLETED_EMPTY_MESSAGE_PROBE,
    SENDING_MESSAGE,
    WAITING_FOR_MESSAGE_RESULT,
    COMPLETED_MESSAGE_SENT,
    PAUSED_FOR_MANUAL_HANDOFF,
    STOPPED,
    FAILED,
}

data class AutomationUiState(
    val serviceConnected: Boolean = false,
    val serviceStatusKnown: Boolean = false,
    val phase: AutomationPhase = AutomationPhase.IDLE,
    val lastPage: PageDetection? = null,
    val lastNodeDumpPath: String? = null,
    val lastScreenshotPath: String? = null,
    /** OCR is held in process/app-private diagnostics only; it is never sent to Logcat. */
    val lastOcrText: String? = null,
    val lastError: String? = null,
    val awaitingManualHandoff: Boolean = false,
    /** Current task metadata and counters are intentionally visible for operator diagnosis. */
    val taskId: String? = null,
    val taskStartedAtMillis: Long? = null,
    val taskHandledUserCount: Int = 0,
    val taskDuplicateUserCount: Int = 0,
    val taskLastEvent: String? = null,
    val taskRecords: List<UserTaskRecord> = emptyList(),
    val diagnosticEntries: List<DiagnosticEntry> = emptyList(),
)

/**
 * Process-local bridge between the Compose diagnostics surface and the enabled accessibility
 * service. Commands are only accepted once Android has connected the service; this prevents the
 * app UI from appearing to control a service that the user has not explicitly enabled.
 */
object AutomationStore {
    val logger = DiagnosticLogger(logTag = "DyinPoc")

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _commands = MutableSharedFlow<AutomationCommand>(extraBufferCapacity = 16)
    private val _uiState = MutableStateFlow(AutomationUiState())
    private val recordLock = Any()
    private var recordPreferences: SharedPreferences? = null
    private var allTaskRecords: List<UserTaskRecord> = emptyList()
    private var currentTaskId: String? = null

    val commands: SharedFlow<AutomationCommand> = _commands.asSharedFlow()
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        storeScope.launch {
            logger.entries.collect { entries ->
                _uiState.update { current -> current.copy(diagnosticEntries = entries) }
            }
        }
    }

    /** Load the private task history once the Android service has a Context. */
    fun initialize(context: Context) {
        synchronized(recordLock) {
            if (recordPreferences != null) return
            recordPreferences = context.applicationContext.getSharedPreferences(
                TASK_RECORDS_PREFERENCES,
                Context.MODE_PRIVATE,
            )
            allTaskRecords = decodeRecords(recordPreferences?.getString(TASK_RECORDS_KEY, null))
        }
    }

    /** Starts a new in-memory task and returns its stable id for later task publishing. */
    fun beginTask(keyword: String, snapshot: TaskSnapshot? = null): String {
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        synchronized(recordLock) { currentTaskId = taskId }
        _uiState.update {
            it.copy(
                taskId = taskId,
                taskStartedAtMillis = now,
                taskHandledUserCount = 0,
                taskDuplicateUserCount = 0,
                taskLastEvent = "TASK_STARTED",
                taskRecords = emptyList(),
            )
        }
        logger.info(
            "task_started",
            attributes = buildMap {
                put("task_id_hash", taskId.hashCode())
                put("keyword_hash", keyword.hashCode())
                snapshot?.let {
                    put("query_count", it.composedQueries.size)
                    put("blocked_keyword_count", it.normalizedBlockedKeywords.size)
                    put("preset_version_hash", it.presetVersion.hashCode())
                }
            },
        )
        return taskId
    }

    /** Create the durable per-user record when a unique row is first selected. */
    fun recordUserTaskStarted(identityHash: Int, page: PageKind? = PageKind.USER_RESULTS) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        logger.info(
            "task_user_started",
            attributes = mapOf("task_id_hash" to taskId.hashCode(), "identity_hash" to identityHash),
        )
        appendTaskRecord(
            UserTaskRecord(
                recordId = UUID.randomUUID().toString(),
                taskId = taskId,
                identityHash = identityHash,
                outcome = UserTaskRecord.Outcome.IN_PROGRESS,
                startedAtMillis = System.currentTimeMillis(),
                page = page,
            ),
        )
    }

    /** Finish the current user's record without creating a second row for the same attempt. */
    fun recordUserTaskFinished(
        identityHash: Int?,
        outcome: UserTaskRecord.Outcome,
        reason: String? = null,
        page: PageKind? = null,
    ) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        logger.info(
            "task_user_finished",
            attributes = mapOf(
                "task_id_hash" to taskId.hashCode(),
                "identity_hash" to (identityHash ?: 0),
                "outcome" to outcome.name,
            ),
        )
        val now = System.currentTimeMillis()
        synchronized(recordLock) {
            val index = allTaskRecords.indexOfLast {
                it.taskId == taskId && it.identityHash == identityHash && it.outcome == UserTaskRecord.Outcome.IN_PROGRESS
            }
            if (index >= 0) {
                val updated = allTaskRecords[index].copy(
                    outcome = outcome,
                    finishedAtMillis = now,
                    page = page ?: allTaskRecords[index].page,
                    reason = reason,
                )
                allTaskRecords = allTaskRecords.toMutableList().also { it[index] = updated }
                persistRecordsLocked()
                publishCurrentTaskRecordsLocked(updated.outcome.name)
            } else {
                appendTaskRecordLocked(
                    UserTaskRecord(
                        recordId = UUID.randomUUID().toString(),
                        taskId = taskId,
                        identityHash = identityHash,
                        outcome = outcome,
                        startedAtMillis = now,
                        finishedAtMillis = now,
                        page = page,
                        reason = reason,
                    ),
                )
            }
        }
    }

    /** Record a row-level event such as a duplicate or follow-back skip. */
    fun recordUserTaskEvent(
        identityHash: Int?,
        outcome: UserTaskRecord.Outcome,
        reason: String? = null,
        page: PageKind? = PageKind.USER_RESULTS,
    ) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        logger.info(
            "task_user_event",
            attributes = mapOf(
                "task_id_hash" to taskId.hashCode(),
                "identity_hash" to (identityHash ?: 0),
                "outcome" to outcome.name,
            ),
        )
        val now = System.currentTimeMillis()
        appendTaskRecord(
            UserTaskRecord(
                recordId = UUID.randomUUID().toString(),
                taskId = taskId,
                identityHash = identityHash,
                outcome = outcome,
                startedAtMillis = now,
                finishedAtMillis = now,
                page = page,
                reason = reason,
            ),
        )
    }

    private fun appendTaskRecord(record: UserTaskRecord) {
        synchronized(recordLock) { appendTaskRecordLocked(record) }
    }

    private fun appendTaskRecordLocked(record: UserTaskRecord) {
        allTaskRecords = (allTaskRecords + record).takeLast(MAX_TASK_RECORDS)
        persistRecordsLocked()
        publishCurrentTaskRecordsLocked(record.outcome.name)
    }

    private fun publishCurrentTaskRecordsLocked(lastEvent: String) {
        val taskId = currentTaskId ?: return
        val records = allTaskRecords.filter { it.taskId == taskId }
        _uiState.update {
            it.copy(
                taskRecords = records,
                taskHandledUserCount = records.count { record ->
                    record.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                taskDuplicateUserCount = records.count { record ->
                    record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                taskLastEvent = lastEvent,
            )
        }
    }

    private fun persistRecordsLocked() {
        recordPreferences?.edit()?.putString(
            TASK_RECORDS_KEY,
            JSONArray(allTaskRecords.map { it.toJson() }).toString(),
        )?.apply()
    }

    private fun UserTaskRecord.toJson(): JSONObject = JSONObject().apply {
        put("record_id", recordId)
        put("task_id", taskId)
        put("identity_hash", identityHash)
        put("outcome", outcome.name)
        put("started_at", startedAtMillis)
        put("finished_at", finishedAtMillis ?: JSONObject.NULL)
        put("page", page?.name ?: JSONObject.NULL)
        put("reason", reason ?: JSONObject.NULL)
    }

    private fun decodeRecords(raw: String?): List<UserTaskRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(minOf(json.length(), MAX_TASK_RECORDS)) {
                val start = (json.length() - MAX_TASK_RECORDS).coerceAtLeast(0)
                for (index in start until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        UserTaskRecord(
                            recordId = item.getString("record_id"),
                            taskId = item.getString("task_id"),
                            identityHash = if (item.isNull("identity_hash")) null else item.getInt("identity_hash"),
                            outcome = UserTaskRecord.Outcome.valueOf(item.getString("outcome")),
                            startedAtMillis = item.getLong("started_at"),
                            finishedAtMillis = if (item.isNull("finished_at")) null else item.getLong("finished_at"),
                            page = if (item.isNull("page")) null else PageKind.valueOf(item.getString("page")),
                            reason = if (item.isNull("reason")) null else item.getString("reason"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun send(command: AutomationCommand) {
        if (!_uiState.value.serviceConnected) {
            val reason = "Enable the accessibility service before running this command."
            logger.warn("command_rejected", message = reason, attributes = mapOf("command" to command.name()))
            publishFailure(reason)
            return
        }
        if (!_commands.tryEmit(command)) {
            logger.warn("command_dropped", attributes = mapOf("command" to command.name()))
            publishFailure("The service command queue is full. Try again after the current action finishes.")
        }
    }

    fun markServiceConnected() {
        _uiState.update {
            it.copy(
                serviceConnected = true,
                serviceStatusKnown = true,
                phase = if (it.phase == AutomationPhase.IDLE) AutomationPhase.SERVICE_READY else it.phase,
                lastError = null,
            )
        }
    }

    fun markServiceDisconnected() {
        // AccessibilityService can be briefly destroyed and rebound during an APK update or
        // a screenshot request. Keep the last known connection while the activity's scheduled
        // refreshes verify the new binding; this avoids a misleading "not connected" flash.
        _uiState.update { it.copy(serviceStatusKnown = false) }
    }

    /**
     * Accessibility services may be bound before an activity is recreated after a settings change
     * or APK update. Ask Android for the source of truth whenever the diagnostics activity resumes
     * so the UI does not show a stale "not connected" state.
     */
    fun refreshServiceStatus(context: Context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val expectedServiceName = DouyinAccessibilityService::class.java.name
        val expectedServiceId = "${context.packageName}/$expectedServiceName"
        val enabledServices = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        val isEnabled = enabledServices.any { serviceInfo ->
            val resolved = serviceInfo.resolveInfo?.serviceInfo
            val resolvedComponentMatches = resolved?.packageName == context.packageName &&
                (resolved.name == expectedServiceName ||
                    resolved.name.endsWith(DouyinAccessibilityService::class.java.simpleName))
            val serviceId = serviceInfo.id.orEmpty()
            val serviceIdMatches = serviceId == expectedServiceId ||
                serviceId.endsWith("/$expectedServiceName") ||
                serviceId.endsWith("/${DouyinAccessibilityService::class.java.simpleName}")
            resolvedComponentMatches || serviceIdMatches
        }
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val normalizedSetting = enabledSetting.orEmpty().replace(" ", "")
        val settingMatches = normalizedSetting
            .split(':')
            .any { value ->
                value.equals(expectedServiceId, ignoreCase = true) ||
                    value.endsWith("/$expectedServiceName", ignoreCase = true) ||
                    value.endsWith("/${DouyinAccessibilityService::class.java.simpleName}", ignoreCase = true) ||
                    (value.contains(context.packageName, ignoreCase = true) &&
                        value.contains(DouyinAccessibilityService::class.java.simpleName, ignoreCase = true))
            }
        val targetEnabled = isEnabled || settingMatches
        logger.info(
            "service_status_refreshed",
            attributes = mapOf("enabled_services" to enabledServices.size, "target_enabled" to targetEnabled),
        )

        _uiState.update { current ->
            current.copy(
                serviceConnected = targetEnabled,
                serviceStatusKnown = true,
                phase = if (targetEnabled && current.phase == AutomationPhase.IDLE) {
                    AutomationPhase.SERVICE_READY
                } else {
                    current.phase
                },
            )
        }
    }

    fun publishPhase(
        phase: AutomationPhase,
        error: String? = null,
        awaitingManualHandoff: Boolean = false,
    ) {
        _uiState.update {
            it.copy(
                phase = phase,
                lastError = error ?: it.lastError.takeUnless { phase != AutomationPhase.FAILED },
                awaitingManualHandoff = awaitingManualHandoff,
            )
        }
    }

    fun publishObservation(detection: PageDetection) {
        _uiState.update { it.copy(lastPage = detection) }
    }

    fun publishNodeDump(path: String) {
        _uiState.update { it.copy(lastNodeDumpPath = path) }
    }

    fun publishScreenshot(path: String) {
        _uiState.update { it.copy(lastScreenshotPath = path) }
    }

    fun publishOcr(text: String) {
        _uiState.update { it.copy(lastOcrText = text.take(MAX_OCR_PREVIEW)) }
    }

    fun publishFailure(reason: String) {
        _uiState.update {
            it.copy(
                phase = AutomationPhase.FAILED,
                lastError = reason,
                awaitingManualHandoff = false,
            )
        }
    }

    fun publishManualHandoff(reason: String) {
        _uiState.update {
            it.copy(
                phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
                lastError = reason,
                awaitingManualHandoff = true,
            )
        }
    }

    private fun AutomationCommand.name(): String = when (this) {
        is AutomationCommand.Start -> "start"
        is AutomationCommand.SendMessage -> "send_message"
        AutomationCommand.Pause -> "pause"
        AutomationCommand.Resume -> "resume"
        AutomationCommand.Stop -> "stop"
        AutomationCommand.CaptureDiagnostics -> "capture_diagnostics"
        AutomationCommand.DumpNodeTree -> "dump_node_tree"
    }

    private const val MAX_OCR_PREVIEW = 1_000
    private const val TASK_RECORDS_PREFERENCES = "automation_task_records"
    private const val TASK_RECORDS_KEY = "records"
    private const val MAX_TASK_RECORDS = 2_000
}
