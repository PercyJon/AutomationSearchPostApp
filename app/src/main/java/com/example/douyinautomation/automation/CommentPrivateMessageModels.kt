package com.example.douyinautomation.automation

/**
 * The kind of work a task performs.  The existing profile-to-private-message flow remains the
 * default; comment private messaging is additive and is not selected unless explicitly chosen.
 */
enum class AutomationTaskType {
    PROFILE_PRIVATE_MESSAGE,
    COMMENT_PRIVATE_MESSAGE,
}

/** How a comment-private-message task locates the profile on which it should start. */
enum class CommentPrivateMessageEntryMode {
    /** Search for a configured account, open its profile, then begin at the first video. */
    SEARCH_TARGET_PROFILE,

    /** Use the profile currently visible in Douyin after the operator presses Start in the overlay. */
    CURRENT_PROFILE,
}

/** Stages shared by the future comment runner and the floating progress overlay. */
enum class CommentPrivateMessageStage {
    RESOLVING_ENTRY,
    OPENING_FIRST_VIDEO,
    OPENING_COMMENTS,
    READING_COMMENTS,
    MATCHING_COMMENT,
    OPENING_COMMENT_USER,
    OPENING_PRIVATE_MESSAGE,
    PROBING_MESSAGE,
    VERIFYING_RESULT,
    RETURNING_TO_VIDEO,
    COMPLETED,
    FAILED,
    PAUSED_FOR_MANUAL_HANDOFF,
}

/**
 * Persisted configuration for a comment-private-message task.  It contains only operator
 * intent; screen observations, OCR, and raw comments stay in memory/diagnostic storage.
 *
 * [matchKeywords] uses OR semantics. An empty list deliberately means "match every comment".
 */
data class CommentPrivateMessageConfig(
    val entryMode: CommentPrivateMessageEntryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE,
    val targetUser: String? = null,
    val matchKeywords: List<String> = emptyList(),
    val maxVideos: Int = DEFAULT_MAX_VIDEOS,
    val maxUsersPerVideo: Int = DEFAULT_MAX_USERS_PER_VIDEO,
) {
    fun normalizedKeywords(): List<String> = CommentKeywordMatcher.normalizeKeywords(matchKeywords)

    fun validationErrors(): List<String> = buildList {
        if (entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE && targetUser.isNullOrBlank()) {
            add("搜索用户入口需要填写目标用户")
        }
        if (maxVideos !in 1..MAX_VIDEOS) {
            add("视频数量上限必须在 1-$MAX_VIDEOS 之间")
        }
        if (maxUsersPerVideo !in 1..MAX_USERS_PER_VIDEO) {
            add("每个视频的用户数量上限必须在 1-$MAX_USERS_PER_VIDEO 之间")
        }
    }

    fun normalized(): CommentPrivateMessageConfig = copy(
        targetUser = targetUser?.trim()?.takeIf(String::isNotEmpty),
        matchKeywords = normalizedKeywords(),
    )

    companion object {
        const val DEFAULT_MAX_VIDEOS = 20
        const val DEFAULT_MAX_USERS_PER_VIDEO = 50
        const val MAX_VIDEOS = 500
        const val MAX_USERS_PER_VIDEO = 500
    }
}

/** Immutable configuration captured when a comment task starts. */
data class CommentPrivateMessageSnapshot(
    val entryMode: CommentPrivateMessageEntryMode,
    val targetUser: String?,
    val matchKeywords: List<String>,
    val maxVideos: Int,
    val maxUsersPerVideo: Int,
) {
    fun matchesComment(comment: String): Boolean =
        CommentKeywordMatcher.matches(comment, matchKeywords)
}

/** Converts pipe-separated UI input into the canonical list stored in the task snapshot. */
object CommentKeywordMatcher {
    fun parsePipeSeparated(raw: String): List<String> =
        normalizeKeywords(raw.split('|'))

    fun normalizeKeywords(values: Iterable<String>): List<String> = values
        .flatMap { it.split('|') }
        .map(::normalize)
        .filter(String::isNotEmpty)
        .distinct()

    /** Empty keywords intentionally match all comments. Non-empty keywords use OR/contains. */
    fun matches(comment: String, keywords: Iterable<String>): Boolean {
        val normalizedComment = normalize(comment)
        val normalizedKeywords = normalizeKeywords(keywords)
        return normalizedKeywords.isEmpty() || normalizedKeywords.any(normalizedComment::contains)
    }

    private fun normalize(value: String): String = IdentityTextCanonicalizer.normalize(value)
}

/** Counters shown by the floating overlay; no raw comment text is retained here. */
data class CommentPrivateMessageProgress(
    val stage: CommentPrivateMessageStage = CommentPrivateMessageStage.RESOLVING_ENTRY,
    val videoIndex: Int = 0,
    val videoTotal: Int = 0,
    val commentUsersProcessed: Int = 0,
    val commentUsersMatched: Int = 0,
    val privateMessagesSucceeded: Int = 0,
    val privateMessagesFailed: Int = 0,
    val privateMessagesSkipped: Int = 0,
    val lastError: String? = null,
) {
    val totalPrivateMessages: Int
        get() = privateMessagesSucceeded + privateMessagesFailed + privateMessagesSkipped

    val completionPercent: Int
        get() = when {
            videoTotal <= 0 -> 0
            else -> ((videoIndex.coerceIn(0, videoTotal) * 100) / videoTotal)
        }
}

/** Generic, UI-safe snapshot consumed by a future WindowManager overlay. */
data class FloatingTaskProgress(
    val taskId: String? = null,
    val taskName: String? = null,
    val taskType: AutomationTaskType = AutomationTaskType.PROFILE_PRIVATE_MESSAGE,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val stageLabel: String? = null,
) {
    val completionPercent: Int
        get() = when {
            total <= 0 -> 0
            else -> ((processed.coerceIn(0, total) * 100) / total)
        }
}
