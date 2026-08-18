package com.example.douyinautomation.automation

/**
 * A private, app-local audit record for one search-result row in a task.
 *
 * The raw account name is intentionally not persisted. [identityHash] is derived from the
 * short-lived identity key used by the controller; it is enough to de-duplicate one run while
 * avoiding account text in diagnostics and preferences. The record model is deliberately small so
 * it can later be moved to Room when task publishing is introduced.
 */
data class UserTaskRecord(
    val recordId: String,
    val taskId: String,
    val identityHash: Int?,
    val outcome: Outcome,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val page: PageKind? = null,
    val reason: String? = null,
) {
    enum class Outcome {
        IN_PROGRESS,
        BLANK_PROBE_VERIFIED,
        PRIVATE_MESSAGE_UNAVAILABLE,
        MESSAGE_SEND_FAILED,
        FOLLOW_BACK_SKIPPED,
        DUPLICATE_SKIPPED,
        IDENTITY_UNAVAILABLE,
        PAUSED,
        STOPPED,
    }
}
