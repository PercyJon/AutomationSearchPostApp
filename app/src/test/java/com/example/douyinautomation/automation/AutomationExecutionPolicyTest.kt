package com.example.douyinautomation.automation

import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutomationExecutionPolicyTest {
    @Test
    fun `task defaults to one thousand users and rejects a larger bound`() {
        val defaultDraft = TaskDraft(id = "local", name = "", customKeywords = listOf("红木沙发"))
        val tooLargeDraft = defaultDraft.copy(maxUsers = AutomationExecutionLimits.MAX_USERS_PER_TASK + 1)

        assertEquals(1_000, defaultDraft.maxUsers)
        assertTrue(tooLargeDraft.validationErrors().any { it.contains("1000") })
    }

    @Test
    fun `legacy auto-saved default migrates to one thousand while explicit values remain bounded`() {
        assertEquals(1_000, TaskUserLimitInputPolicy.valueForPersistedDraft(20))
        assertEquals(50, TaskUserLimitInputPolicy.valueForPersistedDraft(50))
        assertEquals("1000", TaskUserLimitInputPolicy.sanitizeInput("9999"))
        assertEquals("", TaskUserLimitInputPolicy.sanitizeInput(""))
    }

    @Test
    fun `comment task total cannot exceed one thousand users`() {
        val config = CommentPrivateMessageConfig(
            targetUser = "designer",
            maxVideos = 3,
            maxUsersPerVideo = 500,
        )

        assertTrue(config.validationErrors().any { it.contains("总上限") })
        assertEquals(
            "评论私信单任务用户总上限不能超过 1000",
            AutomationTaskLimitPolicy.taskValidationError(
                snapshot(id = "comment", commentConfig = config.toSnapshot()),
            ),
        )
    }

    @Test
    fun `snapshot gate rejects invalid comment bounds before navigation starts`() {
        val invalid = snapshot(
            id = "comment-invalid",
            commentConfig = CommentPrivateMessageSnapshot(
                entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE,
                targetUser = "designer",
                matchKeywords = emptyList(),
                maxVideos = 0,
                maxUsersPerVideo = 1,
            ),
        )

        assertEquals("视频数量上限必须在 1-500 之间", AutomationTaskLimitPolicy.taskValidationError(invalid))
    }

    @Test
    fun `remote task requires an allow-listed id while local task is authorized`() {
        assertEquals(
            TaskStartAuthorization.LOCAL_AUTHORIZED,
            TaskStartAuthorizationPolicy.decide(
                snapshot = snapshot(id = "local-uuid"),
                remoteResume = null,
                authorizedRemoteTaskIds = emptySet(),
            ),
        )
        assertEquals(
            TaskStartAuthorization.REMOTE_NOT_AUTHORIZED,
            TaskStartAuthorizationPolicy.decide(
                snapshot = snapshot(id = "42"),
                remoteResume = RemoteTaskResume(42L, emptyRemoteProgress(42L)),
                authorizedRemoteTaskIds = setOf(41L),
            ),
        )
        assertEquals(
            TaskStartAuthorization.REMOTE_AUTHORIZED,
            TaskStartAuthorizationPolicy.decide(
                snapshot = snapshot(id = "42"),
                remoteResume = RemoteTaskResume(42L, emptyRemoteProgress(42L)),
                authorizedRemoteTaskIds = setOf(42L),
            ),
        )
    }

    @Test
    fun `remote allow-list accepts Chinese and English separators`() {
        assertEquals(setOf(101L, 102L, 103L), RemoteTaskAuthorizationStore.parse("101，102, 103\n无效"))
    }

    @Test
    fun `daily unique ledger rejects only a new fingerprint at the daily ceiling then resets next day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = 1_724_551_200_000L
        val fullLedger = (1..AutomationExecutionLimits.MAX_DAILY_UNIQUE_USERS).associate { index ->
            "fingerprint-$index" to now
        }

        assertEquals(
            DailyUserAdmission.ADMITTED,
            DailyUserLimitPolicy.admit(fullLedger, "fingerprint-1", now, zone),
        )
        assertEquals(
            DailyUserAdmission.DAILY_UNIQUE_USER_LIMIT_REACHED,
            DailyUserLimitPolicy.admit(fullLedger, "fingerprint-new", now, zone),
        )
        val nextDay = now + 24L * 60L * 60L * 1_000L
        assertEquals(
            DailyUserAdmission.ADMITTED,
            DailyUserLimitPolicy.admit(fullLedger, "fingerprint-new", nextDay, zone),
        )
        assertTrue(DailyUserLimitPolicy.retainCurrentDay(fullLedger, nextDay, zone).isEmpty())
    }

    @Test
    fun `blank action interval disables the global action pacer`() = runBlocking {
        var now = 1_000L
        val waits = mutableListOf<Long>()
        val pacer = AutomationActionPacer(
            nowMillis = { now },
            wait = { duration ->
                waits += duration
                now += duration
            },
        )

        assertEquals(0L, pacer.awaitTurn())
        now += 100L
        assertEquals(0L, pacer.awaitTurn())
        assertEquals(0L, pacer.awaitTurn())
        assertTrue(waits.isEmpty())
    }

    @Test
    fun `action interval accepts blank and only one to five seconds`() {
        assertNull(AutomationActionIntervalPolicy.validationError(""))
        assertNull(AutomationActionIntervalPolicy.validationError("1000"))
        assertNull(AutomationActionIntervalPolicy.validationError("5000"))
        assertTrue(AutomationActionIntervalPolicy.validationError("999")?.contains("1000-5000") == true)
        assertTrue(AutomationActionIntervalPolicy.validationError("5001")?.contains("1000-5000") == true)
        assertEquals(null, AutomationActionIntervalPolicy.configuredIntervalMillisOrNull(""))
        assertEquals(1_000L, AutomationActionIntervalPolicy.configuredIntervalMillisOrNull("1000"))
        assertEquals(5_000L, AutomationActionIntervalPolicy.configuredIntervalMillisOrNull("5000"))
        assertEquals(null, AutomationActionIntervalPolicy.configuredIntervalMillisOrNull("5001"))
        assertEquals("1000", AutomationActionIntervalPolicy.sanitizeInput("1a0b0c0"))
    }

    @Test
    fun `action pacer reads the configured interval for each new action slot`() = runBlocking {
        var now = 1_000L
        var configuredIntervalMillis = 1_000L
        val waits = mutableListOf<Long>()
        val pacer = AutomationActionPacer(
            minIntervalMillisProvider = { configuredIntervalMillis },
            nowMillis = { now },
            wait = { duration ->
                waits += duration
                now += duration
            },
        )

        assertEquals(0L, pacer.awaitTurn())
        now += 100L
        assertEquals(900L, pacer.awaitTurn())
        configuredIntervalMillis = 5_000L
        assertEquals(5_000L, pacer.awaitTurn())
        assertEquals(listOf(900L, 5_000L), waits)
    }

    @Test
    fun `local queue rejects more than nine hundred ninety nine tasks`() {
        val tooMany = List(AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE + 1) { index ->
            snapshot(id = "task-$index")
        }

        assertNull(LocalTaskQueuePolicy.validate(tooMany))
        assertFailsWith<IllegalArgumentException> {
            LocalTaskQueueSession(
                queueId = "queue",
                queueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
                tasks = tooMany,
                updatedAtMillis = 0L,
            )
        }
    }

    private fun CommentPrivateMessageConfig.toSnapshot() = CommentPrivateMessageSnapshot(
        entryMode = entryMode,
        targetUser = targetUser,
        matchKeywords = matchKeywords,
        matchMode = matchMode,
        maxVideos = maxVideos,
        maxUsersPerVideo = maxUsersPerVideo,
        skipPinnedVideos = skipPinnedVideos,
        dryRun = dryRun,
    )

    private fun emptyRemoteProgress(taskId: Long) = RemoteTaskProgress(
        taskId = taskId,
        status = 1,
        totalCount = 0,
        processedCount = 0,
        pendingCount = 0,
        successCount = 0,
        failedCount = 0,
        skippedCount = 0,
        completionPercent = 0.0,
        lastUserKey = null,
        lastUserName = null,
        lastPageNumber = null,
        lastPageFingerprint = null,
        checkpointVersion = 0,
    )

    private fun snapshot(
        id: String,
        commentConfig: CommentPrivateMessageSnapshot? = null,
    ) = TaskSnapshot(
        taskId = id,
        taskName = id,
        presetVersion = "test",
        baseKeywords = listOf("keyword"),
        region = "",
        composedQueries = listOf("keyword"),
        normalizedBlockedKeywords = emptyList(),
        maxUsers = 1,
        messageTemplate = null,
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
        createdAtMillis = 0L,
        taskType = if (commentConfig == null) {
            AutomationTaskType.PROFILE_PRIVATE_MESSAGE
        } else {
            AutomationTaskType.COMMENT_PRIVATE_MESSAGE
        },
        commentConfig = commentConfig,
    )
}
