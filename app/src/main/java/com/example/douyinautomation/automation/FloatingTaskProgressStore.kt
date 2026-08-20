package com.example.douyinautomation.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-local progress bridge for a future WindowManager overlay.
 *
 * The store deliberately contains counters and stage labels only; it never retains comments,
 * OCR output, or account text.  It is independent from the existing B-end runner so the overlay
 * can be reused by comment private messaging and later automation modules without changing the
 * already-validated task controller.
 */
class FloatingTaskProgressStore(
    initial: FloatingTaskProgress = FloatingTaskProgress(),
) {
    private val _progress = MutableStateFlow(initial)
    val progress: StateFlow<FloatingTaskProgress> = _progress.asStateFlow()

    /** Binds a newly selected task and resets all counters before the overlay is shown. */
    fun start(
        taskId: String,
        taskName: String,
        taskType: AutomationTaskType,
        total: Int,
        stageLabel: String? = null,
    ) {
        _progress.value = FloatingTaskProgress(
            taskId = taskId,
            taskName = taskName,
            taskType = taskType,
            isRunning = true,
            isPaused = false,
            total = total.coerceAtLeast(0),
            stageLabel = stageLabel,
        )
    }

    /** Updates only counters/stage, preserving the task identity and running state. */
    fun update(
        processed: Int? = null,
        total: Int? = null,
        success: Int? = null,
        failed: Int? = null,
        skipped: Int? = null,
        stageLabel: String? = null,
    ) {
        _progress.update { current ->
            current.copy(
                processed = (processed ?: current.processed).coerceAtLeast(0),
                total = (total ?: current.total).coerceAtLeast(0),
                success = (success ?: current.success).coerceAtLeast(0),
                failed = (failed ?: current.failed).coerceAtLeast(0),
                skipped = (skipped ?: current.skipped).coerceAtLeast(0),
                stageLabel = stageLabel ?: current.stageLabel,
            )
        }
    }

    fun pause(stageLabel: String = "已暂停") {
        _progress.update { current ->
            if (!current.isRunning) current else current.copy(isPaused = true, stageLabel = stageLabel)
        }
    }

    fun resume(stageLabel: String? = null) {
        _progress.update { current ->
            if (!current.isRunning) current else current.copy(
                isPaused = false,
                stageLabel = stageLabel ?: current.stageLabel,
            )
        }
    }

    fun stop(stageLabel: String = "已停止") {
        _progress.update { current ->
            current.copy(isRunning = false, isPaused = false, stageLabel = stageLabel)
        }
    }

    fun complete(stageLabel: String = "已完成") {
        _progress.update { current ->
            current.copy(
                isRunning = false,
                isPaused = false,
                processed = current.total.coerceAtLeast(current.processed),
                stageLabel = stageLabel,
            )
        }
    }

    /** Hides the overlay state after the owning task has left the foreground. */
    fun clear() {
        _progress.value = FloatingTaskProgress()
    }
}
