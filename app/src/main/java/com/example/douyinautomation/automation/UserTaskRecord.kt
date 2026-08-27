package com.example.douyinautomation.automation

/**
 * A private, app-local audit record for one search-result row in a task.
 *
 * The record is deliberately app-private. It keeps the operator-visible account label and action
 * content so a completed task can be audited from the Records tab; diagnostic logs still use
 * opaque fingerprints and never emit these fields.
 */
data class UserTaskRecord(
    val recordId: String,
    val taskId: String,
    /** Legacy 32-bit field retained so existing app-private records remain readable. */
    val identityHash: Int?,
    /** SHA-256 reference used to associate new in-progress and finished records safely. */
    val identityFingerprint: String? = null,
    val displayName: String? = null,
    val userKey: String? = null,
    val messageContent: String? = null,
    val outcome: Outcome,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val page: PageKind? = null,
    val reason: String? = null,
) {
    enum class Outcome {
        IN_PROGRESS,
        BLANK_PROBE_VERIFIED,
        /** Debug skip: commenter profile was confirmed, then the runtime returned to comments. */
        PROFILE_OPENED,
        /** Real send: the requested message was verified in the conversation. */
        MESSAGE_SENT,
        PRIVATE_MESSAGE_UNAVAILABLE,
        MESSAGE_SEND_FAILED,
        FOLLOW_BACK_SKIPPED,
        FILTERED_BY_KEYWORD,
        DUPLICATE_SKIPPED,
        IDENTITY_UNAVAILABLE,
        PAUSED,
        STOPPED,
        ;

        /** Overlay「已私信」and remote SUCCESS share this set. */
        fun countsAsMessaged(): Boolean = this == BLANK_PROBE_VERIFIED ||
            this == PROFILE_OPENED ||
            this == MESSAGE_SENT

        /** B-end「用户数」only counts a verified private-message probe or a real send. */
        fun countsTowardUserQuota(): Boolean = this == BLANK_PROBE_VERIFIED ||
            this == MESSAGE_SENT
    }
}
