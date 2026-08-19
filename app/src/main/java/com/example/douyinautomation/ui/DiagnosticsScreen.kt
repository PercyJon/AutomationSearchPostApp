package com.example.douyinautomation.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AutomationCommand
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationErrorSurface
import com.example.douyinautomation.ui.theme.AutomationPage
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationWarning

/**
 * An on-device control surface for the M0 proof of concept.
 *
 * It intentionally exposes diagnostics rather than trying to conceal failures: an operator can
 * inspect the current page, node dump, screenshot and OCR result before accepting the next step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    initialKeyword: String = "",
    onBack: (() -> Unit)? = null,
) {
    val state by AutomationStore.uiState.collectAsState()
    val context = LocalContext.current
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }

    LazyColumn(
        modifier = modifier.background(AutomationPage),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TopAppBar(
                navigationIcon = {
                    onBack?.let { back ->
                        androidx.compose.material3.TextButton(onClick = back) {
                            Text("返回工作台")
                        }
                    }
                },
                title = {
                    Column {
                        Text("开发诊断")
                        Text(
                            text = "截图、OCR、节点树与运行日志",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AutomationPage),
            )
        }

        item {
            ServiceStatusCard(
                connected = state.serviceConnected,
                statusKnown = state.serviceStatusKnown,
                onOpenSettings = { openAccessibilitySettings(context) },
            )
        }

        if (state.awaitingManualHandoff) {
            item {
                ManualHandoffCard(reason = state.lastError)
            }
        }

        item {
            TaskControlCard(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                keywordPresets = DEFAULT_TEST_KEYWORDS,
                onStart = {
                    val normalizedKeyword = keyword.trim()
                    if (normalizedKeyword.isNotEmpty()) {
                        AutomationStore.send(
                            AutomationCommand.Start(
                                keyword = normalizedKeyword,
                                // M2 is a non-delivery safety probe: the service submits one
                                // space and expects Douyin's “不能发送空白消息” notice.
                                message = "",
                                safetyProbe = true,
                            ),
                        )
                    }
                },
                onPause = { AutomationStore.send(AutomationCommand.Pause) },
                onResume = { AutomationStore.send(AutomationCommand.Resume) },
                onStop = { AutomationStore.send(AutomationCommand.Stop) },
            )
        }

        item {
            DiagnosticsActionsCard(
                onCapture = { AutomationStore.send(AutomationCommand.CaptureDiagnostics) },
                onDumpNodeTree = { AutomationStore.send(AutomationCommand.DumpNodeTree) },
            )
        }

        item {
            StateCard(
                phase = state.phase,
                lastPage = state.lastPage,
                lastNodeDumpPath = state.lastNodeDumpPath,
                lastScreenshotPath = state.lastScreenshotPath,
                lastOcrText = state.lastOcrText,
                lastError = state.lastError,
                taskId = state.taskId,
                taskHandledUserCount = state.taskHandledUserCount,
                taskDuplicateUserCount = state.taskDuplicateUserCount,
                taskLastEvent = state.taskLastEvent,
            )
        }

        item {
            Text(
                text = "诊断日志",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.diagnosticEntries.isEmpty()) {
            item {
                Text(
                    text = "暂无诊断事件。开启无障碍服务后，可在这里采集诊断信息。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.diagnosticEntries.takeLast(MAX_VISIBLE_LOGS).asReversed()) { entry ->
                LogEntryCard(
                    text = entry.toString(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(
    connected: Boolean,
    statusKnown: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    !statusKnown -> "正在检查无障碍服务…"
                    connected -> "无障碍服务已连接"
                    else -> "无障碍服务未连接"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    !statusKnown -> MaterialTheme.colorScheme.onSurface
                    connected -> AutomationSuccess
                    else -> AutomationError
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !statusKnown -> "正在检查 Android 无障碍服务状态。"
                    connected -> {
                    "可以检查当前窗口，并执行明确请求的诊断操作。"
                    }
                    else -> {
                    "请先在 Android 无障碍设置中开启“抖音自动化诊断”。"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text("打开无障碍设置")
            }
        }
    }
}

@Composable
private fun ManualHandoffCard(reason: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AutomationErrorSurface,
            contentColor = AutomationError,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("需要人工处理", fontWeight = FontWeight.Bold)
            Text(
                text = "暂停原因：${reason.orEmpty().ifBlank { "未提供" }}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "自动化因验证、风控提示或其他不明确状态已暂停。请人工完成或关闭提示，再检查诊断信息后继续。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TaskControlCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    keywordPresets: List<String>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("引导式 POC 流程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "M2 安全探测模式：不会发送真实文案。进入私信页后只提交一个空格；检测到“不能发送空白消息”后，视为当前用户验证成功并继续下一位。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索关键词") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Text(
                text = "测试关键词预设",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                keywordPresets.forEach { preset ->
                    AssistChip(
                        onClick = { onKeywordChange(preset) },
                        label = { Text(preset) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStart,
                    enabled = keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开始测试")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("暂停")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("继续")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("停止")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsActionsCard(
    onCapture: () -> Unit,
    onDumpNodeTree: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("采集诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "采集服务截图并执行 OCR，同时导出当前无障碍节点树用于选择器检查。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onCapture, modifier = Modifier.weight(1f)) {
                    Text("截图 + OCR")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDumpNodeTree, modifier = Modifier.weight(1f)) {
                    Text("导出节点树")
                }
            }
        }
    }
}

@Composable
private fun StateCard(
    phase: Any?,
    lastPage: Any?,
    lastNodeDumpPath: Any?,
    lastScreenshotPath: Any?,
    lastOcrText: Any?,
    lastError: Any?,
    taskId: Any?,
    taskHandledUserCount: Any?,
    taskDuplicateUserCount: Any?,
    taskLastEvent: Any?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("最新状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DetailRow("任务 ID", taskId)
            DetailRow("已处理用户", taskHandledUserCount)
            DetailRow("跳过重复", taskDuplicateUserCount)
            DetailRow("最近事件", taskLastEvent)
            DetailRow("阶段", phase)
            DetailRow("检测页面", lastPage)
            DetailRow("节点树", lastNodeDumpPath)
            DetailRow("截图", lastScreenshotPath)
            DetailRow("OCR", lastOcrText)
            DetailRow("错误", lastError)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: Any?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(104.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value?.toString().orEmpty().ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogEntryCard(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private const val MAX_VISIBLE_LOGS = 100
private val DEFAULT_TEST_KEYWORDS = listOf(
    "红木沙发",
    "是小瑜瑜呀~",
    "实木餐桌",
    "茶桌",
)
