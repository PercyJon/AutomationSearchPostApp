package com.example.douyinautomation.automation

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Product-level limits applied before an automation command can change the target application. */
object AutomationExecutionLimits {
    /** Historical auto-saved default; migrated only when reopening an unfinished form draft. */
    const val LEGACY_DEFAULT_MAX_USERS_PER_TASK = 20
    const val DEFAULT_MAX_USERS_PER_TASK = 1_000
    const val MAX_USERS_PER_TASK = 1_000
    const val MAX_TASKS_PER_LOCAL_QUEUE = 999
    const val MAX_DAILY_UNIQUE_USERS = 9_999
    const val MIN_ACTION_INTERVAL_MILLIS = 3_000L
}

/** Keeps the form's empty/default behavior stable across the legacy 20-person draft migration. */
object TaskUserLimitInputPolicy {
    fun valueForPersistedDraft(savedValue: Int): Int = when (savedValue) {
        AutomationExecutionLimits.LEGACY_DEFAULT_MAX_USERS_PER_TASK ->
            AutomationExecutionLimits.DEFAULT_MAX_USERS_PER_TASK

        else -> savedValue.coerceIn(1, AutomationExecutionLimits.MAX_USERS_PER_TASK)
    }

    fun sanitizeInput(raw: String): String {
        val digits = raw.filter(Char::isDigit).take(4)
        return digits.toIntOrNull()
            ?.coerceAtMost(AutomationExecutionLimits.MAX_USERS_PER_TASK)
            ?.toString()
            ?: digits
    }
}

/** Keeps task limits independent from Compose forms and remote-task parsing. */
object AutomationTaskLimitPolicy {
    fun taskValidationError(snapshot: TaskSnapshot): String? {
        if (snapshot.maxUsers !in 1..AutomationExecutionLimits.MAX_USERS_PER_TASK) {
            return "单任务用户上限必须在 1-${AutomationExecutionLimits.MAX_USERS_PER_TASK} 之间"
        }
        val comment = snapshot.commentConfig ?: return null
        if (comment.maxVideos !in 1..CommentPrivateMessageConfig.MAX_VIDEOS) {
            return "视频数量上限必须在 1-${CommentPrivateMessageConfig.MAX_VIDEOS} 之间"
        }
        if (comment.maxUsersPerVideo !in 1..CommentPrivateMessageConfig.MAX_USERS_PER_VIDEO) {
            return "每个视频的用户数量上限必须在 1-${CommentPrivateMessageConfig.MAX_USERS_PER_VIDEO} 之间"
        }
        return commentValidationError(comment)
    }

    fun commentValidationError(config: CommentPrivateMessageConfig): String? =
        commentValidationError(config.maxVideos, config.maxUsersPerVideo)

    fun commentValidationError(snapshot: CommentPrivateMessageSnapshot): String? =
        commentValidationError(snapshot.maxVideos, snapshot.maxUsersPerVideo)

    private fun commentValidationError(maxVideos: Int, maxUsersPerVideo: Int): String? {
        val total = maxVideos.toLong() * maxUsersPerVideo.toLong()
        return if (total > AutomationExecutionLimits.MAX_USERS_PER_TASK) {
            "评论私信单任务用户总上限不能超过 ${AutomationExecutionLimits.MAX_USERS_PER_TASK}"
        } else {
            null
        }
    }
}

/** The only two task origins recognized by the authorization gate. */
enum class TaskStartAuthorization {
    LOCAL_AUTHORIZED,
    REMOTE_AUTHORIZED,
    REMOTE_NOT_AUTHORIZED,
}

/**
 * Local UI creates UUID task ids and is explicitly operator-authorized. A remote task is
 * identified by its remote-resume contract (or a persisted numeric task id on rebind) and must
 * be present in the operator-managed local allow-list.
 */
object TaskStartAuthorizationPolicy {
    fun decide(
        snapshot: TaskSnapshot?,
        remoteResume: RemoteTaskResume?,
        authorizedRemoteTaskIds: Set<Long>,
    ): TaskStartAuthorization {
        val remoteTaskId = remoteResume?.taskId
            ?: snapshot?.taskId?.toLongOrNull()?.takeIf { it > 0L }
            ?: return TaskStartAuthorization.LOCAL_AUTHORIZED
        return if (remoteTaskId in authorizedRemoteTaskIds) {
            TaskStartAuthorization.REMOTE_AUTHORIZED
        } else {
            TaskStartAuthorization.REMOTE_NOT_AUTHORIZED
        }
    }
}

enum class DailyUserAdmission {
    ADMITTED,
    DAILY_UNIQUE_USER_LIMIT_REACHED,
    NO_ACTIVE_TASK,
}

/**
 * Stores only opaque fingerprints with their reservation time. Entries are retained for the
 * current local calendar day so a 9,999-person limit remains reliable even when detail history
 * is compacted.
 */
object DailyUserLimitPolicy {
    fun startOfLocalDayMillis(
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    fun retainCurrentDay(
        reservations: Map<String, Long>,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<String, Long> {
        val dayStart = startOfLocalDayMillis(nowMillis, zoneId)
        return reservations.filter { (fingerprint, reservedAt) ->
            fingerprint.isNotBlank() && reservedAt >= dayStart && reservedAt <= nowMillis
        }
    }

    fun admit(
        reservations: Map<String, Long>,
        fingerprint: String,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailyUserAdmission {
        if (fingerprint.isBlank()) return DailyUserAdmission.NO_ACTIVE_TASK
        val currentDay = retainCurrentDay(reservations, nowMillis, zoneId)
        if (fingerprint in currentDay) return DailyUserAdmission.ADMITTED
        return if (currentDay.size >= AutomationExecutionLimits.MAX_DAILY_UNIQUE_USERS) {
            DailyUserAdmission.DAILY_UNIQUE_USER_LIMIT_REACHED
        } else {
            DailyUserAdmission.ADMITTED
        }
    }
}

/**
 * Process-wide-within-service action slot allocator. The caller reserves a slot immediately
 * before it changes the target app, guaranteeing the required gap across concurrent controller
 * and comment-runtime coroutines.
 */
class AutomationActionPacer(
    private val minIntervalMillis: Long = AutomationExecutionLimits.MIN_ACTION_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = { android.os.SystemClock.uptimeMillis() },
    private val wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val mutex = Mutex()
    private var lastActionAtMillis: Long? = null

    suspend fun awaitTurn(): Long = mutex.withLock {
        val previous = lastActionAtMillis
        val requiredWait = previous
            ?.let { (minIntervalMillis - (nowMillis() - it)).coerceAtLeast(0L) }
            ?: 0L
        if (requiredWait > 0L) wait(requiredWait)
        lastActionAtMillis = nowMillis()
        requiredWait
    }
}
