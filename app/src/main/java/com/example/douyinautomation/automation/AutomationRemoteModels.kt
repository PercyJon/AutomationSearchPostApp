package com.example.douyinautomation.automation

/**
 * Wire-independent models for the mobile automation API.  These models deliberately keep the
 * server contract separate from the local task snapshot so a catalog update cannot mutate a
 * running task.
 */
data class RegionRule(
    val id: String,
    val name: String,
    val prefix: String,
    val enabled: Boolean = true,
    val sort: Int = 0,
)

data class RegionCatalog(
    val version: String,
    val items: List<RegionRule>,
    val updatedAtMillis: Long?,
)

data class BlockKeywordRule(
    val id: String,
    val keyword: String,
    val matchMode: String = "contains",
    val enabled: Boolean = true,
    val sort: Int = 0,
)

data class BlockKeywordCatalog(
    val version: String,
    val items: List<BlockKeywordRule>,
    val updatedAtMillis: Long?,
)

data class RemoteTask(
    val id: Long,
    val code: String,
    val name: String,
    val keyword: String,
    val regionName: String?,
    val regionPrefix: String?,
    val message: String,
    val sendMode: String,
    val catalogVersion: Int,
    val maxUsers: Int,
    val status: Int,
    val licenseId: Long?,
    val totalCount: Int,
    val processedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val lastUserKey: String?,
    val lastUserName: String?,
    val lastPageNumber: Int?,
    val lastPageFingerprint: String?,
    val checkpointVersion: Int,
    /** Optional task-level snapshot supplied by newer backend versions. */
    val blockedKeywords: List<String> = emptyList(),
)

data class RemoteTaskProgress(
    val taskId: Long,
    val status: Int,
    val totalCount: Int,
    val processedCount: Int,
    val pendingCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val completionPercent: Double,
    val lastUserKey: String?,
    val lastUserName: String?,
    val lastPageNumber: Int?,
    val lastPageFingerprint: String?,
    val checkpointVersion: Int,
)

/**
 * In-memory continuation contract for a claimed remote task.  The raw anchor is deliberately
 * not persisted in the local checkpoint; it is fetched again from the backend when an operator
 * resumes a remote task.
 */
data class RemoteTaskResume(
    val taskId: Long,
    val progress: RemoteTaskProgress,
)

object RemoteTaskResumePolicy {
    /** A processed remote task is safe to resume only when its last user anchor is available. */
    fun canResumeExactly(progress: RemoteTaskProgress): Boolean =
        progress.processedCount <= 0 || !progress.lastUserKey.isNullOrBlank()

    fun requiresAnchor(progress: RemoteTaskProgress): Boolean =
        progress.processedCount > 0 && !progress.lastUserKey.isNullOrBlank()
}

data class RemoteCheckpointRequest(
    val pageNumber: Int,
    val pageFingerprint: String,
    val lastUserKey: String? = null,
    val lastUserName: String? = null,
    val visibleUserKeys: List<String> = emptyList(),
)

data class RemoteCheckpointResponse(
    val taskId: Long,
    val pageNumber: Int,
    val pageFingerprint: String,
    val checkpointVersion: Int,
    val lastUserKey: String?,
    val newUserKeys: List<String>,
    val duplicateUserKeys: List<String>,
    val terminalUserKeys: List<String>,
    val progress: RemoteTaskProgress,
)

data class RemoteRecordRequest(
    val userKey: String,
    val displayName: String? = null,
    val douyinId: String? = null,
    /** 0 pending, 1 processing, 2 success, 3 failed, 4 skipped, 5 blocked. */
    val status: Int,
    val lastAction: String? = null,
    val pageNumber: Int? = null,
    val rowIndex: Int? = null,
    val pageFingerprint: String? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

data class RemoteTaskRecord(
    val taskId: Long,
    val userKey: String,
    val displayName: String?,
    val douyinId: String?,
    val status: Int,
    val attemptCount: Int,
    val lastAction: String?,
    val lastPageNumber: Int?,
    val lastRowIndex: Int?,
    val lastPageFingerprint: String?,
    val failureCode: String?,
    val failureMessage: String?,
)

data class RemoteRecordSyncResponse(
    val record: RemoteTaskRecord,
    val deduplicated: Boolean,
    val progress: RemoteTaskProgress,
)

data class RemoteTaskStatusRequest(
    val status: Int,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

object RemoteTaskStatus {
    const val READY = 1
    const val RUNNING = 2
    const val PAUSED = 3
    const val COMPLETED = 4
    const val FAILED = 5
    const val CANCELLED = 6
}

interface AutomationTaskGateway {
    suspend fun listTasks(): List<RemoteTask>
    suspend fun claimTask(taskId: Long): RemoteTask
    suspend fun getTaskProgress(taskId: Long): RemoteTaskProgress
    suspend fun submitCheckpoint(taskId: Long, request: RemoteCheckpointRequest): RemoteCheckpointResponse
    suspend fun submitRecord(taskId: Long, request: RemoteRecordRequest): RemoteRecordSyncResponse
    suspend fun updateTaskStatus(taskId: Long, request: RemoteTaskStatusRequest): RemoteTask
}
