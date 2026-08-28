package com.example.douyinautomation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AppUpdateController
import com.example.douyinautomation.automation.AppUpdateStage
import com.example.douyinautomation.automation.AppUpdateUiState
import com.example.douyinautomation.ui.components.AppButtonRow
import com.example.douyinautomation.ui.components.AppPrimaryButton
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationSpacing

@Composable
internal fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val visible = state.stage in setOf(
        AppUpdateStage.READY,
        AppUpdateStage.DOWNLOADING,
        AppUpdateStage.INSTALLING,
        AppUpdateStage.FAILED,
        AppUpdateStage.UP_TO_DATE,
    )
    if (!visible) return
    val force = state.forceLocked
    val update = state.update
    BackHandler(enabled = force) { }
    AlertDialog(
        onDismissRequest = { if (!force) onDismiss() },
        title = {
            Text(
                when {
                    state.stage == AppUpdateStage.UP_TO_DATE -> "检测更新"
                    state.stage == AppUpdateStage.FAILED && update == null -> "检测更新"
                    else -> update?.title?.ifBlank { "发现新版本" } ?: "发现新版本"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
            ) {
                when (state.stage) {
                    AppUpdateStage.UP_TO_DATE -> Text("已是最新版本")
                    AppUpdateStage.FAILED -> Text(state.message ?: "操作失败，请稍后重试")
                    AppUpdateStage.DOWNLOADING, AppUpdateStage.INSTALLING -> {
                        Text(if (state.stage == AppUpdateStage.INSTALLING) "正在打开安装程序…" else "正在下载新版本")
                        LinearProgressIndicator(
                            progress = { state.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AutomationBlue,
                        )
                        Text(
                            formatDownloadProgress(state.progressBytes, state.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        update?.let {
                            Text("版本 ${it.versionName}")
                            if (it.releaseNotes.isNotBlank()) {
                                Text(it.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            when (state.stage) {
                AppUpdateStage.UP_TO_DATE -> {
                    AppPrimaryButton(text = "确定", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
                AppUpdateStage.FAILED -> {
                    if (update != null) {
                        AppPrimaryButton(text = "重试", onClick = onUpdate, modifier = Modifier.fillMaxWidth())
                    } else {
                        AppPrimaryButton(text = "确定", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                    }
                }
                AppUpdateStage.DOWNLOADING, AppUpdateStage.INSTALLING -> {
                    AppPrimaryButton(
                        text = if (state.stage == AppUpdateStage.INSTALLING) "安装中…" else "下载中…",
                        onClick = {},
                        enabled = false,
                        loading = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    if (force) {
                        AppPrimaryButton(
                            text = "立即更新",
                            onClick = onUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AppButtonRow(
                            secondaryText = "取消",
                            onSecondary = onDismiss,
                            primaryText = "立即更新",
                            onPrimary = onUpdate,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun AppUpdateHost() {
    val state by AppUpdateController.uiState.collectAsState()
    val context = LocalContext.current
    AppUpdateDialog(
        state = state,
        onDismiss = { AppUpdateController.dismiss() },
        onUpdate = { AppUpdateController.startDownload(context) },
    )
}

internal fun formatDownloadProgress(written: Long, total: Long): String {
    fun mb(value: Long): String = "%.1f MB".format(value / (1024f * 1024f))
    return if (total > 0L) "${mb(written)} / ${mb(total)}" else mb(written)
}
