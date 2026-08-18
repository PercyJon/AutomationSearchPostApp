package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
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
        /** Optional backend progress fetched immediately before a remote task is resumed. */
        val remoteResume: RemoteTaskResume? = null,
    ) : AutomationCommand
    /** Explicitly sends one operator-provided message on the currently verified chat page. */
    data class SendMessage(val message: String) : AutomationCommand
    /** Explicitly resumes the last private checkpoint after the operator reviews the screen. */
    data object ResumeSavedTask : AutomationCommand
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
    COMPLETED_TASK,
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
    val taskName: String? = null,
    val taskQueryIndex: Int = 0,
    val taskQueryCount: Int = 0,
    val taskMaxUsers: Int? = null,
    val taskBlockedKeywordCount: Int = 0,
    val taskStartedAtMillis: Long? = null,
    val taskHandledUserCount: Int = 0,
    val taskDuplicateUserCount: Int = 0,
    val taskLastEvent: String? = null,
    val remoteTaskId: Long? = null,
    val remoteTaskStatus: Int? = null,
    val remoteTaskStatusUpdatedAtMillis: Long? = null,
    val remoteSyncPendingCount: Int = 0,
    val remoteSyncLastError: String? = null,
    val savedTaskAvailable: Boolean = false,
    val savedTaskName: String? = null,
    val savedTaskQueryIndex: Int = 0,
    val savedTaskQueryCount: Int = 0,
    val taskRecords: List<UserTaskRecord> = emptyList(),
    /** All persisted per-user records, used by the Records tab across historical tasks. */
    val recordEntries: List<UserTaskRecord> = emptyList(),
    val taskHistory: List<TaskHistoryEntry> = emptyList(),
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
    private var applicationContext: Context? = null
    private var lastRecordsOpenAtMillis: Long = 0L
    private var recordPreferences: SharedPreferences? = null
    private var allTaskRecords: List<UserTaskRecord> = emptyList()
    private var taskHistory: List<TaskHistoryEntry> = emptyList()
    private var currentTaskId: String? = null
    private var savedCheckpoint: TaskCheckpoint? = null
    private var remoteTaskId: Long? = null
    private var remoteGateway: AutomationTaskGateway? = null
    private var remoteSyncQueue: RemoteTaskSyncQueue? = null
    private var lastRemoteStatusSignature: String? = null
    private var remoteConfigFingerprint: Int? = null
    private val remoteUserKeys = mutableMapOf<Int, String>()
    private val remoteDisplayNames = mutableMapOf<Int, String>()

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
        applicationContext = context.applicationContext
        AuthStore.initialize(context)
        synchronized(recordLock) {
            if (recordPreferences == null) {
                recordPreferences = context.applicationContext.getSharedPreferences(
                    TASK_RECORDS_PREFERENCES,
                    Context.MODE_PRIVATE,
                )
                allTaskRecords = decodeRecords(recordPreferences?.getString(TASK_RECORDS_KEY, null))
                taskHistory = decodeTaskHistory(recordPreferences?.getString(TASK_HISTORY_KEY, null))
                savedCheckpoint = decodeCheckpoint(recordPreferences?.getString(TASK_CHECKPOINT_KEY, null))
            }
            // A process restart or accessibility-service rebind cannot keep an old controller
            // running. Reconcile persisted RUNNING entries before publishing the Records tab so
            // stale tasks never look executable forever.
            if (currentTaskId == null) {
                val interruptedAt = System.currentTimeMillis()
                val reconciled = taskHistory.map { entry ->
                    if (entry.status == TaskRunStatus.RUNNING) {
                        entry.copy(
                            status = TaskRunStatus.FAILED,
                            updatedAtMillis = interruptedAt,
                            errorMessage = entry.errorMessage
                                ?: "应用重启或无障碍服务重连时任务未完成",
                        )
                    } else {
                        entry
                    }
                }
                if (reconciled != taskHistory) {
                    taskHistory = reconciled
                    persistTaskHistoryLocked()
                }
            }
            val latestTaskId = taskHistory.maxByOrNull(TaskHistoryEntry::updatedAtMillis)?.taskId
            val latestTaskRecords = latestTaskId?.let { id -> allTaskRecords.filter { it.taskId == id } }.orEmpty()
            _uiState.update { current ->
                current.copy(
                    savedTaskAvailable = savedCheckpoint != null,
                    savedTaskName = savedCheckpoint?.snapshot?.taskName,
                    savedTaskQueryIndex = savedCheckpoint?.queryIndex ?: 0,
                    savedTaskQueryCount = savedCheckpoint?.snapshot?.composedQueries?.size ?: 0,
                    taskRecords = latestTaskRecords,
                    recordEntries = allTaskRecords,
                    taskHandledUserCount = latestTaskRecords.count { it.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED },
                    taskDuplicateUserCount = latestTaskRecords.count { it.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED },
                    taskHistory = taskHistory,
                )
            }
            logger.info(
                "task_records_loaded",
                attributes = mapOf(
                    "records" to allTaskRecords.size,
                    "history" to taskHistory.size,
                    "latest_records" to latestTaskRecords.size,
                ),
            )
        }
        refreshRemoteSync()
    }

    /** Bring the operator back to the in-app Records tab after any terminal task outcome. */
    fun openRecordsTab() {
        val context = applicationContext ?: return
        val now = System.currentTimeMillis()
        synchronized(recordLock) {
            if (now - lastRecordsOpenAtMillis < RECORDS_OPEN_THROTTLE_MS) return
            lastRecordsOpenAtMillis = now
        }
        context.startActivity(
            Intent(context, com.example.douyinautomation.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(com.example.douyinautomation.MainActivity.EXTRA_OPEN_RECORDS, true)
            },
        )
    }

    fun getSavedCheckpoint(): TaskCheckpoint? = synchronized(recordLock) { savedCheckpoint }

    fun getCurrentTaskId(): String? = synchronized(recordLock) { currentTaskId }

    /**
     * Returns the frozen task contract when Android has rebound the accessibility service while
     * the same process still owns a running task.  AccessibilityService instances are transient:
     * OEMs and diagnostic tooling can destroy and recreate the service without killing the app
     * process.  The new controller must be allowed to rehydrate this checkpoint instead of
     * leaving the task in RUNNING/LAUNCHING_TARGET forever.
     *
     * A paused, stopped, completed, or failed task is deliberately excluded; those states always
     * require an explicit operator action.
     */
    fun getCheckpointForServiceRebind(): TaskCheckpoint? = synchronized(recordLock) {
        val checkpoint = savedCheckpoint ?: return@synchronized null
        val taskId = currentTaskId ?: return@synchronized null
        val history = taskHistory.firstOrNull { it.taskId == taskId } ?: return@synchronized null
        if (history.status != TaskRunStatus.RUNNING || checkpoint.taskId != taskId) {
            return@synchronized null
        }
        checkpoint
    }

    /** Persist the last task configuration so an operator can resume setup after app recreation. */
    fun loadTaskDraft(): TaskDraft? = synchronized(recordLock) {
        decodeTaskDraft(recordPreferences?.getString(TASK_DRAFT_KEY, null))
    }

    fun saveTaskDraft(draft: TaskDraft) {
        synchronized(recordLock) {
            recordPreferences?.edit()
                ?.putString(TASK_DRAFT_KEY, encodeTaskDraft(draft).toString())
                ?.apply()
        }
    }

    fun clearTaskDraft() {
        synchronized(recordLock) {
            recordPreferences?.edit()?.remove(TASK_DRAFT_KEY)?.apply()
        }
    }

    /** Starts a new in-memory task and returns its stable id for later task publishing. */
    fun beginTask(keyword: String, snapshot: TaskSnapshot? = null): String {
        val taskId = UUID.randomUUID().toString()
        beginTaskInternal(taskId, keyword, snapshot, queryIndex = 0, resetRecords = true)
        return taskId
    }

    /** Rehydrates a reviewed checkpoint without creating a second task history. */
    fun resumeTask(checkpoint: TaskCheckpoint): String {
        beginTaskInternal(
            taskId = checkpoint.taskId,
            keyword = checkpoint.snapshot.composedQueries[checkpoint.queryIndex],
            snapshot = checkpoint.snapshot,
            queryIndex = checkpoint.queryIndex,
            resetRecords = false,
        )
        return checkpoint.taskId
    }

    private fun beginTaskInternal(
        taskId: String,
        keyword: String,
        snapshot: TaskSnapshot?,
        queryIndex: Int,
        resetRecords: Boolean,
    ) {
        refreshRemoteSync()
        val now = System.currentTimeMillis()
        val existingRecords: List<UserTaskRecord>
        synchronized(recordLock) {
            currentTaskId = taskId
            remoteTaskId = snapshot?.taskId?.toLongOrNull()?.takeIf { it > 0L }
            lastRemoteStatusSignature = null
            remoteUserKeys.clear()
            remoteDisplayNames.clear()
            if (resetRecords) {
                allTaskRecords = allTaskRecords.filterNot { it.taskId == taskId }
            }
            if (snapshot == null) {
                savedCheckpoint = null
                recordPreferences?.edit()?.remove(TASK_CHECKPOINT_KEY)?.apply()
            }
            existingRecords = allTaskRecords.filter { record -> record.taskId == taskId }
            val previous = taskHistory.firstOrNull { it.taskId == taskId }
            val historyEntry = TaskHistoryEntry(
                taskId = taskId,
                taskName = snapshot?.taskName ?: keyword,
                queryCount = snapshot?.composedQueries?.size ?: 1,
                maxUsers = snapshot?.maxUsers ?: TaskDraft.DEFAULT_MAX_USERS,
                startedAtMillis = previous?.startedAtMillis ?: now,
                updatedAtMillis = now,
                status = TaskRunStatus.RUNNING,
                handledCount = existingRecords.count { record ->
                    record.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                skippedCount = existingRecords.count { record ->
                    record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                filteredCount = existingRecords.count { record ->
                    record.outcome == UserTaskRecord.Outcome.FILTERED_BY_KEYWORD
                },
                duplicateCount = existingRecords.count { record ->
                    record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                searchQueries = snapshot?.composedQueries ?: listOf(keyword),
                region = snapshot?.region,
                blockedKeywords = snapshot?.normalizedBlockedKeywords.orEmpty(),
                messageTemplate = snapshot?.messageTemplate,
                executionMode = snapshot?.executionMode ?: TaskExecutionMode.SAFE_BLANK_PROBE,
                errorMessage = null,
            )
            taskHistory = (taskHistory.filterNot { it.taskId == taskId } + historyEntry).takeLast(MAX_TASK_HISTORY)
            persistTaskHistoryLocked()
        }
        _uiState.update {
            it.copy(
                taskId = taskId,
                taskName = snapshot?.taskName,
                taskQueryIndex = queryIndex,
                taskQueryCount = snapshot?.composedQueries?.size ?: 1,
                taskMaxUsers = snapshot?.maxUsers,
                taskBlockedKeywordCount = snapshot?.normalizedBlockedKeywords?.size ?: 0,
                taskStartedAtMillis = now,
                taskLastEvent = "TASK_STARTED",
                remoteTaskId = remoteTaskId,
                remoteTaskStatus = remoteTaskId?.let { RemoteTaskStatus.RUNNING },
                remoteTaskStatusUpdatedAtMillis = remoteTaskId?.let { now },
                remoteSyncLastError = null,
                taskRecords = existingRecords,
                recordEntries = allTaskRecords,
                taskHandledUserCount = existingRecords.count { record ->
                    record.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                taskDuplicateUserCount = existingRecords.count { record ->
                    record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                savedTaskAvailable = true,
                savedTaskName = snapshot?.taskName,
                savedTaskQueryIndex = queryIndex,
                savedTaskQueryCount = snapshot?.composedQueries?.size ?: 1,
                taskHistory = taskHistory,
            )
        }
        logger.info(
            "task_started",
            attributes = buildMap {
                put("task_id_hash", taskId.hashCode())
                put("keyword_hash", keyword.hashCode())
                put("query_index", queryIndex)
                snapshot?.let {
                    put("query_count", it.composedQueries.size)
                    put("blocked_keyword_count", it.normalizedBlockedKeywords.size)
                    put("preset_version_hash", it.presetVersion.hashCode())
                }
            },
        )
        snapshot?.let {
            saveTaskCheckpoint(
                TaskCheckpoint(
                    taskId = taskId,
                    snapshot = it,
                    queryIndex = queryIndex,
                    updatedAtMillis = now,
                ),
            )
        }
        remoteTaskId?.let { id ->
            claimRemoteTask(id)
            syncRemoteStatus(RemoteTaskStatus.RUNNING)
        }
    }

    /** Rebuilds the gateway when the encrypted endpoint/token configuration changes. */
    private fun refreshRemoteSync() {
        val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable)
        val fingerprint = config?.let { 31 * it.endpoint.hashCode() + it.licenseToken.hashCode() }
        synchronized(recordLock) {
            if (fingerprint == remoteConfigFingerprint) return
            remoteSyncQueue?.close()
            remoteConfigFingerprint = fingerprint
            remoteGateway = config?.let(::AutomationHttpClient)
            remoteSyncQueue = remoteGateway?.let { gateway ->
                RemoteTaskSyncQueue(
                    scope = storeScope,
                    gatewayProvider = { gateway },
                    logger = logger,
                    onFailure = { message ->
                        _uiState.update { it.copy(remoteSyncLastError = message) }
                    },
                    onSuccess = {
                        _uiState.update { it.copy(remoteSyncLastError = null) }
                    },
                    onStatusSuccess = { task ->
                        _uiState.update {
                            it.copy(
                                remoteTaskStatus = task.status,
                                remoteTaskStatusUpdatedAtMillis = System.currentTimeMillis(),
                                remoteSyncLastError = null,
                            )
                        }
                    },
                )
            }
            remoteSyncQueue?.let { queue ->
                storeScope.launch {
                    queue.pendingCount.collect { count ->
                        _uiState.update { it.copy(remoteSyncPendingCount = count) }
                    }
                }
            }
        }
    }

    private fun claimRemoteTask(taskId: Long) {
        val gateway = synchronized(recordLock) { remoteGateway } ?: return
        storeScope.launch {
            runCatching { gateway.claimTask(taskId) }
                .onSuccess { task ->
                    _uiState.update {
                        it.copy(
                            remoteTaskStatus = task.status,
                            remoteTaskStatusUpdatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    logger.info(
                        "remote_task_claimed",
                        attributes = mapOf("remote_task_id_hash" to task.id.hashCode(), "status" to task.status),
                    )
                }
                .onFailure { error ->
                    logger.warn(
                        "remote_task_claim_failed",
                        message = "Remote task claim failed; local execution remains available",
                        attributes = mapOf("remote_task_id_hash" to taskId.hashCode()),
                    )
                    _uiState.update { it.copy(remoteSyncLastError = "远程任务领取失败，本地任务仍可继续") }
                }
        }
    }

    /** Persist only opaque identity hashes and the frozen task contract. */
    fun saveTaskCheckpoint(checkpoint: TaskCheckpoint) {
        synchronized(recordLock) {
            savedCheckpoint = checkpoint
            recordPreferences?.edit()
                ?.putString(TASK_CHECKPOINT_KEY, encodeCheckpoint(checkpoint).toString())
                ?.apply()
        }
        _uiState.update {
            it.copy(
                savedTaskAvailable = true,
                savedTaskName = checkpoint.snapshot.taskName,
                savedTaskQueryIndex = checkpoint.queryIndex,
                savedTaskQueryCount = checkpoint.snapshot.composedQueries.size,
                taskQueryIndex = checkpoint.queryIndex,
            )
        }
    }

    fun clearTaskCheckpoint() {
        synchronized(recordLock) {
            savedCheckpoint = null
            recordPreferences?.edit()?.remove(TASK_CHECKPOINT_KEY)?.apply()
        }
        _uiState.update {
            it.copy(
                savedTaskAvailable = false,
                savedTaskName = null,
                savedTaskQueryIndex = 0,
                savedTaskQueryCount = 0,
            )
        }
    }

    /** Submit an explicitly captured result viewport without blocking accessibility gestures. */
    fun syncRemoteCheckpoint(request: RemoteCheckpointRequest) {
        val taskId: Long
        val queue: RemoteTaskSyncQueue
        synchronized(recordLock) {
            taskId = remoteTaskId ?: return
            queue = remoteSyncQueue ?: return
        }
        queue.enqueueCheckpoint(taskId, request)
    }

    /** Non-blocking best-effort writeback of the local lifecycle to the remote task. */
    fun syncRemoteStatus(status: Int, errorCode: String? = null, errorMessage: String? = null) {
        val taskId: Long
        val queue: RemoteTaskSyncQueue
        val signature = "$status|${errorCode.orEmpty()}|${errorMessage.orEmpty()}"
        synchronized(recordLock) {
            if (signature == lastRemoteStatusSignature) return
            taskId = remoteTaskId ?: return
            queue = remoteSyncQueue ?: return
            lastRemoteStatusSignature = signature
        }
        queue.enqueueStatus(
            taskId,
            RemoteTaskStatusRequest(
                status = status,
                errorCode = errorCode,
                errorMessage = errorMessage?.take(512),
            ),
        )
    }

    /** Create the durable per-user record when a unique row is first selected. */
    fun recordUserTaskStarted(
        identityHash: Int,
        page: PageKind? = PageKind.USER_RESULTS,
        remoteUserKey: String? = null,
        displayName: String? = null,
        messageContent: String? = null,
    ) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        synchronized(recordLock) {
            remoteUserKey?.takeIf(String::isNotBlank)?.let { remoteUserKeys[identityHash] = it }
            displayName?.takeIf(String::isNotBlank)?.let { remoteDisplayNames[identityHash] = it }
        }
        logger.info(
            "task_user_started",
            attributes = mapOf("task_id_hash" to taskId.hashCode(), "identity_hash" to identityHash),
        )
        appendTaskRecord(
            UserTaskRecord(
                recordId = UUID.randomUUID().toString(),
                taskId = taskId,
                identityHash = identityHash,
                displayName = displayName,
                userKey = remoteUserKey,
                messageContent = messageContent,
                outcome = UserTaskRecord.Outcome.IN_PROGRESS,
                startedAtMillis = System.currentTimeMillis(),
                page = page,
            ),
        )
        enqueueRemoteRecord(identityHash, UserTaskRecord.Outcome.IN_PROGRESS, null, page)
    }

    /** Finish the current user's record without creating a second row for the same attempt. */
    fun recordUserTaskFinished(
        identityHash: Int?,
        outcome: UserTaskRecord.Outcome,
        reason: String? = null,
        page: PageKind? = null,
        remoteUserKey: String? = null,
        displayName: String? = null,
    ) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        if (identityHash != null) {
            synchronized(recordLock) {
                remoteUserKey?.takeIf(String::isNotBlank)?.let { remoteUserKeys[identityHash] = it }
                displayName?.takeIf(String::isNotBlank)?.let { remoteDisplayNames[identityHash] = it }
            }
        }
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
                    displayName = displayName ?: allTaskRecords[index].displayName,
                    userKey = remoteUserKey ?: allTaskRecords[index].userKey,
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
                        displayName = displayName,
                        userKey = remoteUserKey,
                        outcome = outcome,
                        startedAtMillis = now,
                        finishedAtMillis = now,
                        page = page,
                        reason = reason,
                    ),
                )
            }
        }
        enqueueRemoteRecord(identityHash, outcome, reason, page)
    }

    /** Record a row-level event such as a duplicate or follow-back skip. */
    fun recordUserTaskEvent(
        identityHash: Int?,
        outcome: UserTaskRecord.Outcome,
        reason: String? = null,
        page: PageKind? = PageKind.USER_RESULTS,
        remoteUserKey: String? = null,
        displayName: String? = null,
    ) {
        val taskId = synchronized(recordLock) { currentTaskId } ?: return
        if (identityHash != null) {
            synchronized(recordLock) {
                remoteUserKey?.takeIf(String::isNotBlank)?.let { remoteUserKeys[identityHash] = it }
                displayName?.takeIf(String::isNotBlank)?.let { remoteDisplayNames[identityHash] = it }
            }
        }
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
                        displayName = displayName,
                        userKey = remoteUserKey,
                        outcome = outcome,
                startedAtMillis = now,
                finishedAtMillis = now,
                page = page,
                reason = reason,
            ),
        )
        enqueueRemoteRecord(identityHash, outcome, reason, page)
    }

    private fun enqueueRemoteRecord(
        identityHash: Int?,
        outcome: UserTaskRecord.Outcome,
        reason: String?,
        page: PageKind?,
    ) {
        if (identityHash == null) return
        val remoteId: Long
        val queue: RemoteTaskSyncQueue
        val userKey: String
        val displayName: String?
        synchronized(recordLock) {
            remoteId = remoteTaskId ?: return
            queue = remoteSyncQueue ?: return
            userKey = remoteUserKeys[identityHash] ?: return
            displayName = remoteDisplayNames[identityHash]
        }
        queue.enqueueRecord(
            remoteId,
            RemoteRecordRequest(
                userKey = userKey,
                displayName = displayName,
                status = RemoteTaskRecordStatus.from(outcome),
                lastAction = page?.name ?: outcome.name,
                failureCode = reason?.takeIf { outcome != UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED }?.let { outcome.name },
                failureMessage = reason?.takeIf { outcome != UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED }?.take(512),
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
        val currentHistory = taskHistory.firstOrNull { it.taskId == taskId }
        if (currentHistory != null) {
            taskHistory = taskHistory.map { entry ->
                if (entry.taskId != taskId) return@map entry
                entry.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    handledCount = records.count { record ->
                        record.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                    },
                    skippedCount = records.count { record ->
                        record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                    },
                    filteredCount = records.count { record ->
                        record.outcome == UserTaskRecord.Outcome.FILTERED_BY_KEYWORD
                    },
                    duplicateCount = records.count { record ->
                        record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                    },
                )
            }
            persistTaskHistoryLocked()
        }
        _uiState.update {
            it.copy(
                taskRecords = records,
                recordEntries = allTaskRecords,
                taskHandledUserCount = records.count { record ->
                    record.outcome != UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                taskDuplicateUserCount = records.count { record ->
                    record.outcome == UserTaskRecord.Outcome.DUPLICATE_SKIPPED
                },
                taskLastEvent = lastEvent,
                taskHistory = taskHistory,
            )
        }
    }

    private fun persistRecordsLocked() {
        recordPreferences?.edit()?.putString(
            TASK_RECORDS_KEY,
            JSONArray(allTaskRecords.map { it.toJson() }).toString(),
        )?.apply()
    }

    private fun persistTaskHistoryLocked() {
        recordPreferences?.edit()?.putString(
            TASK_HISTORY_KEY,
            JSONArray(taskHistory.map { it.toJson() }).toString(),
        )?.apply()
    }

    private fun encodeCheckpoint(checkpoint: TaskCheckpoint): JSONObject = JSONObject().apply {
        put("task_id", checkpoint.taskId)
        put("query_index", checkpoint.queryIndex)
        put("updated_at", checkpoint.updatedAtMillis)
        put("processed_identity_hashes", JSONArray(checkpoint.processedIdentityHashes))
        put("snapshot", checkpoint.snapshot.toJson())
    }

    private fun encodeTaskDraft(draft: TaskDraft): JSONObject = JSONObject().apply {
        put("id", draft.id)
        put("name", draft.name)
        put("preset_ids", JSONArray(draft.presetIds))
        put("custom_keywords", JSONArray(draft.customKeywords))
        put("region", draft.region ?: JSONObject.NULL)
        put("blocked_keywords", JSONArray(draft.blockedKeywords))
        put("max_users", draft.maxUsers)
        put("message_template", draft.messageTemplate ?: JSONObject.NULL)
        put("execution_mode", draft.executionMode.name)
    }

    private fun decodeTaskDraft(raw: String?): TaskDraft? = runCatching {
        if (raw.isNullOrBlank()) return null
        val root = JSONObject(raw)
        TaskDraft(
            id = root.optString("id", "draft"),
            name = root.optString("name", "红木客户筛选"),
            presetIds = root.optJSONArray("preset_ids")?.toStringList().orEmpty(),
            customKeywords = root.optJSONArray("custom_keywords")?.toStringList().orEmpty(),
            region = root.optStringOrNull("region"),
            blockedKeywords = root.optJSONArray("blocked_keywords")?.toStringList().orEmpty(),
            maxUsers = root.optInt("max_users", TaskDraft.DEFAULT_MAX_USERS),
            messageTemplate = root.optStringOrNull("message_template"),
            executionMode = runCatching {
                TaskExecutionMode.valueOf(root.optString("execution_mode"))
            }.getOrDefault(TaskExecutionMode.SAFE_BLANK_PROBE),
        )
    }.getOrNull()

    private fun TaskSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("task_id", taskId)
        put("task_name", taskName)
        put("preset_version", presetVersion)
        put("base_keywords", JSONArray(baseKeywords))
        put("region", region)
        put("composed_queries", JSONArray(composedQueries))
        put("blocked_keywords", JSONArray(normalizedBlockedKeywords))
        put("max_users", maxUsers)
        put("message_template", messageTemplate ?: JSONObject.NULL)
        put("execution_mode", executionMode.name)
        put("created_at", createdAtMillis)
    }

    private fun decodeCheckpoint(raw: String?): TaskCheckpoint? = runCatching {
        if (raw.isNullOrBlank()) return null
        val root = JSONObject(raw)
        val snapshotJson = root.getJSONObject("snapshot")
        val snapshot = TaskSnapshot(
            taskId = snapshotJson.getString("task_id"),
            taskName = snapshotJson.getString("task_name"),
            presetVersion = snapshotJson.getString("preset_version"),
            baseKeywords = snapshotJson.getStringList("base_keywords"),
            region = snapshotJson.getString("region"),
            composedQueries = snapshotJson.getStringList("composed_queries").ifEmpty { return null },
            normalizedBlockedKeywords = snapshotJson.getStringList("blocked_keywords"),
            maxUsers = snapshotJson.getInt("max_users"),
            messageTemplate = if (snapshotJson.isNull("message_template")) null else snapshotJson.getString("message_template"),
            executionMode = TaskExecutionMode.valueOf(snapshotJson.getString("execution_mode")),
            createdAtMillis = snapshotJson.getLong("created_at"),
        )
        val queryIndex = root.getInt("query_index")
        if (queryIndex !in snapshot.composedQueries.indices) return null
        TaskCheckpoint(
            taskId = root.getString("task_id"),
            snapshot = snapshot,
            queryIndex = queryIndex,
            processedIdentityHashes = root.optJSONArray("processed_identity_hashes")?.let { hashes ->
                buildList(hashes.length()) { for (index in 0 until hashes.length()) add(hashes.getInt(index)) }
            }.orEmpty(),
            updatedAtMillis = root.getLong("updated_at"),
        )
    }.getOrNull()

    private fun JSONObject.getStringList(key: String): List<String> =
        optJSONArray(key)?.let { values ->
            buildList(values.length()) { for (index in 0 until values.length()) add(values.getString(index)) }
        }.orEmpty()

    private fun JSONArray.toStringList(): List<String> = buildList(length()) {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }

    private fun TaskHistoryEntry.toJson(): JSONObject = JSONObject().apply {
        put("task_id", taskId)
        put("task_name", taskName)
        put("query_count", queryCount)
        put("max_users", maxUsers)
        put("started_at", startedAtMillis)
        put("updated_at", updatedAtMillis)
        put("status", status.name)
        put("handled_count", handledCount)
        put("skipped_count", skippedCount)
        put("filtered_count", filteredCount)
        put("duplicate_count", duplicateCount)
        put("search_queries", JSONArray(searchQueries))
        put("region", region ?: JSONObject.NULL)
        put("blocked_keywords", JSONArray(blockedKeywords))
        put("message_template", messageTemplate ?: JSONObject.NULL)
        put("execution_mode", executionMode.name)
        put("error_message", errorMessage ?: JSONObject.NULL)
    }

    private fun decodeTaskHistory(raw: String?): List<TaskHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList(minOf(json.length(), MAX_TASK_HISTORY)) {
                val start = (json.length() - MAX_TASK_HISTORY).coerceAtLeast(0)
                for (index in start until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        TaskHistoryEntry(
                            taskId = item.getString("task_id"),
                            taskName = item.getString("task_name"),
                            queryCount = item.getInt("query_count"),
                            maxUsers = item.getInt("max_users"),
                            startedAtMillis = item.getLong("started_at"),
                            updatedAtMillis = item.getLong("updated_at"),
                            status = TaskRunStatus.valueOf(item.getString("status")),
                            handledCount = item.getInt("handled_count"),
                            skippedCount = item.getInt("skipped_count"),
                            filteredCount = item.getInt("filtered_count"),
                            duplicateCount = item.getInt("duplicate_count"),
                            searchQueries = item.optJSONArray("search_queries")?.let { values ->
                                buildList(values.length()) { for (index in 0 until values.length()) add(values.getString(index)) }
                            }.orEmpty(),
                            region = item.optStringOrNull("region"),
                            blockedKeywords = item.optJSONArray("blocked_keywords")?.let { values ->
                                buildList(values.length()) { for (index in 0 until values.length()) add(values.getString(index)) }
                            }.orEmpty(),
                            messageTemplate = item.optStringOrNull("message_template"),
                            executionMode = runCatching {
                                TaskExecutionMode.valueOf(item.optString("execution_mode"))
                            }.getOrDefault(TaskExecutionMode.SAFE_BLANK_PROBE),
                            errorMessage = item.optStringOrNull("error_message"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun UserTaskRecord.toJson(): JSONObject = JSONObject().apply {
        put("record_id", recordId)
        put("task_id", taskId)
        put("identity_hash", identityHash)
        put("display_name", displayName ?: JSONObject.NULL)
        put("user_key", userKey ?: JSONObject.NULL)
        put("message_content", messageContent ?: JSONObject.NULL)
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
                    runCatching {
                        val item = json.getJSONObject(index)
                        UserTaskRecord(
                            recordId = item.getString("record_id"),
                            taskId = item.getString("task_id"),
                            identityHash = if (item.isNull("identity_hash")) null else item.getInt("identity_hash"),
                            displayName = item.optStringOrNull("display_name"),
                            userKey = item.optStringOrNull("user_key"),
                            messageContent = item.optStringOrNull("message_content"),
                            outcome = UserTaskRecord.Outcome.valueOf(item.getString("outcome")),
                            startedAtMillis = item.getLong("started_at"),
                            finishedAtMillis = if (item.isNull("finished_at")) null else item.getLong("finished_at"),
                            page = if (item.isNull("page")) null else PageKind.valueOf(item.getString("page")),
                            reason = if (item.isNull("reason")) null else item.getString("reason"),
                        )
                    }.onSuccess(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

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
        val historySnapshot = synchronized(recordLock) {
            val taskId = currentTaskId
            val status = phase.toTaskRunStatus()
            if (taskId != null && status != null) {
                taskHistory = taskHistory.map { entry ->
                    if (entry.taskId == taskId) {
                        entry.copy(
                            status = status,
                            updatedAtMillis = System.currentTimeMillis(),
                            errorMessage = error ?: entry.errorMessage,
                        )
                    } else {
                        entry
                    }
                }
                persistTaskHistoryLocked()
            }
            taskHistory
        }
        _uiState.update {
            it.copy(
                phase = phase,
                lastError = error ?: it.lastError.takeUnless { phase != AutomationPhase.FAILED },
                awaitingManualHandoff = awaitingManualHandoff,
                taskHistory = historySnapshot,
            )
        }
        phase.toRemoteTaskStatus()?.let { status ->
            syncRemoteStatus(status, errorCode = if (phase == AutomationPhase.FAILED) "LOCAL_AUTOMATION_FAILED" else null, errorMessage = error)
        }
        if (phase in TERMINAL_PHASES) openRecordsTab()
    }

    private fun AutomationPhase.toRemoteTaskStatus(): Int? = when (this) {
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> RemoteTaskStatus.PAUSED
        AutomationPhase.STOPPED -> RemoteTaskStatus.CANCELLED
        AutomationPhase.FAILED -> RemoteTaskStatus.FAILED
        // COMPLETED_EMPTY_MESSAGE_PROBE and COMPLETED_MESSAGE_SENT are per-user/action
        // milestones.  The controller advances to the next result immediately afterwards;
        // publishing COMPLETED here closes the whole remote task after the first successful
        // user and makes the backend reject subsequent records/checkpoints with 409.
        AutomationPhase.COMPLETED_TASK -> RemoteTaskStatus.COMPLETED
        else -> null
    }

    private fun AutomationPhase.toTaskRunStatus(): TaskRunStatus? = when (this) {
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> TaskRunStatus.PAUSED
        AutomationPhase.STOPPED -> TaskRunStatus.STOPPED
        AutomationPhase.FAILED -> TaskRunStatus.FAILED
        AutomationPhase.COMPLETED_TASK,
        -> TaskRunStatus.COMPLETED
        // These are per-user milestones. The controller immediately returns to the list and
        // continues the same task, so history must remain RUNNING until COMPLETED_TASK.
        AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE,
        AutomationPhase.COMPLETED_MESSAGE_SENT,
        -> TaskRunStatus.RUNNING
        AutomationPhase.IDLE,
        AutomationPhase.SERVICE_READY,
        -> null
        else -> TaskRunStatus.RUNNING
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
        publishPhase(
            phase = AutomationPhase.FAILED,
            error = reason,
            awaitingManualHandoff = false,
        )
    }

    fun publishManualHandoff(reason: String) {
        publishPhase(
            phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
            error = reason,
            awaitingManualHandoff = true,
        )
    }

    /** Surface remote-claim/resume failures without changing the local automation phase. */
    fun publishRemoteSyncError(reason: String) {
        _uiState.update { it.copy(remoteSyncLastError = reason) }
    }

    private fun AutomationCommand.name(): String = when (this) {
        is AutomationCommand.Start -> "start"
        is AutomationCommand.SendMessage -> "send_message"
        AutomationCommand.ResumeSavedTask -> "resume_saved_task"
        AutomationCommand.Pause -> "pause"
        AutomationCommand.Resume -> "resume"
        AutomationCommand.Stop -> "stop"
        AutomationCommand.CaptureDiagnostics -> "capture_diagnostics"
        AutomationCommand.DumpNodeTree -> "dump_node_tree"
    }

    private const val MAX_OCR_PREVIEW = 1_000
    private const val TASK_RECORDS_PREFERENCES = "automation_task_records"
    private const val TASK_RECORDS_KEY = "records"
    private const val TASK_HISTORY_KEY = "history"
    private const val TASK_CHECKPOINT_KEY = "checkpoint"
    private const val TASK_DRAFT_KEY = "draft"
    private const val MAX_TASK_RECORDS = 2_000
    private const val MAX_TASK_HISTORY = 100
    private const val RECORDS_OPEN_THROTTLE_MS = 1_500L
    private val TERMINAL_PHASES = setOf(
        AutomationPhase.COMPLETED_TASK,
        AutomationPhase.FAILED,
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
        AutomationPhase.STOPPED,
    )
}
