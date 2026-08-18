package com.example.douyinautomation.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LicenseStatus {
    NOT_CONFIGURED,
    VERIFIED,
    REJECTED,
    TEMPORARILY_UNAVAILABLE,
}

data class LicenseUiState(
    val status: LicenseStatus = LicenseStatus.NOT_CONFIGURED,
    val message: String = "尚未配置授权服务",
    val lastHeartbeatAtMillis: Long? = null,
    val nextHeartbeatAtMillis: Long? = null,
)

data class HeartbeatRequest(
    val deviceIdHash: String,
    val appVersion: String,
    val platform: String = "android",
)

data class HeartbeatResponse(
    val accepted: Boolean,
    val message: String,
    val nextCheckAfterMillis: Long = DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
) {
    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000L
    }
}

data class AuthConfig(
    val endpoint: String,
    val licenseToken: String,
    val deviceId: String,
) {
    fun isUsable(): Boolean = endpoint.startsWith("https://") &&
        licenseToken.isNotBlank() &&
        deviceId.isNotBlank()
}

fun interface HeartbeatGateway {
    suspend fun verify(request: HeartbeatRequest): HeartbeatResponse
}

/** Explicit default until the product backend contract and URL are supplied. */
object UnconfiguredHeartbeatGateway : HeartbeatGateway {
    override suspend fun verify(request: HeartbeatRequest): HeartbeatResponse =
        HeartbeatResponse(accepted = false, message = "后端 heartbeat 尚未配置")
}

object HeartbeatPolicy {
    fun evaluate(
        config: AuthConfig?,
        response: HeartbeatResponse?,
        nowMillis: Long,
    ): LicenseUiState = when {
        config == null || !config.isUsable() -> LicenseUiState(
            status = LicenseStatus.NOT_CONFIGURED,
            message = "尚未配置授权服务",
        )
        response == null -> LicenseUiState(
            status = LicenseStatus.TEMPORARILY_UNAVAILABLE,
            message = "授权服务暂时不可用",
            lastHeartbeatAtMillis = nowMillis,
        )
        response.accepted -> LicenseUiState(
            status = LicenseStatus.VERIFIED,
            message = response.message,
            lastHeartbeatAtMillis = nowMillis,
            nextHeartbeatAtMillis = nowMillis + response.nextCheckAfterMillis.coerceAtLeast(60_000L),
        )
        else -> LicenseUiState(
            status = LicenseStatus.REJECTED,
            message = response.message,
            lastHeartbeatAtMillis = nowMillis,
            nextHeartbeatAtMillis = nowMillis + response.nextCheckAfterMillis.coerceAtLeast(60_000L),
        )
    }
}

/** Coroutine coordinator; it never logs or exposes the raw license token. */
class HeartbeatCoordinator(
    private val configProvider: () -> AuthConfig?,
    private val gatewayProvider: () -> HeartbeatGateway,
    private val appVersion: String,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(LicenseUiState())
    private var periodicJob: Job? = null

    val state: StateFlow<LicenseUiState> = _state.asStateFlow()

    suspend fun verifyNow(): LicenseUiState {
        val config = configProvider()
        if (config == null || !config.isUsable()) {
            val nextState = HeartbeatPolicy.evaluate(config, null, nowMillis())
            _state.emit(nextState)
            return nextState
        }
        val response = runCatching {
            gatewayProvider().verify(
                HeartbeatRequest(
                    deviceIdHash = config.deviceId.hashCode().toString(16),
                    appVersion = appVersion,
                ),
            )
        }.getOrNull()
        val nextState = HeartbeatPolicy.evaluate(config, response, nowMillis())
        _state.emit(nextState)
        return nextState
    }

    fun start(scope: CoroutineScope, intervalMillis: Long = HeartbeatResponse.DEFAULT_HEARTBEAT_INTERVAL_MILLIS) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                verifyNow()
                delay(intervalMillis.coerceAtLeast(60_000L))
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
    }
}

object AuthStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var secureStore: SecureAuthStore? = null
    private var coordinator: HeartbeatCoordinator? = null
    private val _uiState = MutableStateFlow(LicenseUiState())

    val uiState: StateFlow<LicenseUiState> = _uiState.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (secureStore != null) return
        val store = SecureAuthStore(context.applicationContext)
        secureStore = store
        coordinator = HeartbeatCoordinator(
            configProvider = { store.read() },
            gatewayProvider = { UnconfiguredHeartbeatGateway },
            appVersion = "0.3.0-m3",
        )
        coordinator?.state?.let { state ->
            scope.launch { state.collect { _uiState.emit(it) } }
        }
        coordinator?.start(scope)
    }

    fun verifyNow() {
        scope.launch { coordinator?.verifyNow() }
    }
}
