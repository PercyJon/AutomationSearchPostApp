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
import java.security.MessageDigest

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
                    // The backend requires a stable, non-reversible digest (minimum 16 chars).
                    // Do not send the device identifier itself over the wire.
                    deviceIdHash = stableDeviceHash(config.deviceId),
                    appVersion = appVersion,
                ),
            )
        }.getOrElse { error ->
            // Authentication failures are a definitive rejection; timeouts and transport
            // failures remain temporary so a device can recover without blocking local safety
            // diagnostics.
            if (error is AutomationGatewayException && error.statusCode in setOf(401, 403)) {
                HeartbeatResponse(accepted = false, message = error.message ?: "授权被拒绝")
            } else {
                null
            }
        }
        val nextState = HeartbeatPolicy.evaluate(config, response, nowMillis())
        _state.emit(nextState)
        return nextState
    }

    private fun stableDeviceHash(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

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
            gatewayProvider = {
                store.read()
                    ?.takeIf(AuthConfig::isUsable)
                    ?.let(::AutomationHttpClient)
                    ?: UnconfiguredHeartbeatGateway
            },
            appVersion = "0.3.0-m3-p4",
        )
        coordinator?.state?.let { state ->
            scope.launch { state.collect { _uiState.emit(it) } }
        }
        coordinator?.start(scope)
    }

    fun verifyNow() {
        scope.launch { coordinator?.verifyNow() }
    }

    /** Saves a validated endpoint/token/device tuple in Android Keystore-backed storage. */
    fun saveConfig(context: android.content.Context, config: AuthConfig): Boolean {
        initialize(context)
        return secureStore?.save(config) == true
    }

    fun clearConfig(context: android.content.Context) {
        initialize(context)
        secureStore?.clear()
        verifyNow()
    }

    fun currentConfig(): AuthConfig? = secureStore?.read()

    /** Remote-first catalog with the existing 24-hour cache and built-in fallback. */
    fun searchPresetRepository(context: android.content.Context): SearchPresetRepository {
        initialize(context)
        val config = secureStore?.read()
        val remote = if (config?.isUsable() == true) {
            AutomationHttpClient(config)
        } else {
            SearchPresetRemoteSource { throw IllegalStateException("后端授权尚未配置") }
        }
        return CachedSearchPresetRepository(
            remote = remote,
            cache = SharedPreferencesSearchPresetCache(context),
        )
    }

    suspend fun loadSearchPresets(
        context: android.content.Context,
        forceRefresh: Boolean = false,
    ): SearchPresetCatalog = searchPresetRepository(context).load(forceRefresh)

    suspend fun loadRegionCatalog(context: android.content.Context): RegionCatalog {
        initialize(context)
        val config = secureStore?.read()?.takeIf(AuthConfig::isUsable)
            ?: return RegionCatalog("local-empty", emptyList(), null)
        return AutomationHttpClient(config).fetchRegionCatalog()
    }

    suspend fun loadBlockKeywordCatalog(context: android.content.Context): BlockKeywordCatalog {
        initialize(context)
        val config = secureStore?.read()?.takeIf(AuthConfig::isUsable)
            ?: return BlockKeywordCatalog("local-empty", emptyList(), null)
        return AutomationHttpClient(config).fetchBlockKeywordCatalog()
    }

    suspend fun loadRemoteTasks(context: android.content.Context): List<RemoteTask> {
        initialize(context)
        val config = secureStore?.read()?.takeIf(AuthConfig::isUsable) ?: return emptyList()
        return AutomationHttpClient(config).listTasks()
    }

    /** Claim a task and fetch the authoritative progress in one resume transaction. */
    suspend fun claimRemoteTask(
        context: android.content.Context,
        taskId: Long,
    ): RemoteTaskSession {
        initialize(context)
        val config = secureStore?.read()?.takeIf(AuthConfig::isUsable)
            ?: error("后端授权尚未配置")
        val gateway = AutomationHttpClient(config)
        val task = gateway.claimTask(taskId)
        val progress = gateway.getTaskProgress(taskId)
        return RemoteTaskSession(task = task, progress = progress)
    }
}

data class RemoteTaskSession(
    val task: RemoteTask,
    val progress: RemoteTaskProgress,
)
