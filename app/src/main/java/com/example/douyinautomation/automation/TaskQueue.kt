package com.example.douyinautomation.automation

/**
 * Small FIFO queue shared by task runners that need to execute saved work one item at a time.
 * The queue deliberately owns no Android state, making the sequencing behavior easy to test and
 * reusable for future automation modules.
 */
class SequentialTaskQueue<T> {
    private val pending = ArrayDeque<T>()

    val isEmpty: Boolean
        get() = pending.isEmpty()

    val size: Int
        get() = pending.size

    fun replace(items: Iterable<T>) {
        pending.clear()
        pending.addAll(items)
    }

    fun addAll(items: Iterable<T>) {
        pending.addAll(items)
    }

    fun poll(): T? = pending.removeFirstOrNull()

    fun peek(): T? = pending.firstOrNull()

    fun clear() {
        pending.clear()
    }

    fun asList(): List<T> = pending.toList()
}

/** The two local automation families that can be executed as a single serial queue. */
enum class LocalTaskQueueType {
    B_END_PRIVATE_MESSAGE,
    COMMENT_SEARCH_PROFILE,
}

/** Durable lifecycle states for a local serial queue. */
enum class LocalTaskQueueStatus {
    RUNNING,
    PAUSED,
    STOPPED,
    COMPLETED,
}

/**
 * Frozen local queue contract. Per-task user results and checkpoints stay in their existing
 * stores; this model owns only selection type, ordering, and the currently active item.
 */
data class LocalTaskQueueSession(
    val queueId: String,
    val queueType: LocalTaskQueueType,
    val tasks: List<TaskSnapshot>,
    val activeTaskIndex: Int = 0,
    val status: LocalTaskQueueStatus = LocalTaskQueueStatus.RUNNING,
    /** The last verified controller phase when an operator paused the queue. */
    val pausedPhase: AutomationPhase? = null,
    val updatedAtMillis: Long,
) {
    init {
        require(tasks.isNotEmpty()) { "A local task queue must contain at least one task" }
        require(tasks.size <= AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE) {
            "A local task queue cannot contain more than ${AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE} tasks"
        }
        require(activeTaskIndex in tasks.indices) { "Active queue task index is outside the queue" }
        require(tasks.all { LocalTaskQueuePolicy.typeOf(it) == queueType }) {
            "Every local queue task must match its queue type"
        }
    }

    val activeTask: TaskSnapshot get() = tasks[activeTaskIndex]
    val hasNextTask: Boolean get() = activeTaskIndex + 1 < tasks.size

    fun pause(
        nowMillis: Long,
        phase: AutomationPhase?,
    ): LocalTaskQueueSession = copy(
        status = LocalTaskQueueStatus.PAUSED,
        pausedPhase = phase,
        updatedAtMillis = nowMillis,
    )

    fun resume(nowMillis: Long): LocalTaskQueueSession = copy(
        status = LocalTaskQueueStatus.RUNNING,
        pausedPhase = null,
        updatedAtMillis = nowMillis,
    )

    fun stop(nowMillis: Long): LocalTaskQueueSession = copy(
        status = LocalTaskQueueStatus.STOPPED,
        updatedAtMillis = nowMillis,
    )

    fun advance(nowMillis: Long): LocalTaskQueueSession = if (hasNextTask) {
        copy(activeTaskIndex = activeTaskIndex + 1, updatedAtMillis = nowMillis)
    } else {
        copy(status = LocalTaskQueueStatus.COMPLETED, updatedAtMillis = nowMillis)
    }
}

/**
 * Keeps queue selection independent of the UI. A malformed or mixed command is rejected before
 * the navigation controller can begin a target-app action.
 */
object LocalTaskQueuePolicy {
    fun typeOf(snapshot: TaskSnapshot): LocalTaskQueueType? = when (snapshot.taskType) {
        AutomationTaskType.PROFILE_PRIVATE_MESSAGE -> LocalTaskQueueType.B_END_PRIVATE_MESSAGE
        AutomationTaskType.COMMENT_PRIVATE_MESSAGE -> snapshot.commentConfig
            ?.takeIf { it.entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE }
            ?.let { LocalTaskQueueType.COMMENT_SEARCH_PROFILE }
    }

    fun validate(tasks: List<TaskSnapshot>): LocalTaskQueueType? {
        if (tasks.isEmpty() || tasks.size > AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE) return null
        val type = tasks.firstOrNull()?.let(::typeOf) ?: return null
        return type.takeIf { candidate -> tasks.all { typeOf(it) == candidate } }
    }
}

/** The controller-side work to perform after one task reaches any terminal phase. */
enum class LocalTaskQueueTerminalRoute {
    /** Keep the terminal state visible briefly, then start the next frozen task. */
    START_NEXT_TASK,
    /** The active task was the last local queue item; persist the completed queue state. */
    COMPLETE_QUEUE,
    /** No local queue owns this task, so its normal terminal publication is final. */
    FINISH_STANDALONE,
}

/**
 * Keeps terminal queue routing independent from phase publication and delayed task startup.
 * A pending frozen task always wins, preserving the existing behavior even if a legacy session
 * is unavailable in memory.
 */
object LocalTaskQueueTerminalPolicy {
    fun route(
        hasPendingTask: Boolean,
        hasLocalQueueSession: Boolean,
    ): LocalTaskQueueTerminalRoute = when {
        hasPendingTask -> LocalTaskQueueTerminalRoute.START_NEXT_TASK
        hasLocalQueueSession -> LocalTaskQueueTerminalRoute.COMPLETE_QUEUE
        else -> LocalTaskQueueTerminalRoute.FINISH_STANDALONE
    }
}

/**
 * Decides whether a paused local queue may reuse the visible target-app page. It never allows
 * comment search tasks to resume in place because a later comment-author profile is
 * structurally indistinguishable from the searched source profile.
 */
object LocalTaskQueueResumePolicy {
    fun requiresInitialRestart(
        queueType: LocalTaskQueueType,
        pausedPhase: AutomationPhase?,
        visiblePage: PageKind?,
    ): Boolean {
        if (queueType == LocalTaskQueueType.COMMENT_SEARCH_PROFILE) return true
        val expectedPages = expectedPagesFor(pausedPhase) ?: return true
        return visiblePage !in expectedPages
    }

    private fun expectedPagesFor(phase: AutomationPhase?): Set<PageKind>? = when (phase) {
        AutomationPhase.LAUNCHING_TARGET,
        AutomationPhase.WAITING_FOR_HOME,
        -> setOf(PageKind.HOME)

        AutomationPhase.OPENING_SEARCH,
        AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
        AutomationPhase.ENTERING_KEYWORD,
        -> setOf(PageKind.SEARCH_ENTRY)

        AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
        AutomationPhase.SELECTING_USER_TAB,
        -> setOf(PageKind.SEARCH_RESULTS)

        AutomationPhase.WAITING_FOR_USER_RESULTS,
        AutomationPhase.SELECTING_USER_RESULT,
        -> setOf(PageKind.USER_RESULTS)

        AutomationPhase.WAITING_FOR_PROFILE,
        AutomationPhase.OPENING_MESSAGE_ENTRY,
        -> setOf(PageKind.USER_PROFILE)

        AutomationPhase.WAITING_FOR_DIRECT_MESSAGE,
        AutomationPhase.COMPLETED_AT_MESSAGE_PAGE,
        -> setOf(PageKind.DIRECT_MESSAGE)

        else -> null
    }
}
