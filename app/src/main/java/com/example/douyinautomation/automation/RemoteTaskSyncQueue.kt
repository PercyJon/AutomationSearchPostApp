package com.example.douyinautomation.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Non-blocking B3 uploader.  The accessibility controller only enqueues work; network latency
 * never holds its state-machine mutex or delays a Douyin gesture.  Failed work is retried with a
 * small bounded backoff and then reported through the supplied callbacks.
 */
class RemoteTaskSyncQueue(
    private val scope: CoroutineScope,
    private val gatewayProvider: () -> AutomationTaskGateway?,
    private val logger: DiagnosticLogger,
    private val onFailure: (String) -> Unit = {},
    private val onSuccess: () -> Unit = {},
    private val onStatusSuccess: (RemoteTask) -> Unit = {},
    private val onPublishSuccess: (String, RemoteTask) -> Unit = { _, _ -> },
    private val onBackfillSuccess: (String) -> Unit = {},
) {
    private sealed interface Work {
        val taskId: Long

        data class Publish(
            val request: MobileTaskCreateRequest,
            val followUpStatus: Int? = null,
        ) : Work {
            override val taskId: Long = 0L
        }

        data class Backfill(
            val request: MobileTaskCreateRequest,
            val existingRemoteId: Long? = null,
            val statusRequest: RemoteTaskStatusRequest?,
            val records: List<RemoteRecordRequest>,
        ) : Work {
            override val taskId: Long = existingRemoteId ?: 0L
        }

        data class Checkpoint(
            override val taskId: Long,
            val request: RemoteCheckpointRequest,
        ) : Work

        data class Record(
            override val taskId: Long,
            val request: RemoteRecordRequest,
        ) : Work

        data class Status(
            override val taskId: Long,
            val request: RemoteTaskStatusRequest,
        ) : Work
    }

    private val queue = Channel<Work>(capacity = MAX_PENDING_WORK)
    private val pending = MutableStateFlow(0)
    private val worker: Job = scope.launch {
        for (work in queue) {
            pending.update { (it - 1).coerceAtLeast(0) }
            process(work)
        }
    }

    val pendingCount: StateFlow<Int> = pending.asStateFlow()

    fun enqueueCheckpoint(taskId: Long, request: RemoteCheckpointRequest) {
        enqueue(Work.Checkpoint(taskId, request))
    }

    fun enqueueRecord(taskId: Long, request: RemoteRecordRequest) {
        enqueue(Work.Record(taskId, request))
    }

    fun enqueueStatus(taskId: Long, request: RemoteTaskStatusRequest) {
        enqueue(Work.Status(taskId, request))
    }

    fun enqueuePublish(request: MobileTaskCreateRequest, followUpStatus: Int? = null) {
        enqueue(Work.Publish(request, followUpStatus))
    }

    fun enqueueBackfill(
        request: MobileTaskCreateRequest,
        existingRemoteId: Long? = null,
        statusRequest: RemoteTaskStatusRequest? = null,
        records: List<RemoteRecordRequest> = emptyList(),
    ) {
        enqueue(Work.Backfill(request, existingRemoteId, statusRequest, records))
    }

    fun close() {
        queue.close()
        worker.cancel()
    }

    private fun enqueue(work: Work) {
        pending.update { it + 1 }
        val result = queue.trySend(work)
        if (result.isFailure) {
            pending.update { (it - 1).coerceAtLeast(0) }
            logger.warn(
                "remote_sync_queue_full",
                message = "Remote sync queue is full; local execution remains authoritative",
                attributes = mapOf("task_id_hash" to work.taskId.hashCode()),
            )
            onFailure("远程同步队列已满，本地任务仍会继续")
        }
    }

    private suspend fun process(work: Work) {
        val gateway = gatewayProvider()
        if (gateway == null) return
        var lastError: Throwable? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val response = when (work) {
                    is Work.Publish -> {
                        val created = gateway.createTask(work.request)
                        onPublishSuccess(work.request.localTaskId, created)
                        if (work.followUpStatus != null) {
                            gateway.updateTaskStatus(
                                created.id,
                                RemoteTaskStatusRequest(status = work.followUpStatus),
                            )
                        } else {
                            created
                        }
                    }
                    is Work.Backfill -> {
                        var last: RemoteTask? = null
                        val remoteId = if (work.existingRemoteId != null) {
                            work.existingRemoteId
                        } else {
                            gateway.createTask(work.request).also { created ->
                                last = created
                                onPublishSuccess(work.request.localTaskId, created)
                            }.id
                        }
                        if (work.records.isNotEmpty()) {
                            last = runCatching {
                                gateway.updateTaskStatus(
                                    remoteId,
                                    RemoteTaskStatusRequest(status = RemoteTaskStatus.RUNNING),
                                )
                            }.getOrNull() ?: last
                            for (record in work.records) {
                                gateway.submitRecord(remoteId, record)
                            }
                        }
                        val statusRequest = work.statusRequest
                        if (statusRequest != null) {
                            last = try {
                                gateway.updateTaskStatus(remoteId, statusRequest)
                            } catch (error: Throwable) {
                                if (error !is AutomationGatewayException || error.statusCode != 422) {
                                    throw error
                                }
                                runCatching {
                                    gateway.updateTaskStatus(
                                        remoteId,
                                        RemoteTaskStatusRequest(status = RemoteTaskStatus.RUNNING),
                                    )
                                }
                                gateway.updateTaskStatus(remoteId, statusRequest)
                            }
                        }
                        last ?: RemoteTask(
                            id = remoteId,
                            code = "",
                            name = work.request.name,
                            keyword = work.request.keyword,
                            regionName = work.request.regionName,
                            regionPrefix = work.request.regionPrefix,
                            message = "",
                            sendMode = work.request.sendMode,
                            catalogVersion = work.request.catalogVersion,
                            maxUsers = work.request.maxUsers,
                            status = work.statusRequest?.status ?: RemoteTaskStatus.READY,
                            licenseId = null,
                            totalCount = 0,
                            processedCount = 0,
                            successCount = 0,
                            failedCount = 0,
                            skippedCount = 0,
                            lastUserKey = null,
                            lastUserName = null,
                            lastPageNumber = null,
                            lastPageFingerprint = null,
                            checkpointVersion = 0,
                        )
                    }
                    is Work.Checkpoint -> gateway.submitCheckpoint(work.taskId, work.request)
                    is Work.Record -> gateway.submitRecord(work.taskId, work.request)
                    is Work.Status -> gateway.updateTaskStatus(work.taskId, work.request)
                }
                if ((work is Work.Status || work is Work.Publish || work is Work.Backfill) && response is RemoteTask) {
                    onStatusSuccess(response)
                }
                logger.info(
                    "remote_sync_succeeded",
                    attributes = mapOf(
                        "task_id_hash" to work.taskId.hashCode(),
                        "kind" to work::class.simpleName.orEmpty(),
                        "attempt" to attempt + 1,
                    ),
                )
                onSuccess()
                if (work is Work.Backfill) {
                    onBackfillSuccess(work.request.localTaskId)
                }
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                val permanent = error is AutomationGatewayException && error.statusCode in setOf(401, 403, 404)
                if (permanent || attempt == MAX_ATTEMPTS - 1) break
                delay(RETRY_DELAYS_MILLIS[attempt])
            }
        }
        val status = (lastError as? AutomationGatewayException)?.statusCode ?: 0
        logger.warn(
            "remote_sync_failed",
            message = "Remote task synchronization failed after bounded retries",
            attributes = mapOf("task_id_hash" to work.taskId.hashCode(), "status" to status),
        )
        onFailure(if (status in setOf(401, 403)) "远程授权已失效，任务仍保留本地记录" else "远程同步暂时失败，已保留本地记录")
    }

    companion object {
        private const val MAX_PENDING_WORK = 128
        private const val MAX_ATTEMPTS = 3
        private val RETRY_DELAYS_MILLIS = longArrayOf(500L, 1_500L)
    }
}

/** Maps local safety-probe outcomes to the B3 record status values. */
object RemoteTaskRecordStatus {
    const val PENDING = 0
    const val PROCESSING = 1
    const val SUCCESS = 2
    const val FAILED = 3
    const val SKIPPED = 4
    const val BLOCKED = 5

    fun from(outcome: UserTaskRecord.Outcome): Int = when (outcome) {
        UserTaskRecord.Outcome.IN_PROGRESS -> PROCESSING
        UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED,
        UserTaskRecord.Outcome.PROFILE_OPENED,
        -> SUCCESS
        UserTaskRecord.Outcome.FILTERED_BY_KEYWORD -> BLOCKED
        UserTaskRecord.Outcome.DUPLICATE_SKIPPED,
        UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED,
        -> SKIPPED
        UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
        UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
        UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
        UserTaskRecord.Outcome.PAUSED,
        UserTaskRecord.Outcome.STOPPED,
        -> FAILED
    }
}
