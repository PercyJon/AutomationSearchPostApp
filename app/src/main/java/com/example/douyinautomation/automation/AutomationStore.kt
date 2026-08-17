package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Commands emitted by the explicitly-operated diagnostics UI. */
sealed interface AutomationCommand {
    data class Start(
        val keyword: String,
        /** Optional one-message action performed after the direct-message page is verified. */
        val message: String = "",
    ) : AutomationCommand
    /** Explicitly sends one operator-provided message on the currently verified chat page. */
    data class SendMessage(val message: String) : AutomationCommand
    data object Pause : AutomationCommand
    data object Resume : AutomationCommand
    data object Stop : AutomationCommand
    data object CaptureDiagnostics : AutomationCommand
    data object DumpNodeTree : AutomationCommand
}

/** M0 stops at a verified direct-message page; M1 adds an explicitly-operated one-message action. */
enum class AutomationPhase {
    IDLE,
    SERVICE_READY,
    LAUNCHING_TARGET,
    WAITING_FOR_HOME,
    WAITING_FOR_SEARCH_ENTRY,
    OPENING_SEARCH,
    ENTERING_KEYWORD,
    WAITING_FOR_SEARCH_RESULTS,
    SELECTING_USER_TAB,
    WAITING_FOR_USER_RESULTS,
    SELECTING_USER_RESULT,
    WAITING_FOR_PROFILE,
    OPENING_MESSAGE_ENTRY,
    WAITING_FOR_DIRECT_MESSAGE,
    COMPLETED_AT_MESSAGE_PAGE,
    SENDING_MESSAGE,
    WAITING_FOR_MESSAGE_RESULT,
    COMPLETED_MESSAGE_SENT,
    PAUSED_FOR_MANUAL_HANDOFF,
    STOPPED,
    FAILED,
}

data class AutomationUiState(
    val serviceConnected: Boolean = false,
    val serviceStatusKnown: Boolean = false,
    val phase: AutomationPhase = AutomationPhase.IDLE,
    val lastPage: PageDetection? = null,
    val lastNodeDumpPath: String? = null,
    val lastScreenshotPath: String? = null,
    /** OCR is held in process/app-private diagnostics only; it is never sent to Logcat. */
    val lastOcrText: String? = null,
    val lastError: String? = null,
    val awaitingManualHandoff: Boolean = false,
    val diagnosticEntries: List<DiagnosticEntry> = emptyList(),
)

/**
 * Process-local bridge between the Compose diagnostics surface and the enabled accessibility
 * service. Commands are only accepted once Android has connected the service; this prevents the
 * app UI from appearing to control a service that the user has not explicitly enabled.
 */
object AutomationStore {
    val logger = DiagnosticLogger(logTag = "DyinPoc")

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _commands = MutableSharedFlow<AutomationCommand>(extraBufferCapacity = 16)
    private val _uiState = MutableStateFlow(AutomationUiState())

    val commands: SharedFlow<AutomationCommand> = _commands.asSharedFlow()
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        storeScope.launch {
            logger.entries.collect { entries ->
                _uiState.update { current -> current.copy(diagnosticEntries = entries) }
            }
        }
    }

    fun send(command: AutomationCommand) {
        if (!_uiState.value.serviceConnected) {
            val reason = "Enable the accessibility service before running this command."
            logger.warn("command_rejected", message = reason, attributes = mapOf("command" to command.name()))
            publishFailure(reason)
            return
        }
        if (!_commands.tryEmit(command)) {
            logger.warn("command_dropped", attributes = mapOf("command" to command.name()))
            publishFailure("The service command queue is full. Try again after the current action finishes.")
        }
    }

    fun markServiceConnected() {
        _uiState.update {
            it.copy(
                serviceConnected = true,
                serviceStatusKnown = true,
                phase = if (it.phase == AutomationPhase.IDLE) AutomationPhase.SERVICE_READY else it.phase,
                lastError = null,
            )
        }
    }

    fun markServiceDisconnected() {
        // AccessibilityService can be briefly destroyed and rebound during an APK update or
        // a screenshot request. Keep the last known connection while the activity's scheduled
        // refreshes verify the new binding; this avoids a misleading "not connected" flash.
        _uiState.update { it.copy(serviceStatusKnown = false) }
    }

    /**
     * Accessibility services may be bound before an activity is recreated after a settings change
     * or APK update. Ask Android for the source of truth whenever the diagnostics activity resumes
     * so the UI does not show a stale "not connected" state.
     */
    fun refreshServiceStatus(context: Context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val expectedServiceName = DouyinAccessibilityService::class.java.name
        val expectedServiceId = "${context.packageName}/$expectedServiceName"
        val enabledServices = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        val isEnabled = enabledServices.any { serviceInfo ->
            val resolved = serviceInfo.resolveInfo?.serviceInfo
            val resolvedComponentMatches = resolved?.packageName == context.packageName &&
                (resolved.name == expectedServiceName ||
                    resolved.name.endsWith(DouyinAccessibilityService::class.java.simpleName))
            val serviceId = serviceInfo.id.orEmpty()
            val serviceIdMatches = serviceId == expectedServiceId ||
                serviceId.endsWith("/$expectedServiceName") ||
                serviceId.endsWith("/${DouyinAccessibilityService::class.java.simpleName}")
            resolvedComponentMatches || serviceIdMatches
        }
        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val normalizedSetting = enabledSetting.orEmpty().replace(" ", "")
        val settingMatches = normalizedSetting
            .split(':')
            .any { value ->
                value.equals(expectedServiceId, ignoreCase = true) ||
                    value.endsWith("/$expectedServiceName", ignoreCase = true) ||
                    value.endsWith("/${DouyinAccessibilityService::class.java.simpleName}", ignoreCase = true) ||
                    (value.contains(context.packageName, ignoreCase = true) &&
                        value.contains(DouyinAccessibilityService::class.java.simpleName, ignoreCase = true))
            }
        val targetEnabled = isEnabled || settingMatches
        logger.info(
            "service_status_refreshed",
            attributes = mapOf("enabled_services" to enabledServices.size, "target_enabled" to targetEnabled),
        )

        _uiState.update { current ->
            current.copy(
                serviceConnected = targetEnabled,
                serviceStatusKnown = true,
                phase = if (targetEnabled && current.phase == AutomationPhase.IDLE) {
                    AutomationPhase.SERVICE_READY
                } else {
                    current.phase
                },
            )
        }
    }

    fun publishPhase(
        phase: AutomationPhase,
        error: String? = null,
        awaitingManualHandoff: Boolean = false,
    ) {
        _uiState.update {
            it.copy(
                phase = phase,
                lastError = error ?: it.lastError.takeUnless { phase != AutomationPhase.FAILED },
                awaitingManualHandoff = awaitingManualHandoff,
            )
        }
    }

    fun publishObservation(detection: PageDetection) {
        _uiState.update { it.copy(lastPage = detection) }
    }

    fun publishNodeDump(path: String) {
        _uiState.update { it.copy(lastNodeDumpPath = path) }
    }

    fun publishScreenshot(path: String) {
        _uiState.update { it.copy(lastScreenshotPath = path) }
    }

    fun publishOcr(text: String) {
        _uiState.update { it.copy(lastOcrText = text.take(MAX_OCR_PREVIEW)) }
    }

    fun publishFailure(reason: String) {
        _uiState.update {
            it.copy(
                phase = AutomationPhase.FAILED,
                lastError = reason,
                awaitingManualHandoff = false,
            )
        }
    }

    fun publishManualHandoff(reason: String) {
        _uiState.update {
            it.copy(
                phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
                lastError = reason,
                awaitingManualHandoff = true,
            )
        }
    }

    private fun AutomationCommand.name(): String = when (this) {
        is AutomationCommand.Start -> "start"
        is AutomationCommand.SendMessage -> "send_message"
        AutomationCommand.Pause -> "pause"
        AutomationCommand.Resume -> "resume"
        AutomationCommand.Stop -> "stop"
        AutomationCommand.CaptureDiagnostics -> "capture_diagnostics"
        AutomationCommand.DumpNodeTree -> "dump_node_tree"
    }

    private const val MAX_OCR_PREVIEW = 1_000
}
