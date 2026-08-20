package com.example.douyinautomation.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import com.example.douyinautomation.automation.RemoteTaskVisibilityStore
import com.example.douyinautomation.automation.RegionCatalog
import com.example.douyinautomation.automation.BlockKeywordCatalog
import com.example.douyinautomation.automation.PageKind
import com.example.douyinautomation.automation.ProfileDisplayNameResolver
import com.example.douyinautomation.automation.SearchPreset
import com.example.douyinautomation.automation.SearchPresetCatalog
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode
import com.example.douyinautomation.automation.TaskHistoryEntry
import com.example.douyinautomation.automation.TaskRunStatus
import com.example.douyinautomation.automation.UserTaskRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueDark
import com.example.douyinautomation.ui.theme.AutomationBlueLight
import com.example.douyinautomation.ui.theme.AutomationBlueSurface
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationErrorSurface
import com.example.douyinautomation.ui.theme.AutomationNeutralSurface
import com.example.douyinautomation.ui.theme.AutomationPage
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationSuccessSurface
import com.example.douyinautomation.ui.theme.AutomationWarning
import com.example.douyinautomation.ui.theme.AutomationWarningSurface

private enum class HomeSection {
    HOME,
    TODO,
    RECORDS,
    SETTINGS,
    DIAGNOSTICS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    initialKeyword: String = "",
    initialSection: String? = null,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection ?: HomeSection.HOME.name) }
    var detailTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRemoteTasks by remember(context) {
        mutableStateOf(RemoteTaskVisibilityStore.isEnabled(context))
    }
    LaunchedEffect(initialSection) {
        initialSection?.let { section = it }
    }
    val selectedSection = HomeSection.valueOf(section)

    detailTaskId?.let { taskId ->
        TaskRecordDetailScreen(
            taskId = taskId,
            onBack = { detailTaskId = null },
            onReuse = {
                detailTaskId = null
                section = HomeSection.TODO.name
            },
            onRetry = {
                detailTaskId = null
                section = HomeSection.TODO.name
            },
        )
        return
    }

    if (selectedSection == HomeSection.DIAGNOSTICS) {
        DiagnosticsScreen(onBack = { section = HomeSection.SETTINGS.name })
        return
    }

    Scaffold(
        containerColor = AutomationPage,
        topBar = {
            if (showCreateTask || selectedSection == HomeSection.RECORDS || selectedSection == HomeSection.SETTINGS || selectedSection == HomeSection.DIAGNOSTICS) {
                TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                showCreateTask -> "新建任务"
                                selectedSection == HomeSection.TODO -> "待办"
                                selectedSection == HomeSection.RECORDS -> "记录"
                                selectedSection == HomeSection.SETTINGS -> "设置"
                                else -> "诊断"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = when (selectedSection) {
                                HomeSection.HOME -> ""
                                HomeSection.TODO -> "保存的任务按顺序执行"
                                HomeSection.RECORDS -> "处理记录"
                                HomeSection.SETTINGS -> "设置"
                                HomeSection.DIAGNOSTICS -> "诊断"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (showCreateTask) {
                        IconButton(onClick = { showCreateTask = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回首页")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AutomationPage,
                    scrolledContainerColor = AutomationPage,
                ),
                )
            }
        },
        bottomBar = {
            if (!showCreateTask) {
                NavigationBar(containerColor = AutomationCard, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = selectedSection == HomeSection.HOME,
                        onClick = { section = HomeSection.HOME.name },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = selectedSection == HomeSection.TODO,
                        onClick = { section = HomeSection.TODO.name },
                        icon = { Icon(Icons.Default.ListAlt, contentDescription = null) },
                        label = { Text("待办") },
                    )
                    NavigationBarItem(
                        selected = selectedSection == HomeSection.RECORDS,
                        onClick = { section = HomeSection.RECORDS.name },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("记录") },
                    )
                    NavigationBarItem(
                        selected = selectedSection == HomeSection.SETTINGS,
                        onClick = { section = HomeSection.SETTINGS.name },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedSection) {
            HomeSection.HOME -> TaskDashboard(
                padding = padding,
                initialKeyword = initialKeyword,
                showCreateTask = showCreateTask,
                showRemoteTasks = showRemoteTasks,
                showTodoOnly = false,
                onCloseCreateTask = { showCreateTask = false },
                onOpenCreateTask = { showCreateTask = true },
                onOpenTodo = { section = HomeSection.TODO.name },
            )
            HomeSection.TODO -> TaskDashboard(
                padding = padding,
                initialKeyword = initialKeyword,
                showCreateTask = showCreateTask,
                showRemoteTasks = showRemoteTasks,
                showTodoOnly = true,
                onCloseCreateTask = { showCreateTask = false },
                onOpenCreateTask = { showCreateTask = true },
                onOpenTodo = { section = HomeSection.TODO.name },
            )

            HomeSection.RECORDS -> TaskRecordsPage(
                padding = padding,
                onOpenTask = { taskId -> detailTaskId = taskId },
            )
            HomeSection.SETTINGS -> SettingsPage(
                padding = padding,
                showRemoteTasks = showRemoteTasks,
                onShowRemoteTasksChange = { enabled ->
                    showRemoteTasks = enabled
                    RemoteTaskVisibilityStore.setEnabled(context, enabled)
                },
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
    showCreateTask: Boolean,
    showRemoteTasks: Boolean,
    showTodoOnly: Boolean,
    onCloseCreateTask: () -> Unit,
    onOpenCreateTask: () -> Unit,
    onOpenTodo: () -> Unit,
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
    var regionCatalog by remember { mutableStateOf(RegionCatalog("local-empty", emptyList(), null)) }
    var blockKeywordCatalog by remember { mutableStateOf(BlockKeywordCatalog("local-empty", emptyList(), null)) }
    var remoteTasks by remember { mutableStateOf<List<RemoteTask>>(emptyList()) }
    var remoteStartingTaskId by remember { mutableStateOf<Long?>(null) }
    var remoteRefreshNonce by remember { mutableStateOf(0) }
    var remoteRefreshInFlight by remember { mutableStateOf(false) }
    var remoteRefreshAtMillis by remember { mutableStateOf<Long?>(null) }
    var remoteRefreshMessage by remember { mutableStateOf<String?>(null) }
    var savedTasks by remember { mutableStateOf<List<TaskDraft>>(emptyList()) }
    var selectedSavedTaskIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(context) {
        savedTasks = withContext(Dispatchers.IO) { AutomationStore.loadSavedTasks() }
        presetCatalog = runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadSearchPresets(context) }
        }.getOrElse { builtInCatalog }
        regionCatalog = runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadRegionCatalog(context) }
        }.getOrDefault(regionCatalog)
        blockKeywordCatalog = runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadBlockKeywordCatalog(context) }
        }.getOrDefault(blockKeywordCatalog)
    }
    LaunchedEffect(context, licenseState.status, remoteRefreshNonce, showRemoteTasks) {
        if (!showRemoteTasks) {
            remoteTasks = emptyList()
            remoteRefreshInFlight = false
            remoteRefreshAtMillis = null
            remoteRefreshMessage = null
            return@LaunchedEffect
        }
        remoteRefreshInFlight = true
        remoteRefreshMessage = null
        runCatching {
            withContext(Dispatchers.IO) { AuthStore.loadRemoteTasks(context) }
        }.onSuccess { tasks ->
            remoteTasks = tasks
            remoteRefreshAtMillis = System.currentTimeMillis()
        }.onFailure { error ->
            if (error is CancellationException) return@onFailure
            remoteRefreshMessage = "远程任务刷新失败：${error.message ?: "网络异常"}"
            AutomationStore.publishRemoteSyncError(remoteRefreshMessage.orEmpty())
        }
        remoteRefreshInFlight = false
    }
    LaunchedEffect(context, licenseState.status, showRemoteTasks) {
        if (!showRemoteTasks) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            remoteRefreshNonce += 1
        }
    }
    val presets = presetCatalog.items
    var taskName by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }
    var region by rememberSaveable { mutableStateOf("") }
    var blockedKeywords by rememberSaveable { mutableStateOf("") }
    var maxUsers by rememberSaveable { mutableStateOf(TaskDraft.DEFAULT_MAX_USERS.toString()) }
    var selectedPresetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draftHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(context, initialKeyword) {
        val savedDraft = withContext(Dispatchers.IO) { AutomationStore.loadTaskDraft() }
        if (savedDraft != null && initialKeyword.isBlank()) {
            taskName = savedDraft.name
            keyword = savedDraft.customKeywords.firstOrNull().orEmpty()
            region = savedDraft.region.orEmpty()
            blockedKeywords = savedDraft.blockedKeywords.joinToString(",")
            maxUsers = savedDraft.maxUsers.toString()
            selectedPresetIds = savedDraft.presetIds.toSet()
        }
        draftHydrated = true
    }

    LaunchedEffect(taskName, keyword, region, blockedKeywords, maxUsers, selectedPresetIds, draftHydrated) {
        if (draftHydrated) {
            withContext(Dispatchers.IO) {
                AutomationStore.saveTaskDraft(
                    TaskDraft(
                        id = "draft",
                        name = taskName,
                        presetIds = selectedPresetIds.toList(),
                        customKeywords = listOf(keyword),
                        region = region,
                        blockedKeywords = blockedKeywords.split(',', '，', '\n'),
                        maxUsers = maxUsers.toIntOrNull() ?: TaskDraft.DEFAULT_MAX_USERS,
                        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
                    ),
                )
            }
        }
    }

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
    val canSave = queries.isNotEmpty() && draftErrors.isEmpty()
    val fixedTodoLayout = !showCreateTask && showTodoOnly && !showRemoteTasks

    Column(
        modifier = if (fixedTodoLayout) {
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .fillMaxHeight()
        } else {
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        },
        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Content),
    ) {
        if (!showCreateTask) {
            val toggleTask: (String) -> Unit = { taskId ->
                selectedSavedTaskIds = if (taskId in selectedSavedTaskIds) {
                    selectedSavedTaskIds - taskId
                } else {
                    selectedSavedTaskIds + taskId
                }
            }
            val startSelected: () -> Unit = {
                val snapshots = savedTasks
                    .filter { it.id in selectedSavedTaskIds }
                    .mapNotNull { saved ->
                        runCatching {
                            saved.toSnapshot(
                                presets = presetCatalog,
                                nowMillis = System.currentTimeMillis(),
                            )
                        }.getOrNull()
                    }
                if (snapshots.isNotEmpty()) {
                    AutomationStore.send(AutomationCommand.StartBatch(snapshots))
                    selectedSavedTaskIds = emptySet()
                }
            }
            if (showTodoOnly) {
                if (fixedTodoLayout) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TodoTaskCard(
                            tasks = savedTasks,
                            presetCatalog = presetCatalog,
                            selectedTaskIds = selectedSavedTaskIds,
                            activeState = state,
                            onToggleTask = toggleTask,
                        )
                    }
                    Button(
                        onClick = startSelected,
                        enabled = state.serviceConnected && selectedSavedTaskIds.isNotEmpty() && !isTaskActivePhase(state.phase),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AutomationSpacing.Page, vertical = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("开始任务")
                    }
                } else {
                    TodoTaskCard(
                        tasks = savedTasks,
                        presetCatalog = presetCatalog,
                        selectedTaskIds = selectedSavedTaskIds,
                        activeState = state,
                        onToggleTask = toggleTask,
                    )
                }
            } else {
                HomeDashboardContent(
                    state = state,
                    savedTasks = savedTasks,
                    presetCatalog = presetCatalog,
                    onOpenCreateTask = onOpenCreateTask,
                    onOpenTodo = onOpenTodo,
                )
            }
            if (showTodoOnly && showRemoteTasks && (remoteTasks.isNotEmpty() || licenseState.status != LicenseStatus.NOT_CONFIGURED)) {
                RemoteTaskCard(
                    tasks = remoteTasks,
                    serviceConnected = state.serviceConnected,
                    startingTaskId = remoteStartingTaskId,
                    refreshInFlight = remoteRefreshInFlight,
                    lastRefreshAtMillis = remoteRefreshAtMillis,
                    refreshMessage = remoteRefreshMessage,
                    onRefresh = { remoteRefreshNonce += 1 },
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
            return@Column
        }

        ServiceStatusBanner(
            connected = state.serviceConnected,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AutomationSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                Text("任务配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "配置搜索词、地区和屏蔽规则。默认使用空格安全探测，不发送真实消息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionHeader("基础信息")
                CompactOutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "任务名称（可选）",
                    placeholder = "不填则按搜索词+时间自动生成",
                    singleLine = true,
                )
                CompactOutlinedTextField(
                    value = keyword,
                    onValueChange = { value ->
                        keyword = value
                        val selectedKeyword = presets.firstOrNull { it.id in selectedPresetIds }?.keyword
                        if (selectedKeyword != null && value != selectedKeyword) {
                            selectedPresetIds = emptySet()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "自定义搜索词",
                    placeholder = "输入搜索词，或点击下方预设",
                    singleLine = true,
                )
                SectionHeader("搜索条件")
                Text("预设搜索词（点击后填入上方输入框）", style = MaterialTheme.typography.labelMedium)
                PresetChips(
                    presets = presets,
                    selectedIds = selectedPresetIds,
                    onToggle = { id ->
                        val selected = presets.firstOrNull { it.id == id }
                        if (id in selectedPresetIds) {
                            selectedPresetIds = emptySet()
                            keyword = ""
                        } else {
                            selectedPresetIds = setOf(id)
                            keyword = selected?.keyword.orEmpty()
                        }
                    },
                )
                CompactOutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "地区（可选，例如广东）",
                    singleLine = true,
                )
                SectionHeader("筛选条件")
                if (regionCatalog.items.isNotEmpty()) {
                    Text("后台地区规则", style = MaterialTheme.typography.labelLarge)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        regionCatalog.items.forEach { rule ->
                            CompactPresetChip(
                                selected = region == rule.prefix,
                                onClick = { region = rule.prefix },
                                label = rule.name,
                            )
                        }
                    }
                }
                CompactOutlinedTextField(
                    value = blockedKeywords,
                    onValueChange = { blockedKeywords = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "屏蔽关键词（逗号分隔）",
                    placeholder = "例如：厂，公司",
                    singleLine = true,
                )
                val blockedPresetKeywords = (blockKeywordCatalog.items.map { it.keyword } + listOf("厂", "公司"))
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                if (blockedPresetKeywords.isNotEmpty()) {
                    Text("预设屏蔽词（点击后替换输入框内容）", style = MaterialTheme.typography.labelMedium)
                    val selectedBlocked = blockedKeywords
                        .split(',', '，', '\n')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .toSet()
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        blockedPresetKeywords.forEach { keywordPreset ->
                            CompactPresetChip(
                                selected = keywordPreset in selectedBlocked,
                                onClick = {
                                    blockedKeywords = if (keywordPreset in selectedBlocked) {
                                        ""
                                    } else {
                                        keywordPreset
                                    }
                                },
                                label = keywordPreset,
                            )
                        }
                    }
                }
                SectionHeader("执行限制")
                CompactOutlinedTextField(
                    value = maxUsers,
                    onValueChange = { maxUsers = it.filter(Char::isDigit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "最多处理用户数",
                    singleLine = true,
                )
                if (draftErrors.isNotEmpty()) {
                    Text(draftErrors.first(), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val reusableTask = taskSnapshot?.let { snapshot ->
                                draft.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = snapshot.taskName,
                                )
                            } ?: return@OutlinedButton
                            scope.launch {
                                val stored = withContext(Dispatchers.IO) {
                                    AutomationStore.saveSavedTask(reusableTask)
                                }
                                savedTasks = withContext(Dispatchers.IO) { AutomationStore.loadSavedTasks() }
                                selectedSavedTaskIds = selectedSavedTaskIds - stored.id
                                AutomationStore.clearTaskDraft()
                                // Saving is a completed form action. Start the next new-task
                                // form clean so the operator cannot accidentally save the same
                                // configuration again while preparing the next batch item.
                                taskName = ""
                                keyword = ""
                                region = ""
                                blockedKeywords = ""
                                maxUsers = TaskDraft.DEFAULT_MAX_USERS.toString()
                                selectedPresetIds = emptySet()
                                onCloseCreateTask()
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text("保存")
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
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text("立即开始")
                    }
                }
        }
    }
}

@Composable
private fun HomeDashboardContent(
    state: com.example.douyinautomation.automation.AutomationUiState,
    savedTasks: List<TaskDraft>,
    presetCatalog: SearchPresetCatalog,
    onOpenCreateTask: () -> Unit,
    onOpenTodo: () -> Unit,
) {
    val history = state.taskHistory
    val totalTasks = history.size
    val terminalTasks = history.count { it.status != TaskRunStatus.RUNNING }
    val completion = if (totalTasks == 0) 1f else (terminalTasks.toFloat() / totalTasks).coerceIn(0f, 1f)
    val handledCustomers = history.sumOf { it.handledCount }
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "早上好，欢迎回来"
        in 12..17 -> "下午好，欢迎回来"
        else -> "晚上好，欢迎回来"
    }

    Column(verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Section)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(AutomationBlue),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(greeting, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("自动化获客助手", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 38.dp)
                    .padding(horizontal = AutomationSpacing.Page),
                colors = CardDefaults.cardColors(containerColor = AutomationCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "总览",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Normal,
                        )
                        OverviewMetric(
                            value = totalTasks.toString(),
                            label = "个任务已运行",
                            valueColor = AutomationBlue,
                        )
                        OverviewMetric(
                            value = handledCustomers.toString(),
                            label = "个客户已发送",
                            valueColor = AutomationSuccess,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "任务进展",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                        )
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = completion,
                                modifier = Modifier
                                    .height(64.dp)
                                    .width(64.dp),
                                color = AutomationBlue,
                                trackColor = AutomationBlueLight,
                                strokeWidth = 5.dp,
                            )
                            Text(
                                "${(completion * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AutomationBlue,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        Column(
            modifier = Modifier.padding(horizontal = AutomationSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("功能", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FunctionEntryCard(
                    title = "B端私信",
                    description = "搜索用户并安全探测",
                    icon = Icons.Default.Business,
                    color = AutomationBlue,
                    onClick = onOpenCreateTask,
                    modifier = Modifier.weight(1f),
                )
                FunctionEntryCard(
                    title = "评论私信",
                    description = "评论区触达功能",
                    icon = Icons.Default.Forum,
                    color = Color(0xFFE83A55),
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = AutomationSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日待办", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenTodo, contentPadding = PaddingValues(0.dp)) { Text("查看全部") }
            }
            if (savedTasks.isEmpty()) {
                EmptyState(title = "今天没有任务", detail = "点击功能中的 B端私信创建任务")
            } else {
                savedTasks.take(3).forEach { task ->
                    val query = task.customKeywords.firstOrNull { it.isNotBlank() }
                        ?: task.presetIds.asSequence()
                            .mapNotNull { id -> presetCatalog.items.firstOrNull { it.id == id }?.keyword }
                            .firstOrNull { it.isNotBlank() }
                        ?: "未设置搜索词"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AutomationBlueSurface)
                            .clickable(onClick = onOpenTodo)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .width(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(AutomationBlue),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(task.name.ifBlank { query }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$query · 上限 ${task.maxUsers}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(
                            checked = false,
                            onCheckedChange = { onOpenTodo() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    value: String,
    label: String,
    valueColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun FunctionEntryCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(142.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(description, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
                Text(if (title == "B端私信") "立即创建" else "敬请期待", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .height(54.dp),
            )
        }
    }
}

@Composable
private fun RemoteTaskCard(
    tasks: List<RemoteTask>,
    serviceConnected: Boolean,
    startingTaskId: Long?,
    refreshInFlight: Boolean,
    lastRefreshAtMillis: Long?,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onStart: (RemoteTask) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AutomationSpacing.Page),
        colors = CardDefaults.cardColors(containerColor = AutomationCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("远程任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRefresh, enabled = !refreshInFlight) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text(if (refreshInFlight) "刷新中" else "刷新")
                }
            }
            Text(
                "来自后台的待执行任务。领取后仍由本地无障碍状态机执行，网络同步在后台完成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lastRefreshAtMillis?.let {
                Text(
                    "最近刷新：${formatTaskTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            refreshMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (tasks.isEmpty()) {
                EmptyState(title = "暂无待领取任务", detail = "后台发布任务后将在这里显示")
            } else tasks.take(5).forEach { task ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "#${task.id} · ${remoteTaskListStatusLabel(task.status)} · ${task.keyword}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "已处理 ${task.processedCount}/${task.maxUsers.takeIf { it > 0 } ?: "不限"}",
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

private fun remoteTaskListStatusLabel(status: Int): String = when (status) {
    0 -> "草稿"
    1 -> "待执行"
    2 -> "执行中"
    3 -> "已暂停"
    4 -> "已完成"
    5 -> "失败"
    6 -> "已取消"
    else -> "未知状态"
}

private fun RemoteTask.toTaskSnapshot(): com.example.douyinautomation.automation.TaskSnapshot = run {
    val selectedRegion = regionPrefix ?: regionName.orEmpty()
    val composedQuery = QueryComposer.compose(selectedRegion, keyword)?.query ?: keyword
    com.example.douyinautomation.automation.TaskSnapshot(
        taskId = id.toString(),
        taskName = name,
        presetVersion = catalogVersion.toString(),
        baseKeywords = listOf(keyword),
        region = QueryComposer.normalize(selectedRegion),
        composedQueries = listOf(composedQuery),
        normalizedBlockedKeywords = blockedKeywords,
        maxUsers = maxUsers.takeIf { it > 0 } ?: TaskDraft.DEFAULT_MAX_USERS,
        messageTemplate = message.takeIf { it.isNotBlank() },
        // Never turn a server task into an automatic real-message send in this milestone.
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
        createdAtMillis = System.currentTimeMillis(),
    )
}

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    label: String,
    placeholder: String? = null,
    singleLine: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier.padding(top = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank() && placeholder != null) {
                Text(
                    placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = singleLine,
                visualTransformation = visualTransformation,
            )
        }
        HorizontalDivider(color = AutomationDivider, thickness = 1.dp)
    }
}

@Composable
private fun PresetChips(
    presets: List<SearchPreset>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        // Keep presets in a natural flow instead of forcing a two-column grid.
        // The small gap lets several short presets share a line while still
        // wrapping safely on narrower devices.
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEach { preset ->
            CompactPresetChip(
                selected = preset.id in selectedIds,
                onClick = { onToggle(preset.id) },
                label = preset.label,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompactPresetChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
                modifier = modifier
                    .height(30.dp)
                    .clickable(onClick = onClick),
            shape = shape,
            color = AutomationNeutralSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ServiceStatusBanner(
    connected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AutomationSpacing.Page, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            if (connected) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.height(17.dp),
            tint = if (connected) AutomationSuccess else AutomationWarning,
        )
        Text(
            if (connected) "自动化服务正常" else "自动化服务未开启",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DashboardHeader(
    serviceConnected: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AutomationSpacing.Page, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("自动化工作台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "搜索、筛选与安全探测",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Accessibility, contentDescription = null, modifier = Modifier.height(18.dp), tint = if (serviceConnected) AutomationSuccess else AutomationWarning)
                Text(
                    if (serviceConnected) "设备已就绪" else "需要开启无障碍",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!serviceConnected) {
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("去开启")
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(label)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun TodoTaskCard(
    tasks: List<TaskDraft>,
    presetCatalog: SearchPresetCatalog,
    selectedTaskIds: Set<String>,
    activeState: com.example.douyinautomation.automation.AutomationUiState,
    onToggleTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AutomationSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("待办任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (isTaskActivePhase(activeState.phase)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AutomationBlueSurface, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("正在执行", style = MaterialTheme.typography.labelMedium, color = AutomationBlueDark)
                    Text(
                        activeState.taskName ?: "当前任务",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${activeState.taskHandledUserCount} 已处理",
                    style = MaterialTheme.typography.bodySmall,
                    color = AutomationBlueDark,
                )
            }
        }
        if (tasks.isEmpty()) {
                EmptyState(
                    title = "暂无待办任务",
                    detail = "保存任务后，可在这里勾选多个任务并按顺序执行。",
                )
        } else {
                tasks.forEach { task ->
                    val taskLabel = task.name.trim().ifBlank { "未命名任务" }
                    val query = task.customKeywords.firstOrNull { it.isNotBlank() }
                        ?: task.presetIds.asSequence()
                            .mapNotNull { id -> presetCatalog.items.firstOrNull { it.id == id }?.keyword }
                            .firstOrNull { it.isNotBlank() }
                        ?: "未设置搜索词"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AutomationBlueSurface)
                        .clickable(enabled = !isTaskActivePhase(activeState.phase)) {
                            onToggleTask(task.id)
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .width(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AutomationBlue),
                    )
                    Column(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .weight(1f),
                    ) {
                        Text(
                            taskLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(query)
                                task.region?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                                append(" · 上限 ").append(task.maxUsers)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = task.id in selectedTaskIds,
                        onCheckedChange = { onToggleTask(task.id) },
                        enabled = !isTaskActivePhase(activeState.phase),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentTaskCard(
    state: com.example.douyinautomation.automation.AutomationUiState,
    showRemoteTasks: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AutomationSpacing.Page),
        colors = CardDefaults.cardColors(containerColor = AutomationCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("当前任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusBadge(
                    label = if (state.taskId == null && state.phase == AutomationPhase.IDLE) "暂无任务" else phaseLabel(state.phase),
                    tone = statusTone(state.phase),
                )
            }
            if (state.taskId == null && state.phase == AutomationPhase.IDLE) {
                Text("还没有运行中的任务", style = MaterialTheme.typography.titleLarge)
                Text("创建任务后，处理进度和安全探测结果会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            state.taskName?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            if (showRemoteTasks) {
                state.remoteTaskId?.let { remoteId ->
                    Text(
                        "远程任务 #$remoteId · ${remoteStatusLabel(state.remoteTaskStatus)} · 待同步 ${state.remoteSyncPendingCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.remoteSyncLastError?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = AutomationDivider)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("已处理", state.taskHandledUserCount.toString(), AutomationBlue)
                MetricItem("已跳过", state.taskDuplicateUserCount.toString(), AutomationWarning)
                MetricItem("失败", state.taskFailedUserCount.toString(), AutomationError)
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
                ErrorBanner(message = "任务执行异常", detail = it)
            }
            if (isTaskActivePhase(state.phase)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF) {
                        FilledTonalButton(onClick = { AutomationStore.send(AutomationCommand.Pause) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            Text("暂停")
                        }
                    } else {
                        Button(onClick = { AutomationStore.send(AutomationCommand.Resume) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            Text("继续任务")
                        }
                    }
                    OutlinedButton(onClick = { AutomationStore.send(AutomationCommand.Stop) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
}

private enum class StatusTone { PRIMARY, SUCCESS, WARNING, ERROR, NEUTRAL }

@Composable
private fun StatusBadge(label: String, tone: StatusTone) {
    val (background, foreground) = when (tone) {
        StatusTone.PRIMARY -> AutomationBlueLight to AutomationBlueDark
        StatusTone.SUCCESS -> AutomationSuccessSurface to AutomationSuccess
        StatusTone.WARNING -> AutomationWarningSurface to AutomationWarning
        StatusTone.ERROR -> AutomationErrorSurface to AutomationError
        StatusTone.NEUTRAL -> AutomationNeutralSurface to MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.material3.Surface(
        color = background,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun statusTone(phase: AutomationPhase): StatusTone = when (phase) {
    AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE,
    AutomationPhase.COMPLETED_TASK,
    AutomationPhase.COMPLETED_AT_MESSAGE_PAGE,
    -> StatusTone.SUCCESS
    AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> StatusTone.WARNING
    AutomationPhase.FAILED -> StatusTone.ERROR
    AutomationPhase.IDLE,
    AutomationPhase.SERVICE_READY,
    AutomationPhase.STOPPED,
    -> StatusTone.NEUTRAL
    else -> StatusTone.PRIMARY
}

private fun isTaskActivePhase(phase: AutomationPhase): Boolean = phase !in setOf(
    AutomationPhase.IDLE,
    AutomationPhase.SERVICE_READY,
    AutomationPhase.STOPPED,
    AutomationPhase.FAILED,
    AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE,
    AutomationPhase.COMPLETED_TASK,
    AutomationPhase.COMPLETED_AT_MESSAGE_PAGE,
)

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBanner(message: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(AutomationErrorSurface)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = AutomationError)
        Column(modifier = Modifier.weight(1f)) {
            Text(message, color = AutomationError, fontWeight = FontWeight.SemiBold)
            Text(detail, color = AutomationError, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Assignment, contentDescription = null, tint = AutomationBlue)
        Text(title, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskRecordsPage(
    padding: PaddingValues,
    onOpenTask: (String) -> Unit,
) {
    val state by AutomationStore.uiState.collectAsState()
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    val visibleHistory = state.taskHistory.asReversed().filter { history ->
        statusFilter == "ALL" || history.status.name == statusFilter
    }
    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(AutomationSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Content),
    ) {
        Text("处理记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "点击任务查看完整结果和用户处理明细",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL" to "全部", "RUNNING" to "执行中", "COMPLETED" to "已完成", "FAILED" to "失败", "PAUSED" to "暂停", "STOPPED" to "已停止").forEach { (value, label) ->
                FilterChip(
                    selected = statusFilter == value,
                    onClick = { statusFilter = value },
                    label = { Text(label) },
                )
            }
        }
        if (visibleHistory.isEmpty()) {
            EmptyState(title = "暂无任务记录", detail = "完成的自动化任务将在这里显示")
        } else {
            visibleHistory.forEach { history ->
                TaskHistoryCard(history, onClick = { onOpenTask(history.taskId) })
            }
        }
    }
}

@Composable
private fun TaskHistoryCard(history: TaskHistoryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AutomationCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(92.dp)
                    .background(AutomationBlue),
            )
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(history.taskName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusBadge(taskStatusLabel(history.status), taskStatusTone(history.status))
                }
                Text(formatTaskTime(history.updatedAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("已处理 ${history.handledCount} · 跳过 ${history.skippedCount} · 失败 ${history.failedCount}")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRecordDetailScreen(
    taskId: String,
    onBack: () -> Unit,
    onReuse: () -> Unit,
    onRetry: () -> Unit,
) {
    val state by AutomationStore.uiState.collectAsState()
    val history = state.taskHistory.firstOrNull { it.taskId == taskId }
    var retryMessage by rememberSaveable(taskId) { mutableStateOf<String?>(null) }
    val records = state.recordEntries
        .filter { it.taskId == taskId }
        .sortedByDescending { it.startedAtMillis }

    Scaffold(
        containerColor = AutomationPage,
        topBar = {
            TopAppBar(
                title = { Text(history?.taskName ?: "任务结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AutomationPage),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AutomationSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (history == null) {
                ErrorBanner("记录不可用", "任务记录不存在或已被清理")
                return@Column
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AutomationCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("任务概览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    StatusBadge(taskStatusLabel(history.status), taskStatusTone(history.status))
                    history.errorMessage?.takeIf(String::isNotBlank)?.let {
                        ErrorBanner("执行异常", taskErrorLabel(it))
                    }
                    HorizontalDivider(color = AutomationDivider)
                    Text("开始：${formatTaskTime(history.startedAtMillis)}")
                    Text("更新：${formatTaskTime(history.updatedAtMillis)}")
                    Text("搜索词组：${history.queryCount} · 用户上限：${history.maxUsers}")
                    if (history.searchQueries.isNotEmpty()) {
                        Text("实际搜索词：${history.searchQueries.joinToString("、")}")
                    }
                    history.region?.takeIf(String::isNotBlank)?.let { Text("地区前缀：$it") }
                    if (history.blockedKeywords.isNotEmpty()) {
                        Text("屏蔽词：${history.blockedKeywords.joinToString("、")}")
                    }
                    Text("执行模式：${executionModeLabel(history.executionMode)}")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricItem("已处理", history.handledCount.toString(), AutomationBlue)
                        MetricItem("已跳过", history.skippedCount.toString(), AutomationWarning)
                        MetricItem("失败", history.failedCount.toString(), AutomationError)
                    }
                    Text("命中屏蔽词：${history.filteredCount} · 重复用户：${history.duplicateCount}", style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(
                        onClick = {
                            AutomationStore.saveTaskDraft(history.toReusableDraft())
                            onReuse()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("复用此任务配置") }
                    if (history.status in setOf(TaskRunStatus.PAUSED, TaskRunStatus.STOPPED, TaskRunStatus.FAILED)) {
                        OutlinedButton(
                            onClick = {
                                val rejection = AutomationStore.retryTask(taskId)
                                if (rejection == null) {
                                    onRetry()
                                } else {
                                    retryMessage = rejection
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重新执行（空消息安全探测）") }
                        retryMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Text(
                "用户处理结果（${records.size}）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (records.isEmpty()) {
                EmptyState("暂无用户记录", "任务执行后，每个用户的处理结果会显示在这里")
            } else {
                records.forEach { record ->
                    UserTaskResultCard(record)
                }
            }
        }
    }
}

private fun TaskHistoryEntry.toReusableDraft(): TaskDraft = TaskDraft(
    id = "draft",
    name = "$taskName（复用）",
    customKeywords = searchQueries.ifEmpty { listOf(taskName) },
    region = region,
    blockedKeywords = blockedKeywords,
    maxUsers = maxUsers,
    messageTemplate = messageTemplate,
    executionMode = executionMode,
    taskType = taskType,
    commentConfig = commentConfig?.let { config ->
        com.example.douyinautomation.automation.CommentPrivateMessageConfig(
            entryMode = config.entryMode,
            targetUser = config.targetUser,
            matchKeywords = config.matchKeywords,
            maxVideos = config.maxVideos,
            maxUsersPerVideo = config.maxUsersPerVideo,
        )
    },
)

@Composable
private fun UserTaskResultCard(record: UserTaskRecord) {
    val visibleName = recordVisibleName(record)
    val accountLabel = recordAccountLabel(record, visibleName)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AutomationCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                Text(
                    visibleName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    softWrap = true,
                )
                Text(
                    recordOutcomeLabel(record.outcome),
                    color = if (record.outcome == UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED) AutomationSuccess else AutomationError,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true,
                )
            }
            accountLabel?.let { resultDetailLine(if (it.startsWith("抖音号：")) "抖音号" else "账号", it.removePrefix("抖音号：")) }
            resultDetailLine("时间", formatTaskTime(record.startedAtMillis))
            record.finishedAtMillis?.let { resultDetailLine("结束", formatTaskTime(it)) }
            resultDetailLine("内容", record.messageContent.orEmpty().ifBlank { "未设置" })
            resultDetailLine("页面", recordPageLabel(record.page))
            resultDetailLine("说明", recordReasonLabel(record), subdued = true)
        }
    }
}

/**
 * The stable identity key is intentionally composite for deduplication, but it is not a user
 * facing name. Never render that internal key as the card title.
 */
private fun recordVisibleName(record: UserTaskRecord): String {
    val display = record.displayName
        ?.let(::cleanRecordIdentity)
        .orEmpty()
    if (display.isNotBlank() && !isInternalIdentityLabel(display) && !isGenericAccountLabel(display)) {
        if (!isTruncatedIdentityLabel(display)) return display
    }
    record.userKey
        ?.takeIf { it.startsWith("handle:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { return "抖音号 $it" }
    return recordIdentitySegments(record.userKey)
        .firstOrNull { !isTruncatedIdentityLabel(it) }
        ?: recordIdentitySegments(record.userKey).firstOrNull()
        ?: display.takeIf {
            it.isNotBlank() &&
                !isInternalIdentityLabel(it) &&
                !isGenericAccountLabel(it)
        }
        ?: "未识别用户"
}

private fun recordAccountLabel(record: UserTaskRecord, visibleName: String): String? {
    val key = record.userKey?.trim().orEmpty()
    if (key.startsWith("handle:", ignoreCase = true)) {
        return "抖音号：${key.substringAfter(':').trim()}"
    }
    // Composite keys are kept for duplicate detection, not for display. Without a real handle,
    // the cleaned display name is safer than exposing company/verification metadata as an account.
    return visibleName.takeIf { it != "未识别用户" }
}

private fun recordIdentitySegments(userKey: String?): List<String> = userKey
    ?.split('|')
    ?.asSequence()
    ?.mapNotNull(::cleanRecordIdentity)
    ?.filter(String::isNotBlank)
    ?.filterNot(::isInternalIdentityLabel)
    ?.filterNot(::isGenericAccountLabel)
    ?.distinct()
    ?.toList()
    .orEmpty()

/**
 * Cleans only user-facing history text. Stored composite keys remain untouched for deduplication.
 * A few Douyin builds prepend a row ordinal and expose a clipped final dot/ellipsis in the same
 * text node; removing those presentation artifacts is safe when a complete profile title was not
 * available for an older record.
 */
private fun cleanRecordIdentity(value: String): String? {
    var cleaned = ProfileDisplayNameResolver.sanitizeCandidate(value) ?: return null
    cleaned = cleaned.replace(Regex("^\\d+[)）.]\\s*"), "")
    cleaned = ProfileDisplayNameResolver.sanitizeCandidate(cleaned) ?: return null
    cleaned = cleaned.trimEnd(' ', '.', '…', '·')
    return cleaned.takeIf(String::isNotBlank)
}

private fun isInternalIdentityLabel(value: String): Boolean {
    val normalized = value.lowercase(Locale.ROOT)
    return normalized.startsWith("com.") ||
        normalized.startsWith("android.") ||
        normalized.contains(":id/") ||
        normalized.contains("textview")
}

private fun isGenericAccountLabel(value: String): Boolean {
    val normalized = value
        .replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
        .lowercase(Locale.ROOT)
    return normalized in setOf(
        "直播",
        "背景图片",
        "背景图",
        "用户头像",
        "头像",
        "头像图片",
        "图片",
        "图片背景",
        "背景",
        "默认头像",
        "用户图片",
        "封面",
        "封面图片",
        "视频封面",
        "视频",
        "照片",
        "筛选",
        "按钮",
        "店铺账号",
        "商家认证账号",
        "(v)店铺账号",
        "(v)商家认证账号",
        "发过相关视频",
        "朋友",
    ) || normalized.contains("筛选") || normalized.contains("按钮")
}

private fun isTruncatedIdentityLabel(value: String): Boolean =
    value.contains("…") || value.contains("..") || value.trimEnd().endsWith('.')

/** Compact, wrapping key/value row used by the per-user audit cards. */
@Composable
private fun resultDetailLine(label: String, value: String, subdued: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = if (subdued) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            softWrap = true,
        )
    }
}

private fun recordPageLabel(page: PageKind?): String = when (page) {
    PageKind.HOME -> "抖音首页"
    PageKind.SEARCH_ENTRY -> "搜索页"
    PageKind.SEARCH_RESULTS -> "搜索结果页"
    PageKind.USER_RESULTS -> "用户列表页"
    PageKind.USER_PROFILE -> "用户主页"
    PageKind.PRIVATE_MESSAGE_RESTRICTED -> "私信受限提示页"
    PageKind.DIRECT_MESSAGE -> "私信页"
    PageKind.MESSAGE_EMPTY_REJECTED -> "私信页（空消息提示）"
    PageKind.MESSAGE_SEND_FAILED -> "私信页（发送失败）"
    PageKind.HUMAN_INTERVENTION -> "需要人工处理的页面"
    PageKind.LOGIN -> "登录页"
    PageKind.OUTSIDE_TARGET -> "抖音外部页面"
    PageKind.UNKNOWN, null -> "未知页面"
}

/** Convert internal English diagnostics into a short explanation an operator can understand. */
private fun recordReasonLabel(record: UserTaskRecord): String {
    val raw = record.reason.orEmpty().lowercase(Locale.ROOT)
    return when (record.outcome) {
        UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED ->
            "抖音提示不能发送空白消息，安全探测已完成，未发送真实内容。"
        UserTaskRecord.Outcome.MESSAGE_SEND_FAILED -> when {
            raw.contains("setting") || raw.contains("rejected") ->
                "对方设置了私信权限限制，当前消息无法发送，系统已自动跳过。"
            raw.contains("timeout") ->
                "等待私信发送结果超时，无法确认是否成功，系统已自动跳过。"
            raw.contains("input") || raw.contains("place") ->
                "未能找到或填写私信输入框，系统已自动跳过。"
            else -> "私信发送未获得成功确认，系统已自动跳过。"
        }
        UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE ->
            "未找到可用的发私信入口，系统已自动跳过。"
        UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED ->
            "该用户显示为“回关”，按规则跳过，避免误操作。"
        UserTaskRecord.Outcome.FILTERED_BY_KEYWORD ->
            "用户信息命中了屏蔽词，未进入私信流程。"
        UserTaskRecord.Outcome.DUPLICATE_SKIPPED ->
            "该用户之前已经处理过，为避免重复操作已跳过。"
        UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE ->
            "未能读取到稳定的用户身份信息，无法安全操作。"
        UserTaskRecord.Outcome.PAUSED ->
            "遇到需要人工确认的页面，任务已暂停。"
        UserTaskRecord.Outcome.STOPPED ->
            "任务被手动停止。"
        UserTaskRecord.Outcome.IN_PROGRESS ->
            "正在处理。"
    }
}

private fun taskErrorLabel(error: String): String {
    val raw = error.lowercase(Locale.ROOT)
    if (error.any { it in '\u4e00'..'\u9fff' }) return error
    return when {
        raw.contains("blank") && (raw.contains("input") || raw.contains("place")) ->
            "未能找到私信输入框，当前任务已结束。"
        raw.contains("result page") || raw.contains("continuation anchor") ->
            "下一批用户列表加载失败，任务已结束。"
        raw.contains("timeout") ->
            "某个处理步骤等待超时，任务已结束。"
        raw.contains("risk") || raw.contains("verification") ->
            "遇到抖音安全验证页面，需要人工确认。"
        raw.contains("login") ->
            "抖音登录状态失效，请重新登录后再执行任务。"
        else -> "任务执行过程中出现异常，系统已结束本次任务。"
    }
}

private fun recordOutcomeLabel(outcome: UserTaskRecord.Outcome): String = when (outcome) {
    UserTaskRecord.Outcome.IN_PROGRESS -> "处理中"
    UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED -> "模拟发送成功"
    UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE -> "私信入口不可用"
    UserTaskRecord.Outcome.MESSAGE_SEND_FAILED -> "发送失败"
    UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED -> "回关用户已跳过"
    UserTaskRecord.Outcome.FILTERED_BY_KEYWORD -> "命中屏蔽词"
    UserTaskRecord.Outcome.DUPLICATE_SKIPPED -> "重复用户已跳过"
    UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE -> "无法识别用户"
    UserTaskRecord.Outcome.PAUSED -> "已暂停"
    UserTaskRecord.Outcome.STOPPED -> "已停止"
}

private fun taskStatusLabel(status: TaskRunStatus): String = when (status) {
    TaskRunStatus.RUNNING -> "执行中"
    TaskRunStatus.PAUSED -> "已暂停"
    TaskRunStatus.STOPPED -> "已停止"
    TaskRunStatus.COMPLETED -> "已完成"
    TaskRunStatus.FAILED -> "执行失败"
}

private fun taskStatusTone(status: TaskRunStatus): StatusTone = when (status) {
    TaskRunStatus.RUNNING -> StatusTone.PRIMARY
    TaskRunStatus.PAUSED -> StatusTone.WARNING
    TaskRunStatus.COMPLETED -> StatusTone.SUCCESS
    TaskRunStatus.FAILED -> StatusTone.ERROR
    TaskRunStatus.STOPPED -> StatusTone.NEUTRAL
}

private fun remoteStatusLabel(status: Int?): String = when (status) {
    null -> "未同步"
    0 -> "草稿"
    1 -> "待执行"
    2 -> "执行中"
    3 -> "已暂停"
    4 -> "已完成"
    5 -> "执行失败"
    6 -> "已取消"
    else -> "状态 $status"
}

private fun executionModeLabel(mode: TaskExecutionMode): String = when (mode) {
    TaskExecutionMode.SAFE_BLANK_PROBE -> "空白消息安全探测"
    TaskExecutionMode.REAL_SEND_REQUIRES_CONFIRMATION -> "真实发送（需确认）"
}

private fun formatTaskTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    showRemoteTasks: Boolean,
    onShowRemoteTasksChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseState by AuthStore.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val existingConfig = remember { AuthStore.currentConfig() }
    val defaultDeviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }
    var endpoint by rememberSaveable { mutableStateOf(existingConfig?.endpoint.orEmpty()) }
    var licenseToken by rememberSaveable { mutableStateOf(existingConfig?.licenseToken.orEmpty()) }
    var deviceId by rememberSaveable { mutableStateOf(existingConfig?.deviceId ?: defaultDeviceId) }
    var username by rememberSaveable {
        mutableStateOf(existingConfig?.accountUsername ?: existingConfig?.accountName.orEmpty())
    }
    var password by rememberSaveable { mutableStateOf("") }
    var loginBusy by remember { mutableStateOf(false) }
    var loginMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var loggedInAccount by rememberSaveable { mutableStateOf(existingConfig?.accountName) }
    var configMessage by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(AutomationSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Content),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SettingsSectionCard(title = "账号与授权", icon = Icons.Default.Cloud) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("授权状态", fontWeight = FontWeight.Medium)
                    StatusBadge(licenseStatusLabel(licenseState.status), if (licenseState.status == LicenseStatus.VERIFIED) StatusTone.SUCCESS else StatusTone.WARNING)
                }
                Text(
                    licenseState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                loggedInAccount?.takeIf(String::isNotBlank)?.let { account ->
                    Text(
                        "当前登录账号：$account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                CompactOutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "后端地址（HTTPS）",
                    placeholder = "例如 https://api.example.com",
                    singleLine = true,
                )
                Text(
                    "使用后台账号登录后，系统会为本设备换取移动端授权；密码不会保存，管理端 JWT 也不会写入设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactOutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "后台用户名",
                    singleLine = true,
                )
                CompactOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "后台密码",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    enabled = !loginBusy,
                    onClick = {
                        scope.launch {
                            loginBusy = true
                            loginMessage = null
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AuthStore.login(
                                        context = context,
                                        endpoint = endpoint,
                                        username = username,
                                        password = password,
                                    )
                                }
                            }.onSuccess { result ->
                                loggedInAccount = result.accountName ?: result.accountUsername ?: username.trim()
                                password = ""
                                loginMessage = "登录成功，已获得本设备授权；现在可以刷新远程任务"
                            }.onFailure { error ->
                                loginMessage = "登录失败：${error.message ?: "请检查地址、账号或密码"}"
                            }
                            loginBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loginBusy) "登录中…" else "账号登录并获取任务")
                }
                loginMessage?.let { message ->
                    Text(
                        message,
                        color = if (message.startsWith("登录成功")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                HorizontalDivider()
                Text(
                    "兼容方式：也可以手动粘贴移动端授权 Token。Token 不是后台登录 access_token，两者长度不同是正常的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactOutlinedTextField(
                    value = licenseToken,
                    onValueChange = { licenseToken = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "授权 Token",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                CompactOutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = "设备标识（默认 Android ID）",
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
                                accountName = loggedInAccount,
                                accountUsername = username.trim().takeIf(String::isNotBlank),
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
                    "账号登录后 Token 使用 Android Keystore 加密保存；请求仅携带移动端 Bearer 授权，不会写入 Logcat。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsSectionCard(title = "自动化服务", icon = Icons.Default.Accessibility) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRow("目标应用", "抖音")
                SettingsRow("执行方式", "Android 无障碍服务")
                SettingsRow("安全模式", "空白消息探测")
                Text(
                    "预设搜索词：开启远程任务后使用后台目录；关闭时使用本地缓存和内置词。地区规则、屏蔽词与任务断点接口已接入客户端网关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { openAccessibilitySettings(context) }) {
                    Text("无障碍服务设置")
                }
            }
        }
        SettingsSectionCard(title = "远程任务", icon = Icons.Default.Cloud) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("显示远程任务", fontWeight = FontWeight.Medium)
                    Text(
                        "默认关闭。开启后，任务页才会显示并刷新后台下发的任务；手机本地新建任务不受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showRemoteTasks,
                    onCheckedChange = onShowRemoteTasksChange,
                )
            }
        }
        SettingsSectionCard(title = "开发者选项", icon = Icons.Default.Tune) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("截图、OCR、节点树和原始事件仅在诊断页显示。")
                OutlinedButton(onClick = onOpenDiagnostics) { Text("打开诊断页") }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AutomationCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = AutomationBlue)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = AutomationDivider)
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
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
