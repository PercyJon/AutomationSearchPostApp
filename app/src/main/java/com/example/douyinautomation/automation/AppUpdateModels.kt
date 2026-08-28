package com.example.douyinautomation.automation

data class AppUpdateInfo(
    val forceUpdate: Boolean,
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val fileSize: Long,
    val sha256: String,
)

data class AppUpdateCheckResult(
    val updateAvailable: Boolean,
    val update: AppUpdateInfo? = null,
)

enum class AppUpdateStage {
    IDLE,
    CHECKING,
    READY,
    DOWNLOADING,
    INSTALLING,
    FAILED,
    UP_TO_DATE,
}

data class AppUpdateUiState(
    val stage: AppUpdateStage = AppUpdateStage.IDLE,
    val update: AppUpdateInfo? = null,
    val forceLocked: Boolean = false,
    val progressBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (progressBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

object AppUpdatePolicy {
    val terminalPhases: Set<AutomationPhase> = setOf(
        AutomationPhase.IDLE,
        AutomationPhase.SERVICE_READY,
        AutomationPhase.STOPPED,
        AutomationPhase.FAILED,
        AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE,
        AutomationPhase.COMPLETED_TASK,
        AutomationPhase.COMPLETED_AT_MESSAGE_PAGE,
        AutomationPhase.COMPLETED_MESSAGE_SENT,
    )

    fun shouldDeferForRunningTask(phase: AutomationPhase): Boolean = phase !in terminalPhases

    fun effectiveForce(serverForce: Boolean, taskDeferred: Boolean): Boolean =
        serverForce && !taskDeferred
}
