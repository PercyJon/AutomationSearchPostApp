package com.example.douyinautomation.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AutomationCommand
import com.example.douyinautomation.automation.AutomationPhase
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.LocalSearchPresetRepository
import com.example.douyinautomation.automation.QueryComposer
import com.example.douyinautomation.automation.SearchPreset
import com.example.douyinautomation.automation.SearchPresetCatalog
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode

private enum class HomeSection {
    TASKS,
    RECORDS,
    SETTINGS,
    DIAGNOSTICS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    initialKeyword: String = "",
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.TASKS.name) }
    val selectedSection = HomeSection.valueOf(section)

    if (selectedSection == HomeSection.DIAGNOSTICS) {
        DiagnosticsScreen(onBack = { section = HomeSection.SETTINGS.name })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("自动化任务", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when (selectedSection) {
                                HomeSection.TASKS -> "任务工作台"
                                HomeSection.RECORDS -> "处理记录"
                                HomeSection.SETTINGS -> "设置"
                                HomeSection.DIAGNOSTICS -> "诊断"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedSection == HomeSection.TASKS,
                    onClick = { section = HomeSection.TASKS.name },
                    icon = { Text("任务") },
                    label = null,
                )
                NavigationBarItem(
                    selected = selectedSection == HomeSection.RECORDS,
                    onClick = { section = HomeSection.RECORDS.name },
                    icon = { Text("记录") },
                    label = null,
                )
                NavigationBarItem(
                    selected = selectedSection == HomeSection.SETTINGS,
                    onClick = { section = HomeSection.SETTINGS.name },
                    icon = { Text("设置") },
                    label = null,
                )
            }
        },
    ) { padding ->
        when (selectedSection) {
            HomeSection.TASKS -> TaskDashboard(
                padding = padding,
                initialKeyword = initialKeyword,
            )

            HomeSection.RECORDS -> TaskRecordsPage(padding)
            HomeSection.SETTINGS -> SettingsPage(
                padding = padding,
                onOpenDiagnostics = { section = HomeSection.DIAGNOSTICS.name },
            )

            HomeSection.DIAGNOSTICS -> Unit
        }
    }
}

@Composable
private fun TaskDashboard(
    padding: PaddingValues,
    initialKeyword: String,
) {
    val state by AutomationStore.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val presets = remember { LocalSearchPresetRepository.BUILT_IN_PRESETS }
    val presetCatalog = remember {
        SearchPresetCatalog(
            version = LocalSearchPresetRepository.BUILT_IN_VERSION,
            items = presets,
            updatedAtMillis = 0L,
            source = SearchPresetCatalog.Source.BUILT_IN,
        )
    }
    var taskName by rememberSaveable { mutableStateOf("红木客户筛选") }
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }
    var region by rememberSaveable { mutableStateOf("") }
    var blockedKeywords by rememberSaveable { mutableStateOf("") }
    var maxUsers by rememberSaveable { mutableStateOf(TaskDraft.DEFAULT_MAX_USERS.toString()) }
    var selectedPresetIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val selectedPresetKeywords = presets
        .filter { it.id in selectedPresetIds }
        .map(SearchPreset::keyword)
    val baseKeywords = selectedPresetKeywords + keyword
    val queries = QueryComposer.composeAll(region, baseKeywords)
    val draft = TaskDraft(
        id = "preview",
        name = taskName,
        presetIds = selectedPresetIds.toList(),
        customKeywords = listOf(keyword),
        region = region,
        blockedKeywords = blockedKeywords.split(',', '，', '\n'),
        maxUsers = maxUsers.toIntOrNull() ?: TaskDraft.DEFAULT_MAX_USERS,
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
    )
    val draftErrors = draft.validationErrors()
    val taskSnapshot = runCatching {
        draft.toSnapshot(
            presets = presetCatalog,
            nowMillis = System.currentTimeMillis(),
        )
    }.getOrNull()
    val canStart = state.serviceConnected && queries.isNotEmpty() && draftErrors.isEmpty()

    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceStatusBanner(
            connected = state.serviceConnected,
            onOpenSettings = { openAccessibilitySettings(context) },
        )

        CurrentTaskCard(state = state)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("新建任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "配置搜索词、地区和屏蔽规则。当前默认使用空格安全探测，不发送真实消息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任务名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义搜索词") },
                    singleLine = true,
                )
                Text("预设搜索词", style = MaterialTheme.typography.labelLarge)
                PresetChips(
                    presets = presets,
                    selectedIds = selectedPresetIds,
                    onToggle = { id ->
                        selectedPresetIds = if (id in selectedPresetIds) {
                            selectedPresetIds - id
                        } else {
                            selectedPresetIds + id
                        }
                    },
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地区（可选，例如广东）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = blockedKeywords,
                    onValueChange = { blockedKeywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("屏蔽关键词（逗号分隔）") },
                    placeholder = { Text("例如：工厂，批发") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxUsers,
                    onValueChange = { maxUsers = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最多处理用户数") },
                    singleLine = true,
                )
                Text("最终搜索词预览", style = MaterialTheme.typography.labelLarge)
                if (queries.isEmpty()) {
                    Text("请先选择预设或输入自定义搜索词", color = MaterialTheme.colorScheme.error)
                } else {
                    queries.forEach { query ->
                        Text("• ${query.query}", fontWeight = FontWeight.Medium)
                    }
                }
                if (draftErrors.isNotEmpty()) {
                    Text(draftErrors.first(), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        // M3-A keeps the existing controller contract: execute the first preview
                        // query while the multi-query task runner is introduced in M3-C.
                        AutomationStore.send(
                            AutomationCommand.Start(
                                keyword = queries.firstOrNull()?.query.orEmpty(),
                                safetyProbe = true,
                                taskSnapshot = taskSnapshot,
                            ),
                        )
                    },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.phase == AutomationPhase.IDLE) "开始任务" else "启动新的任务")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { AutomationStore.send(AutomationCommand.Pause) },
                        modifier = Modifier.weight(1f),
                    ) { Text("暂停") }
                    FilledTonalButton(
                        onClick = { AutomationStore.send(AutomationCommand.Resume) },
                        modifier = Modifier.weight(1f),
                    ) { Text("继续") }
                    OutlinedButton(
                        onClick = { AutomationStore.send(AutomationCommand.Stop) },
                        modifier = Modifier.weight(1f),
                    ) { Text("停止") }
                }
            }
        }
    }
}

@Composable
private fun PresetChips(
    presets: List<SearchPreset>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id in selectedIds,
                onClick = { onToggle(preset.id) },
                label = { Text(preset.label) },
            )
        }
    }
}

@Composable
private fun ServiceStatusBanner(
    connected: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (connected) "无障碍服务已连接" else "需要开启无障碍服务",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (connected) "可以开始配置任务" else "开启后才能执行自动化任务",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!connected) {
                TextButton(onClick = onOpenSettings) { Text("去开启") }
            }
        }
    }
}

@Composable
private fun CurrentTaskCard(
    state: com.example.douyinautomation.automation.AutomationUiState,
) {
    if (state.taskId == null && state.phase == AutomationPhase.IDLE) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("当前任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(phaseLabel(state.phase), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("已处理 ${state.taskHandledUserCount}")
                Text("已跳过 ${state.taskDuplicateUserCount}")
            }
            state.lastError?.takeIf(String::isNotBlank)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TaskRecordsPage(padding: PaddingValues) {
    val state by AutomationStore.uiState.collectAsState()
    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("处理记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (state.taskRecords.isEmpty()) {
            Text("暂无当前任务记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.taskRecords.asReversed().take(50).forEach { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(record.outcome.name, fontWeight = FontWeight.SemiBold)
                        Text(record.reason.orEmpty().ifBlank { "未记录原因" })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    onOpenDiagnostics: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设备与服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("目标应用：抖音\n执行方式：Android 无障碍服务\n安全模式：空白消息探测")
                OutlinedButton(onClick = { openAccessibilitySettings(context) }) {
                    Text("无障碍服务设置")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开发诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("截图、OCR、节点树和原始事件仅在诊断页显示。")
                OutlinedButton(onClick = onOpenDiagnostics) { Text("打开诊断页") }
            }
        }
    }
}

private fun phaseLabel(phase: AutomationPhase): String = when (phase) {
    AutomationPhase.IDLE, AutomationPhase.SERVICE_READY -> "准备执行"
    AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> "需要人工处理"
    AutomationPhase.STOPPED -> "已停止"
    AutomationPhase.FAILED -> "执行失败"
    AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE -> "安全探测完成"
    AutomationPhase.COMPLETED_AT_MESSAGE_PAGE -> "已进入私信页"
    else -> "执行中 · ${phase.name}"
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
