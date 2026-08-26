package com.example.douyinautomation.automation

/**
 * Builds the mobile create-task payload from a local draft or frozen snapshot.
 * Real DM text, OCR, coordinates, and raw comments are never included.
 */
object MobileTaskPublishMapper {
    fun isStableLocalTaskId(id: String): Boolean {
        val trimmed = id.trim()
        if (trimmed.isEmpty() || trimmed == "draft" || trimmed == "preview") return false
        if (trimmed.toLongOrNull() != null) return false
        return trimmed.length >= 8
    }

    fun fromDraft(draft: TaskDraft, deviceIdHash: String?): MobileTaskCreateRequest {
        val keyword = draft.customKeywords.firstOrNull { it.isNotBlank() }
            ?: draft.commentConfig?.targetUser
            ?: draft.name.trim().ifBlank { "当前账号" }
        return MobileTaskCreateRequest(
            localTaskId = draft.id,
            name = clip(draft.name.trim().ifBlank { keyword }, 128),
            taskType = draft.taskType.name,
            keyword = clip(keyword, 128),
            regionName = draft.region?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            regionPrefix = draft.region?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            blockedKeywords = draft.blockedKeywords.map { it.trim() }.filter { it.isNotEmpty() },
            sendMode = sendMode(draft.executionMode),
            catalogVersion = 0,
            maxUsers = draft.maxUsers,
            deviceIdHash = deviceIdHash,
            commentConfig = draft.commentConfig
                ?.takeIf { draft.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
                ?.toPayload(),
        )
    }

    fun fromSnapshot(snapshot: TaskSnapshot, deviceIdHash: String?): MobileTaskCreateRequest {
        val keyword = snapshot.composedQueries.firstOrNull { it.isNotBlank() }
            ?: snapshot.commentConfig?.targetUser
            ?: snapshot.taskName.trim().ifBlank { "当前账号" }
        return MobileTaskCreateRequest(
            localTaskId = snapshot.taskId,
            name = clip(snapshot.taskName.trim().ifBlank { keyword }, 128),
            taskType = snapshot.taskType.name,
            keyword = clip(keyword, 128),
            regionName = snapshot.region.trim().takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            regionPrefix = snapshot.region.trim().takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            blockedKeywords = snapshot.normalizedBlockedKeywords,
            sendMode = sendMode(snapshot.executionMode),
            catalogVersion = snapshot.presetVersion.toIntOrNull() ?: 0,
            maxUsers = snapshot.maxUsers,
            deviceIdHash = deviceIdHash,
            commentConfig = snapshot.commentConfig
                ?.takeIf { snapshot.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
                ?.toPayload(),
        )
    }

    fun fromHistory(entry: TaskHistoryEntry, deviceIdHash: String?): MobileTaskCreateRequest {
        val keyword = entry.searchQueries.firstOrNull { it.isNotBlank() }
            ?: entry.commentConfig?.targetUser
            ?: entry.taskName.trim().ifBlank { "当前账号" }
        return MobileTaskCreateRequest(
            localTaskId = entry.taskId,
            name = clip(entry.taskName.trim().ifBlank { keyword }, 128),
            taskType = entry.taskType.name,
            keyword = clip(keyword, 128),
            regionName = entry.region?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            regionPrefix = entry.region?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(it, 64) },
            blockedKeywords = entry.blockedKeywords.map { it.trim() }.filter { it.isNotEmpty() },
            sendMode = sendMode(entry.executionMode),
            catalogVersion = 0,
            maxUsers = entry.maxUsers,
            deviceIdHash = deviceIdHash,
            commentConfig = entry.commentConfig
                ?.takeIf { entry.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
                ?.toPayload(),
        )
    }

    fun remoteStatus(status: TaskRunStatus): Int = when (status) {
        TaskRunStatus.RUNNING -> RemoteTaskStatus.RUNNING
        TaskRunStatus.PAUSED -> RemoteTaskStatus.PAUSED
        TaskRunStatus.STOPPED -> RemoteTaskStatus.CANCELLED
        TaskRunStatus.COMPLETED -> RemoteTaskStatus.COMPLETED
        TaskRunStatus.FAILED -> RemoteTaskStatus.FAILED
    }

    private fun sendMode(mode: TaskExecutionMode): String =
        if (mode == TaskExecutionMode.SAFE_BLANK_PROBE) "simulate_empty" else "send"

    private fun clip(value: String, max: Int): String = value.take(max)

    private fun CommentPrivateMessageConfig.toPayload(): MobileCommentConfigPayload =
        MobileCommentConfigPayload(
            entryMode = entryMode.name,
            targetUser = targetUser,
            matchKeywords = matchKeywords,
            maxVideos = maxVideos,
            maxUsersPerVideo = maxUsersPerVideo,
            skipPinnedVideos = skipPinnedVideos,
        )

    private fun CommentPrivateMessageSnapshot.toPayload(): MobileCommentConfigPayload =
        MobileCommentConfigPayload(
            entryMode = entryMode.name,
            targetUser = targetUser,
            matchKeywords = matchKeywords,
            maxVideos = maxVideos,
            maxUsersPerVideo = maxUsersPerVideo,
            skipPinnedVideos = skipPinnedVideos,
        )
}
