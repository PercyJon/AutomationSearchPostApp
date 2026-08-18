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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AutomationCommand
import com.example.douyinautomation.automation.AutomationPhase
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthConfig
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.LicenseStatus
import com.example.douyinautomation.automation.LocalSearchPresetRepository
import com.example.douyinautomation.automation.QueryComposer
import com.example.douyinautomation.automation.RemoteTask
import com.example.douyinautomation.automation.RemoteTaskResume
import com.example.douyinautomation.automation.RemoteTaskResumePolicy
import com.example.douyinautomation.automation.SearchPreset
import com.example.douyinautomation.automation.SearchPresetCatalog
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode
import com.example.douyinautomation.automation.TaskHistoryEntry
import com.example.douyinautomation.automation.TaskRunStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.PasswordVisualTransformation

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
    val licenseState by AuthStore.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val builtInCatalog = remember {
        SearchPresetCatalog(
            version = LocalSearchPresetRepository.BUILT_IN_VERSION,
            items = LocalSearchPresetRepository.BUILT_IN_PRESETS,
            updatedAtMillis = 0L,
            source = SearchPresetCatalog.Source.BUILT_IN,
        )
    }
    var presetCatalog by remember { mutableStateOf(builtInCatalog) }
    var remoteTasks by remember { mutableStateOf<List<RemoteTask>>(emptyList()) }
    var remoteStartingTaskId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(context) {
        presetCatalog = runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadSearchPresets(context) }
        }.getOrElse { builtInCatalog }
    }
    LaunchedEffect(context, licenseState.status) {
        remoteTasks = runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadRemoteTasks(context) }
        }.getOrDefault(emptyList())
    }
    val presets = presetCatalog.items
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

        if (remoteTasks.isNotEmpty()) {
            RemoteTaskCard(
                tasks = remoteTasks,
                serviceConnected = state.serviceConnected,
                startingTaskId = remoteStartingTaskId,
                onStart = { remoteTask ->
                    scope.launch {
                        remoteStartingTaskId = remoteTask.id
                        val session = runCatching {
                            withContext(Dispatchers.IO) {
                                AuthStore.claimRemoteTask(context, remoteTask.id)
                            }
                        }.getOrElse { error ->
                            AutomationStore.publishRemoteSyncError(
                                "远程任务 #${remoteTask.id} 领取/读取进度失败：${error.message ?: "网络异常"}",
                            )
                            null
                        }
                        if (session != null) {
                            val progress = session.progress
                            if (!RemoteTaskResumePolicy.canResumeExactly(progress)) {
                                AutomationStore.publishRemoteSyncError(
                                    "远程任务 #${remoteTask.id} 已有 ${progress.processedCount} 条进度，但缺少最后用户锚点，已暂停以避免重复处理",
                                )
                            } else {
                                AutomationStore.send(
                                    AutomationCommand.Start(
                                        keyword = session.task.keyword,
                                        // Remote "send" tasks remain in the M2 blank-message safety
                                        // mode until a separate, explicit confirmation flow exists.
                                        message = "",
                                        safetyProbe = true,
                                        taskSnapshot = session.task.toTaskSnapshot(),
                                        remoteResume = RemoteTaskResume(session.task.id, progress),
                                    ),
                                )
                            }
                        }
                        remoteStartingTaskId = null
                    }
                },
            )
        }

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
private fun RemoteTaskCard(
    tasks: List<RemoteTask>,
    serviceConnected: Boolean,
    startingTaskId: Long?,
    onStart: (RemoteTask) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("远程任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "来自后台的待执行任务。领取后仍由本地无障碍状态机执行，网络同步在后台完成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tasks.take(5).forEach { task ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "#${task.id} · ${task.keyword} · 已处理 ${task.processedCount}/${task.maxUsers.takeIf { it > 0 } ?: "不限"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onStart(task) },
                        enabled = serviceConnected && startingTaskId == null,
                    ) { Text(if (startingTaskId == task.id) "读取中" else "领取") }
                }
            }
        }
    }
}

private fun RemoteTask.toTaskSnapshot(): com.example.douyinautomation.automation.TaskSnapshot =
    com.example.douyinautomation.automation.TaskSnapshot(
        taskId = id.toString(),
        taskName = name,
        presetVersion = catalogVersion.toString(),
        baseKeywords = listOf(keyword),
        region = regionName.orEmpty(),
        composedQueries = listOf(keyword),
        normalizedBlockedKeywords = emptyList(),
        maxUsers = maxUsers.takeIf { it > 0 } ?: TaskDraft.DEFAULT_MAX_USERS,
        messageTemplate = message.takeIf { it.isNotBlank() },
        // Never turn a server task into an automatic real-message send in this milestone.
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
        createdAtMillis = System.currentTimeMillis(),
    )

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
            state.taskName?.takeIf(String::isNotBlank)?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            Text(phaseLabel(state.phase), fontWeight = FontWeight.SemiBold)
            state.remoteTaskId?.let { remoteId ->
                Text(
                    "远程任务 #$remoteId · 待同步 ${state.remoteSyncPendingCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.remoteSyncLastError?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("已处理 ${state.taskHandledUserCount}")
                Text("已跳过 ${state.taskDuplicateUserCount}")
            }
            if (state.taskQueryCount > 0 || state.taskMaxUsers != null) {
                Text(
                    "搜索词 ${state.taskQueryIndex + 1}/${state.taskQueryCount.coerceAtLeast(1)} · 上限 ${state.taskMaxUsers ?: "未设置"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.savedTaskAvailable && state.phase in setOf(
                    AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
                    AutomationPhase.STOPPED,
                    AutomationPhase.FAILED,
                    AutomationPhase.IDLE,
                )
            ) {
                Text(
                    "已保存检查点：${state.savedTaskName.orEmpty()} · 搜索词 ${state.savedTaskQueryIndex + 1}/${state.savedTaskQueryCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { AutomationStore.send(AutomationCommand.ResumeSavedTask) }) {
                    Text("从检查点继续")
                }
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
        if (state.taskHistory.isEmpty() && state.taskRecords.isEmpty()) {
            Text("暂无任务记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.taskHistory.asReversed().forEach { history ->
                TaskHistoryCard(history)
            }
            if (state.taskRecords.isNotEmpty()) {
                Text("当前任务明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
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
private fun TaskHistoryCard(history: TaskHistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(history.taskName, fontWeight = FontWeight.SemiBold)
            Text(
                "${taskStatusLabel(history.status)} · ${formatTaskTime(history.updatedAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("搜索词 ${history.queryCount} · 已处理 ${history.handledCount} · 跳过 ${history.skippedCount}")
            if (history.filteredCount > 0 || history.duplicateCount > 0) {
                Text(
                    "屏蔽 ${history.filteredCount} · 重复 ${history.duplicateCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun taskStatusLabel(status: TaskRunStatus): String = when (status) {
    TaskRunStatus.RUNNING -> "执行中"
    TaskRunStatus.PAUSED -> "已暂停"
    TaskRunStatus.STOPPED -> "已停止"
    TaskRunStatus.COMPLETED -> "已完成"
    TaskRunStatus.FAILED -> "执行失败"
}

private fun formatTaskTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    onOpenDiagnostics: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseState by AuthStore.uiState.collectAsState()
    val existingConfig = remember { AuthStore.currentConfig() }
    val defaultDeviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }
    var endpoint by rememberSaveable { mutableStateOf(existingConfig?.endpoint.orEmpty()) }
    var licenseToken by rememberSaveable { mutableStateOf(existingConfig?.licenseToken.orEmpty()) }
    var deviceId by rememberSaveable { mutableStateOf(existingConfig?.deviceId ?: defaultDeviceId) }
    var configMessage by rememberSaveable { mutableStateOf<String?>(null) }
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
                Text("授权与连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("状态：${licenseStatusLabel(licenseState.status)}")
                Text(
                    licenseState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("后端地址（HTTPS）") },
                    placeholder = { Text("例如 https://api.example.com") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = licenseToken,
                    onValueChange = { licenseToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("授权 Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备标识（默认 Android ID）") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val saved = AuthStore.saveConfig(
                            context,
                            AuthConfig(
                                endpoint = endpoint.trim().trimEnd('/'),
                                licenseToken = licenseToken.trim(),
                                deviceId = deviceId.trim(),
                            ),
                        )
                        configMessage = if (saved) {
                            "授权配置已加密保存"
                        } else {
                            "配置无效：后端地址必须使用 HTTPS，且三项均不能为空"
                        }
                        if (saved) AuthStore.verifyNow()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存并验证") }
                configMessage?.let {
                    Text(it, color = if (it.startsWith("授权")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = { AuthStore.verifyNow() }) {
                    Text("立即验证 heartbeat")
                }
                Text(
                    "Token 使用 Android Keystore 加密保存；请求仅携带 Bearer 授权，不会写入 Logcat。未配置后端时仍可使用本地安全探测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设备与服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("目标应用：抖音\n执行方式：Android 无障碍服务\n安全模式：空白消息探测")
                Text(
                    "预设搜索词：远程优先，失败时使用本地缓存和内置词；地区规则、屏蔽词与任务断点接口已接入客户端网关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun licenseStatusLabel(status: LicenseStatus): String = when (status) {
    LicenseStatus.NOT_CONFIGURED -> "未配置"
    LicenseStatus.VERIFIED -> "已验证"
    LicenseStatus.REJECTED -> "授权拒绝"
    LicenseStatus.TEMPORARILY_UNAVAILABLE -> "暂时不可用"
}

private fun phaseLabel(phase: AutomationPhase): String = when (phase) {
    AutomationPhase.IDLE, AutomationPhase.SERVICE_READY -> "准备执行"
    AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> "需要人工处理"
    AutomationPhase.STOPPED -> "已停止"
    AutomationPhase.FAILED -> "执行失败"
    AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE -> "安全探测完成"
    AutomationPhase.COMPLETED_TASK -> "任务已完成"
    AutomationPhase.COMPLETED_AT_MESSAGE_PAGE -> "已进入私信页"
    else -> "执行中 · ${phase.name}"
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
