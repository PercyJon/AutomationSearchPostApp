package com.example.douyinautomation.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.douyinautomation.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppUpdateController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()
    private var autoChecked = false
    private var running: Job? = null

    fun autoCheckOnce(taskDeferred: Boolean) {
        if (autoChecked) return
        autoChecked = true
        if (taskDeferred) return
        startCheck(silent = true, taskDeferred = false)
    }

    fun checkManually(taskDeferred: Boolean) {
        startCheck(silent = false, taskDeferred = taskDeferred)
    }

    fun dismiss() {
        if (_uiState.value.forceLocked) return
        running?.cancel()
        _uiState.value = AppUpdateUiState()
    }

    fun startDownload(context: Context) {
        val update = _uiState.value.update ?: return
        val appContext = context.applicationContext
        running?.cancel()
        running = scope.launch {
            mutex.withLock { downloadAndInstall(appContext, update) }
        }
    }

    private fun startCheck(silent: Boolean, taskDeferred: Boolean) {
        running?.cancel()
        running = scope.launch {
            mutex.withLock { check(silent, taskDeferred) }
        }
    }

    private suspend fun check(silent: Boolean, taskDeferred: Boolean) {
        _uiState.value = _uiState.value.copy(stage = AppUpdateStage.CHECKING, message = null)
        val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable)
        if (config == null) {
            _uiState.value = AppUpdateUiState(
                stage = if (silent) AppUpdateStage.IDLE else AppUpdateStage.FAILED,
                message = if (silent) null else "未登录，无法检测更新",
            )
            return
        }
        val result = runCatching {
            AutomationHttpClient(config).checkAppUpdate(BuildConfig.VERSION_CODE)
        }.getOrElse { error ->
            _uiState.value = AppUpdateUiState(
                stage = if (silent) AppUpdateStage.IDLE else AppUpdateStage.FAILED,
                message = if (silent) null else (error.message ?: "检测失败，请检查网络"),
            )
            return
        }
        val update = result.update
        if (!result.updateAvailable || update == null) {
            _uiState.value = AppUpdateUiState(
                stage = if (silent) AppUpdateStage.IDLE else AppUpdateStage.UP_TO_DATE,
                message = if (silent) null else "已是最新版本",
            )
            return
        }
        _uiState.value = AppUpdateUiState(
            stage = AppUpdateStage.READY,
            update = update,
            forceLocked = AppUpdatePolicy.effectiveForce(update.forceUpdate, taskDeferred),
            totalBytes = update.fileSize,
        )
    }

    private suspend fun downloadAndInstall(context: Context, update: AppUpdateInfo) {
        _uiState.value = _uiState.value.copy(
            stage = AppUpdateStage.DOWNLOADING,
            progressBytes = 0L,
            totalBytes = update.fileSize,
            message = null,
        )
        val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable)
        if (config == null) {
            _uiState.value = _uiState.value.copy(stage = AppUpdateStage.FAILED, message = "未登录，无法下载更新")
            return
        }
        val apkFile = File(File(context.cacheDir, "updates").apply { mkdirs() }, "${update.versionCode}.apk")
        val downloaded = runCatching {
            if (apkFile.isFile && apkFile.length() == update.fileSize && sha256(apkFile).equals(update.sha256, ignoreCase = true)) {
                apkFile
            } else {
                AutomationHttpClient(
                    config = config,
                    readTimeoutMillis = DOWNLOAD_READ_TIMEOUT_MILLIS,
                ).downloadAppUpdate(update.versionCode, apkFile) { written, reportedTotal ->
                    val total = when {
                        update.fileSize > 0L -> update.fileSize
                        reportedTotal > 0L -> reportedTotal
                        else -> written
                    }
                    _uiState.value = _uiState.value.copy(progressBytes = written, totalBytes = total)
                }
            }
        }.getOrElse { error ->
            apkFile.delete()
            _uiState.value = _uiState.value.copy(
                stage = AppUpdateStage.FAILED,
                message = error.message ?: "下载失败，请稍后重试",
            )
            return
        }
        if (!sha256(downloaded).equals(update.sha256, ignoreCase = true)) {
            downloaded.delete()
            _uiState.value = _uiState.value.copy(stage = AppUpdateStage.FAILED, message = "安装包校验失败，请重新下载")
            return
        }
        _uiState.value = _uiState.value.copy(stage = AppUpdateStage.INSTALLING, progressBytes = downloaded.length())
        val launched = AppUpdateInstaller.install(context, downloaded)
        if (!launched) {
            _uiState.value = _uiState.value.copy(
                stage = AppUpdateStage.READY,
                update = update,
                forceLocked = _uiState.value.forceLocked,
                message = "请允许安装未知应用后，再点击立即更新",
            )
        }
    }

    private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 5 * 60 * 1000
}

internal fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun sha256(file: File): String = sha256File(file)

object AppUpdateInstaller {
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
