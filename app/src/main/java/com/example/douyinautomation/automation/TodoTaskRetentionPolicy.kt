package com.example.douyinautomation.automation

/**
 * Todo holds unstarted local drafts only. A run that has begun must leave the list immediately
 * and must not be written back as a reusable todo item.
 */
object TodoTaskRetentionPolicy {
    fun idsToDiscardOnStart(originalTaskId: String?, boundTaskId: String): Set<String> = buildSet {
        originalTaskId?.takeIf(MobileTaskPublishMapper::isStableLocalTaskId)?.let(::add)
        boundTaskId.takeIf(MobileTaskPublishMapper::isStableLocalTaskId)?.let(::add)
    }

    fun shouldRemainVisible(draft: TaskDraft, startedTaskIds: Set<String>): Boolean =
        !draft.deleted && draft.id !in startedTaskIds
}
