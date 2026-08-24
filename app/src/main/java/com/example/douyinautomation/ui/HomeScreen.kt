package com.example.douyinautomation.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.douyinautomation.BuildConfig
import com.example.douyinautomation.CommentRegressionPreset
import com.example.douyinautomation.automation.AutomationCommand
import com.example.douyinautomation.automation.AutomationActionIntervalPolicy
import com.example.douyinautomation.automation.AutomationActionIntervalSettingsStore
import com.example.douyinautomation.automation.AutomationExecutionLimits
import com.example.douyinautomation.automation.AutomationPhase
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.AutomationTaskType
import com.example.douyinautomation.automation.CommentPrivateMessageConfig
import com.example.douyinautomation.automation.CommentPrivateMessageEntryMode
import com.example.douyinautomation.automation.CommentKeywordMatchMode
import com.example.douyinautomation.automation.LicenseStatus
import com.example.douyinautomation.automation.LocalTaskQueuePolicy
import com.example.douyinautomation.automation.LocalTaskQueueType
import com.example.douyinautomation.automation.LocalSearchPresetRepository
import com.example.douyinautomation.automation.QueryComposer
import com.example.douyinautomation.automation.RemoteTask
import com.example.douyinautomation.automation.RemoteTaskResume
import com.example.douyinautomation.automation.RemoteTaskResumePolicy
import com.example.douyinautomation.automation.RemoteTaskAuthorizationStore
import com.example.douyinautomation.automation.RemoteTaskVisibilityStore
import com.example.douyinautomation.automation.RegionCatalog
import com.example.douyinautomation.automation.BlockKeywordCatalog
import com.example.douyinautomation.automation.BlockedKeywordInputParser
import com.example.douyinautomation.automation.FloatingOverlayService
import com.example.douyinautomation.automation.PageKind
import com.example.douyinautomation.automation.ProfileDisplayNameResolver
import com.example.douyinautomation.automation.SearchPreset
import com.example.douyinautomation.automation.SearchPresetCatalog
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode
import com.example.douyinautomation.automation.TaskHistoryEntry
import com.example.douyinautomation.automation.TaskRunStatus
import com.example.douyinautomation.automation.TaskUserLimitInputPolicy
import com.example.douyinautomation.automation.UserTaskRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    MY,
    RECORDS,
    SETTINGS,
    DIAGNOSTICS,
}

private const val COMMENT_P0_REGRESSION_SEED = "comment-p0-designer-1-1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    initialKeyword: String = "",
    initialSection: String? = null,
    initialCommentTask: Boolean = false,
    autoStartCommentP0: Boolean = false,
    commentP0LaunchToken: Int = 0,
    commentRegressionPreset: CommentRegressionPreset? = null,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection ?: HomeSection.HOME.name) }
    var detailTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var showCommentTask by rememberSaveable { mutableStateOf(initialCommentTask) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRemoteTasks by remember(context) {
        mutableStateOf(RemoteTaskVisibilityStore.isEnabled(context))
    }
    LaunchedEffect(initialSection) {
        initialSection?.let { section = it }
    }
    // A re-delivered OPEN_COMMENT_P0 intent raises the launch token; re-show the comment task even
    // though showCommentTask (rememberSaveable) was persisted as false after the previous onClose().
    LaunchedEffect(commentP0LaunchToken) {
        com.example.douyinautomation.automation.AutomationStore.logger.info(
            "p0_launch_show_comment_effect",
            attributes = mapOf(
                "token" to commentP0LaunchToken,
                "autoStart" to autoStartCommentP0,
                "showBefore" to showCommentTask,
            ),
        )
        if (autoStartCommentP0 && commentP0LaunchToken > 0) {
            // A previous onClose() (or an openRecordsTab() recreate) may leave the persisted
            // section on RECORDS/TODO. CommentTaskScreen is only composed from the HOME/TODO
            // branches, so force the section back to HOME before re-showing it. Without this the
            // re-delivered debug intent re-arms the latch but never composes the runner.
            section = HomeSection.HOME.name
            showCommentTask = true
        }
    }
    val selectedSection = HomeSection.valueOf(section)

    BackHandler(
        enabled = detailTaskId != null || showCreateTask || showCommentTask || selectedSection != HomeSection.HOME,
    ) {
        when {
            detailTaskId != null -> detailTaskId = null
            showCreateTask || showCommentTask -> {
                showCreateTask = false
                showCommentTask = false
            }
            selectedSection == HomeSection.RECORDS ||
                selectedSection == HomeSection.SETTINGS ||
                selectedSection == HomeSection.DIAGNOSTICS -> section = HomeSection.MY.name
            selectedSection == HomeSection.MY || selectedSection == HomeSection.TODO -> section = HomeSection.HOME.name
            else -> Unit
        }
    }

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
                // retryTask() has already dispatched the frozen historical snapshot directly to
                // the accessibility controller. Returning to TODO here opens the generic B-end
                // creation form and visually suggests that a comment task needs to be rebuilt as
                // a different task type. Keep the operator on records while the same safe-probe
                // task starts in Douyin.
                section = HomeSection.RECORDS.name
            },
        )
        return
    }

    if (selectedSection == HomeSection.DIAGNOSTICS) {
        DiagnosticsScreen(onBack = { section = HomeSection.MY.name })
        return
    }

    Scaffold(
        containerColor = AutomationPage,
        topBar = {
            if (showCreateTask || showCommentTask || selectedSection == HomeSection.MY || selectedSection == HomeSection.RECORDS || selectedSection == HomeSection.SETTINGS || selectedSection == HomeSection.DIAGNOSTICS) {
                TopAppBar(
                title = {
                    if (selectedSection == HomeSection.MY && !showCreateTask && !showCommentTask) {
                        Text(
                            text = "我的",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Column {
                            Text(
                                when {
                                    showCommentTask -> "评论私信"
                                    showCreateTask -> "新建任务"
                                    selectedSection == HomeSection.TODO -> "待办"
                                    selectedSection == HomeSection.RECORDS -> "任务记录"
                                    selectedSection == HomeSection.SETTINGS -> "自动化设置"
                                    else -> "诊断"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val subtitle = when (selectedSection) {
                                HomeSection.TODO -> "保存的任务按顺序执行"
                                else -> ""
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showCreateTask || showCommentTask) {
                        IconButton(onClick = {
                            showCreateTask = false
                            showCommentTask = false
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回首页")
                        }
                    } else if (selectedSection == HomeSection.RECORDS || selectedSection == HomeSection.SETTINGS) {
                        IconButton(onClick = { section = HomeSection.MY.name }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回我的")
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
            if (!showCreateTask && !showCommentTask) {
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
                        selected = selectedSection == HomeSection.MY || selectedSection == HomeSection.RECORDS || selectedSection == HomeSection.SETTINGS,
                        onClick = { section = HomeSection.MY.name },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("我的") },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedSection) {
            HomeSection.HOME -> if (showCommentTask) {
                CommentTaskScreen(
                    padding = padding,
                    presetCatalog = remember {
                        SearchPresetCatalog(
                            version = LocalSearchPresetRepository.BUILT_IN_VERSION,
                            items = LocalSearchPresetRepository.BUILT_IN_PRESETS,
                            updatedAtMillis = 0L,
                            source = SearchPresetCatalog.Source.BUILT_IN,
                        )
                    },
                    autoStartP0 = autoStartCommentP0,
                    autoStartToken = commentP0LaunchToken,
                    regressionPreset = commentRegressionPreset,
                    onClose = {
                        showCommentTask = false
                        showCreateTask = false
                    },
                )
            } else TaskDashboard(
                padding = padding,
                initialKeyword = initialKeyword,
                showCreateTask = showCreateTask,
                showRemoteTasks = showRemoteTasks,
                showTodoOnly = false,
                onCloseCreateTask = { showCreateTask = false },
                onOpenCreateTask = { showCreateTask = true },
                onOpenCommentTask = { showCommentTask = true },
                onOpenTodo = { section = HomeSection.TODO.name },
            )
            HomeSection.TODO -> if (showCommentTask) {
                CommentTaskScreen(
                    padding = padding,
                    presetCatalog = remember {
                        SearchPresetCatalog(
                            version = LocalSearchPresetRepository.BUILT_IN_VERSION,
                            items = LocalSearchPresetRepository.BUILT_IN_PRESETS,
                            updatedAtMillis = 0L,
                            source = SearchPresetCatalog.Source.BUILT_IN,
                        )
                    },
                    autoStartP0 = autoStartCommentP0,
                    autoStartToken = commentP0LaunchToken,
                    regressionPreset = commentRegressionPreset,
                    onClose = {
                        showCommentTask = false
                        showCreateTask = false
                    },
                )
            } else TaskDashboard(
                padding = padding,
                initialKeyword = initialKeyword,
                showCreateTask = showCreateTask,
                showRemoteTasks = showRemoteTasks,
                showTodoOnly = true,
                onCloseCreateTask = { showCreateTask = false },
                onOpenCreateTask = { showCreateTask = true },
                onOpenCommentTask = { showCommentTask = true },
                onOpenTodo = { section = HomeSection.TODO.name },
            )

            HomeSection.MY -> MyPage(
                padding = padding,
                onOpenRecords = { section = HomeSection.RECORDS.name },
                onOpenSettings = { section = HomeSection.SETTINGS.name },
                onOpenDiagnostics = { section = HomeSection.DIAGNOSTICS.name },
                onSignOut = { AuthStore.clearConfig(context) },
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
    onOpenCommentTask: () -> Unit,
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
    var selectedTodoQueueType by rememberSaveable {
        mutableStateOf(LocalTaskQueueType.B_END_PRIVATE_MESSAGE)
    }
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
            maxUsers = TaskUserLimitInputPolicy.valueForPersistedDraft(savedDraft.maxUsers).toString()
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
                        blockedKeywords = BlockedKeywordInputParser.parse(blockedKeywords),
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
        blockedKeywords = BlockedKeywordInputParser.parse(blockedKeywords),
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
    val canStart = state.serviceCommandReady && queries.isNotEmpty() && draftErrors.isEmpty()
    val canSave = queries.isNotEmpty() && draftErrors.isEmpty()
    val fixedTodoLayout = !showCreateTask && showTodoOnly && !showRemoteTasks
    val visibleTodoTasks = savedTasks.filter { task ->
        runCatching {
            task.toSnapshot(presets = presetCatalog, nowMillis = 0L)
        }.getOrNull()?.let(LocalTaskQueuePolicy::typeOf) == selectedTodoQueueType
    }

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
                val snapshots = visibleTodoTasks
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
                TabRow(
                    selectedTabIndex = selectedTodoQueueType.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = selectedTodoQueueType == LocalTaskQueueType.B_END_PRIVATE_MESSAGE,
                        onClick = {
                            selectedTodoQueueType = LocalTaskQueueType.B_END_PRIVATE_MESSAGE
                            selectedSavedTaskIds = emptySet()
                        },
                        text = { Text("B端用户私信") },
                    )
                    Tab(
                        selected = selectedTodoQueueType == LocalTaskQueueType.COMMENT_SEARCH_PROFILE,
                        onClick = {
                            selectedTodoQueueType = LocalTaskQueueType.COMMENT_SEARCH_PROFILE
                            selectedSavedTaskIds = emptySet()
                        },
                        text = { Text("评论区私信") },
                    )
                }
                if (fixedTodoLayout) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TodoTaskCard(
                            tasks = visibleTodoTasks,
                            presetCatalog = presetCatalog,
                            selectedTaskIds = selectedSavedTaskIds,
                            activeState = state,
                            onToggleTask = toggleTask,
                        )
                    }
                    Button(
                        onClick = startSelected,
                        enabled = state.serviceCommandReady && selectedSavedTaskIds.isNotEmpty() && !isTaskActivePhase(state.phase),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AutomationSpacing.Page, vertical = 6.dp)
                            .height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("开始任务")
                    }
                } else {
                    TodoTaskCard(
                        tasks = visibleTodoTasks,
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
                    onOpenCommentTask = onOpenCommentTask,
                    onOpenTodo = onOpenTodo,
                )
            }
            if (showTodoOnly && showRemoteTasks && (remoteTasks.isNotEmpty() || licenseState.status != LicenseStatus.NOT_CONFIGURED)) {
                RemoteTaskCard(
                    tasks = remoteTasks,
                    serviceConnected = state.serviceCommandReady,
                    startingTaskId = remoteStartingTaskId,
                    refreshInFlight = remoteRefreshInFlight,
                    lastRefreshAtMillis = remoteRefreshAtMillis,
                    refreshMessage = remoteRefreshMessage,
                    onRefresh = { remoteRefreshNonce += 1 },
                    onStart = { remoteTask ->
                        scope.launch {
                            if (!RemoteTaskAuthorizationStore.isAuthorized(context, remoteTask.id)) {
                                remoteRefreshMessage = "远程任务 #${remoteTask.id} 未在本机授权任务 ID 清单中"
                                AutomationStore.publishRemoteSyncError(remoteRefreshMessage.orEmpty())
                                return@launch
                            }
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
                SectionHeader("基础信息", compact = true)
                CompactOutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "任务名称",
                    placeholder = "选填",
                    taskForm = true,
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
                        .height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "自定义搜索词",
                    placeholder = "输入搜索词",
                    taskForm = true,
                    singleLine = true,
                )
                SectionHeader("搜索条件", compact = true)
                Text("预设搜索词", style = MaterialTheme.typography.labelMedium)
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
                        .height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "地区",
                    placeholder = "选填",
                    taskForm = true,
                    singleLine = true,
                )
                SectionHeader("筛选条件", compact = true)
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
                        .height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "屏蔽关键词",
                    placeholder = "逗号分隔",
                    taskForm = true,
                    singleLine = true,
                )
                val blockedPresetKeywords = (blockKeywordCatalog.items.map { it.keyword } + listOf("厂", "公司"))
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                if (blockedPresetKeywords.isNotEmpty()) {
                    Text("预设屏蔽词", style = MaterialTheme.typography.labelMedium)
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
                SectionHeader("执行限制", compact = true)
                CompactOutlinedTextField(
                    value = maxUsers,
                    onValueChange = { value -> maxUsers = TaskUserLimitInputPolicy.sanitizeInput(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "最多处理用户数",
                    taskForm = true,
                    singleLine = true,
                )
                if (draftErrors.isNotEmpty()) {
                    Text(
                        draftErrors.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                            .height(46.dp),
                        shape = RoundedCornerShape(8.dp),
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
                            .height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("立即开始")
                    }
                }
        }
    }
}

@Composable
private fun CommentTaskScreen(
    padding: PaddingValues,
    presetCatalog: SearchPresetCatalog,
    autoStartP0: Boolean = false,
    autoStartToken: Int = 0,
    regressionPreset: CommentRegressionPreset? = null,
    onClose: () -> Unit,
) {
    val state by AutomationStore.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    // The debug APK starts with the bounded P0 regression contract already filled in.  This
    // removes repeated form entry/screenshots from every device run; release users still start
    // with an empty, editable configuration.
    val useP0RegressionDefaults = BuildConfig.DEBUG
    var entryMode by rememberSaveable {
        mutableStateOf(
            if (useP0RegressionDefaults) {
                CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE
            } else {
                CommentPrivateMessageEntryMode.CURRENT_PROFILE
            },
        )
    }
    var targetUser by rememberSaveable { mutableStateOf(if (useP0RegressionDefaults) "designer" else "") }
    var taskName by rememberSaveable { mutableStateOf("") }
    var matchKeywords by rememberSaveable { mutableStateOf("") }
    var matchMode by rememberSaveable { mutableStateOf(CommentKeywordMatchMode.ANY) }
    var maxVideos by rememberSaveable { mutableStateOf("1") }
    var maxUsers by rememberSaveable { mutableStateOf(if (useP0RegressionDefaults) "1" else "5") }
    var skipPinnedVideos by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    // A versioned one-time seed also handles an existing debug install whose saved Compose state
    // predates this regression preset. It never changes values again after the operator edits
    // the form in the current development build.
    var regressionSeedVersion by rememberSaveable { mutableStateOf("") }
    // The M3 parameterized preset takes precedence over the fixed P0 seed. Its values are applied
    // once per delivered preset instance (identified by the autoStartToken, which is unique per
    // debug intent) so 5/10/20 bounds, match keywords, skip-pinned and multi-video variants can be
    // driven from ADB extras without touching the form.
    var appliedPresetToken by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(autoStartToken, regressionPreset) {
        val preset = regressionPreset
        if (useP0RegressionDefaults && preset != null && appliedPresetToken != autoStartToken) {
            appliedPresetToken = autoStartToken
            entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE
            targetUser = preset.targetUser
            taskName = ""
            matchKeywords = preset.matchKeywords
            matchMode = preset.matchMode
            maxVideos = preset.maxVideos.toString()
            maxUsers = preset.maxUsersPerVideo.toString()
            skipPinnedVideos = preset.skipPinnedVideos
            regressionSeedVersion = COMMENT_P0_REGRESSION_SEED
            com.example.douyinautomation.automation.AutomationStore.logger.info(
                "p0_regression_preset_applied",
                attributes = mapOf(
                    "token" to autoStartToken,
                    // Numeric bounds keyed without "user"/"keyword" so the M3 5/10/20 evidence is
                    // not swallowed by the logger's defensive redaction. The target and terms are
                    // deliberately left sensitive (redacted) since they may be account identifiers.
                    "max_videos" to preset.maxVideos,
                    "per_video_cap" to preset.maxUsersPerVideo,
                    "skip_pinned" to preset.skipPinnedVideos,
                ),
            )
        }
    }
    LaunchedEffect(useP0RegressionDefaults, regressionSeedVersion) {
        if (useP0RegressionDefaults && regressionSeedVersion != COMMENT_P0_REGRESSION_SEED && regressionPreset == null) {
            entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE
            targetUser = "designer"
            taskName = ""
            matchKeywords = ""
            maxVideos = "1"
            maxUsers = "1"
            skipPinnedVideos = false
            regressionSeedVersion = COMMENT_P0_REGRESSION_SEED
        }
    }

    val config = CommentPrivateMessageConfig(
        entryMode = entryMode,
        targetUser = targetUser.trim().takeIf { it.isNotEmpty() },
        matchKeywords = matchKeywords.split('|'),
        matchMode = matchMode,
        maxVideos = maxVideos.toIntOrNull() ?: 0,
        maxUsersPerVideo = maxUsers.toIntOrNull() ?: 0,
        skipPinnedVideos = skipPinnedVideos,
        dryRun = regressionPreset?.dryRun == true,
    )
    val draft = TaskDraft(
        id = java.util.UUID.randomUUID().toString(),
        name = taskName,
        customKeywords = if (entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE) {
            listOf(targetUser)
        } else {
            emptyList()
        },
        maxUsers = (maxUsers.toIntOrNull() ?: 0).coerceAtLeast(1),
        executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
        taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
        commentConfig = config,
    )
    val errors = draft.validationErrors()
    val snapshot = runCatching {
        draft.toSnapshot(presets = presetCatalog, nowMillis = System.currentTimeMillis())
    }.getOrNull()
    // The system setting may say that accessibility is enabled before Android has created the
    // service's command collector. Auto-start must wait for that collector, otherwise the
    // one-shot debug command can be emitted into a SharedFlow with no subscriber.
    val canRun = state.serviceCommandReady && errors.isEmpty() && snapshot != null
    // A monotonically increasing launch token identifies each distinct debug intent. Keying the
    // latch on the token (rather than a rememberSaveable boolean) re-arms auto-start when a new
    // OPEN_COMMENT_P0 intent arrives at an already-running MainActivity, which previously required
    // a force-stop workaround because rememberSaveable survived recreate().
    val autoStartLatchToken = if (autoStartP0) autoStartToken else 0
    var autoStartTriggeredForToken by rememberSaveable { mutableStateOf(0) }

    fun startPreparedTask(
        prepared: com.example.douyinautomation.automation.TaskSnapshot?,
        suspendedBeforeStart: Boolean = false,
    ) {
        if (prepared == null) {
            message = errors.firstOrNull() ?: "任务配置暂不可执行"
        } else {
            AutomationStore.send(
                AutomationCommand.Start(
                    keyword = prepared.composedQueries.firstOrNull().orEmpty(),
                    message = "",
                    safetyProbe = true,
                    taskSnapshot = prepared,
                    suspendedBeforeStart = suspendedBeforeStart,
                ),
            )
            onClose()
        }
    }
    // The preset LaunchedEffect above writes the M3 5/10/20 bounds into the same rememberSaveable
    // form state that this builder reads. Building the snapshot here (inside the auto-start
    // coroutine) instead of reusing the composable-body `snapshot` val is essential: the body val
    // is captured stale from the pre-preset composition, whereas these delegated state reads are
    // live at coroutine execution time, so the bounds that actually reach the runtime match the
    // delivered preset instead of the fixed "1" seed.
    fun buildPreparedSnapshot(): com.example.douyinautomation.automation.TaskSnapshot? {
        val liveConfig = CommentPrivateMessageConfig(
            entryMode = entryMode,
            targetUser = targetUser.trim().takeIf { it.isNotEmpty() },
            matchKeywords = matchKeywords.split('|'),
            matchMode = matchMode,
            maxVideos = maxVideos.toIntOrNull() ?: 0,
            maxUsersPerVideo = maxUsers.toIntOrNull() ?: 0,
            skipPinnedVideos = skipPinnedVideos,
            dryRun = regressionPreset?.dryRun == true,
        )
        val liveDraft = TaskDraft(
            id = java.util.UUID.randomUUID().toString(),
            name = taskName,
            customKeywords = if (entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE) {
                listOf(targetUser)
            } else {
                emptyList()
            },
            maxUsers = (maxUsers.toIntOrNull() ?: 0).coerceAtLeast(1),
            executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
            taskType = AutomationTaskType.COMMENT_PRIVATE_MESSAGE,
            commentConfig = liveConfig,
        )
        if (liveDraft.validationErrors().isNotEmpty()) return null
        return runCatching {
            liveDraft.toSnapshot(presets = presetCatalog, nowMillis = System.currentTimeMillis())
        }.getOrNull()
    }
    // Development-only direct runner: no separate business logic is used. It invokes exactly
    // the same prepared snapshot/start command as the visible “立即开始” control. The
    // appliedPresetToken key re-arms this effect after the M3 regression preset has been written
    // into the form state, so the captured snapshot carries the 5/10/20 bounds instead of the
    // stale fixed seed (the two LaunchedEffects otherwise run in the same frame).
    LaunchedEffect(autoStartLatchToken, canRun, appliedPresetToken) {
        com.example.douyinautomation.automation.AutomationStore.logger.info(
            "p0_autostart_effect",
            attributes = mapOf(
                "token" to autoStartLatchToken,
                "triggeredFor" to autoStartTriggeredForToken,
                "canRun" to canRun,
                "ready" to state.serviceCommandReady,
                "errors" to errors.size,
                "snapshot" to (snapshot != null),
                "applied_preset" to appliedPresetToken,
            ),
        )
        val presetReady = regressionPreset == null || appliedPresetToken == autoStartLatchToken
        if (autoStartLatchToken != 0 && autoStartTriggeredForToken != autoStartLatchToken && canRun && presetReady) {
            autoStartTriggeredForToken = autoStartLatchToken
            startPreparedTask(buildPreparedSnapshot())
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AutomationSpacing.Page, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("开始位置", compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormChoiceChip(
                selected = entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE,
                onClick = { entryMode = CommentPrivateMessageEntryMode.CURRENT_PROFILE },
                label = "当前用户主页",
            )
            FormChoiceChip(
                selected = entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE,
                onClick = { entryMode = CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE },
                label = "搜索指定用户",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("跳过置顶视频", style = MaterialTheme.typography.labelLarge)
            }
            Switch(checked = skipPinnedVideos, onCheckedChange = { skipPinnedVideos = it })
        }
        if (entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE) {
            CompactOutlinedTextField(
                value = targetUser,
                onValueChange = { targetUser = it },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                label = "目标用户",
                placeholder = "用户名或搜索词",
                taskForm = true,
                singleLine = true,
            )
        }
        SectionHeader("任务配置", compact = true)
        CompactOutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            label = "任务名称",
            placeholder = "选填",
            taskForm = true,
            singleLine = true,
        )
        CompactOutlinedTextField(
            value = matchKeywords,
            onValueChange = { matchKeywords = it },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            label = "评论匹配词",
            placeholder = "多个词用 | 分隔",
            taskForm = true,
            singleLine = true,
        )
        Text("匹配方式", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormChoiceChip(
                selected = matchMode == CommentKeywordMatchMode.ANY,
                onClick = { matchMode = CommentKeywordMatchMode.ANY },
                label = "任一匹配",
            )
            FormChoiceChip(
                selected = matchMode == CommentKeywordMatchMode.ALL,
                onClick = { matchMode = CommentKeywordMatchMode.ALL },
                label = "全部匹配",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactOutlinedTextField(
                value = maxVideos,
                onValueChange = { maxVideos = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f).height(50.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                label = "视频数上限",
                taskForm = true,
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = maxUsers,
                onValueChange = { maxUsers = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f).height(50.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                label = "每个视频用户数",
                taskForm = true,
                singleLine = true,
            )
        }
        if (errors.isNotEmpty()) {
            Text(
                errors.first(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { AutomationStore.saveSavedTask(draft) }
                        onClose()
                    }
                },
                enabled = errors.isEmpty(),
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text("保存") }
            if (entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE) {
                FilledTonalButton(
                    onClick = { startPreparedTask(snapshot, suspendedBeforeStart = true) },
                    enabled = canRun,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("挂起") }
            }
            Button(
                onClick = {
                    startPreparedTask(snapshot)
                },
                enabled = canRun,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text("立即开始") }
        }
    }
}

@Composable
private fun HomeDashboardContent(
    state: com.example.douyinautomation.automation.AutomationUiState,
    savedTasks: List<TaskDraft>,
    presetCatalog: SearchPresetCatalog,
    onOpenCreateTask: () -> Unit,
    onOpenCommentTask: () -> Unit,
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
                    actionLabel = "立即创建",
                    icon = Icons.Default.Business,
                    color = AutomationBlue,
                    onClick = onOpenCreateTask,
                    modifier = Modifier.weight(1f),
                )
                FunctionEntryCard(
                    title = "评论私信",
                    description = "评论区触达功能",
                    actionLabel = "立即创建",
                    icon = Icons.Default.Forum,
                    color = Color(0xFFE83A55),
                    onClick = onOpenCommentTask,
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
    actionLabel: String,
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
                Text(actionLabel, color = Color.White, style = MaterialTheme.typography.labelLarge)
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
        maxUsers = maxUsers
            .takeIf { it > 0 }
            ?.coerceAtMost(AutomationExecutionLimits.MAX_USERS_PER_TASK)
            ?: TaskDraft.DEFAULT_MAX_USERS,
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
    taskForm: Boolean = false,
    singleLine: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (taskForm) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = AutomationNeutralSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AutomationDivider),
            ) {
                TaskFormTextInput(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle,
                    placeholder = placeholder,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                )
            }
        } else {
            TaskFormTextInput(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                placeholder = placeholder,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            HorizontalDivider(color = AutomationDivider, thickness = 1.dp)
        }
    }
}

@Composable
private fun TaskFormTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    placeholder: String?,
    singleLine: Boolean,
    visualTransformation: VisualTransformation,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
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
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FormChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.height(34.dp),
            shape = RoundedCornerShape(8.dp),
        )
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
    shape: Shape = RoundedCornerShape(8.dp),
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
private fun SectionHeader(title: String, compact: Boolean = false) {
    Text(
        text = title,
        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            top = if (compact) 10.dp else 14.dp,
            bottom = if (compact) 0.dp else 2.dp,
        ),
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
                    if (state.phase !in setOf(
                            AutomationPhase.SUSPENDED_BEFORE_START,
                            AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
                        )
                    ) {
                        FilledTonalButton(onClick = { AutomationStore.send(AutomationCommand.Pause) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            Text("暂停")
                        }
                    } else {
                        Button(onClick = { AutomationStore.send(AutomationCommand.Resume) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            Text(if (state.phase == AutomationPhase.SUSPENDED_BEFORE_START) "恢复任务" else "继续任务")
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
    AutomationPhase.SUSPENDED_BEFORE_START,
    AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
    -> StatusTone.WARNING
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("筛选状态", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL" to "全部", "RUNNING" to "执行中", "COMPLETED" to "已完成", "FAILED" to "失败", "PAUSED" to "暂停", "STOPPED" to "已停止").forEach { (value, label) ->
                FormChoiceChip(
                    selected = statusFilter == value,
                    onClick = { statusFilter = value },
                    label = label,
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
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(84.dp)
                    .background(AutomationBlue),
            )
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                title = {
                    Text(
                        history?.taskName ?: "任务结果",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("任务概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusBadge(taskStatusLabel(history.status), taskStatusTone(history.status))
                    history.errorMessage?.takeIf(String::isNotBlank)?.let {
                        ErrorBanner("执行异常", taskErrorLabel(it))
                    }
                    HorizontalDivider(color = AutomationDivider)
                    Text("开始：${formatTaskTime(history.startedAtMillis)}", style = MaterialTheme.typography.bodyMedium)
                    Text("更新：${formatTaskTime(history.updatedAtMillis)}", style = MaterialTheme.typography.bodyMedium)
                    Text("搜索词组：${history.queryCount} · 用户上限：${history.maxUsers}", style = MaterialTheme.typography.bodyMedium)
                    if (history.searchQueries.isNotEmpty()) {
                        Text("实际搜索词：${history.searchQueries.joinToString("、")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    history.region?.takeIf(String::isNotBlank)?.let {
                        Text("地区前缀：$it", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (history.blockedKeywords.isNotEmpty()) {
                        Text("屏蔽词：${history.blockedKeywords.joinToString("、")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("执行模式：${executionModeLabel(history.executionMode)}", style = MaterialTheme.typography.bodyMedium)
                    if (history.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE) {
                        Text(
                            "评论匹配：已读取正文 ${history.commentBodiesRead} · 命中正文 ${history.matchedCommentBodies} · 可安全处理 ${history.actionableCommentCandidates}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricItem("已处理", history.handledCount.toString(), AutomationBlue)
                        MetricItem("已跳过", history.skippedCount.toString(), AutomationWarning)
                        MetricItem("失败", history.failedCount.toString(), AutomationError)
                    }
                    Text(
                        "命中屏蔽词：${history.filteredCount} · 重复用户：${history.duplicateCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = {
                            AutomationStore.saveTaskDraft(history.toReusableDraft())
                            onReuse()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp),
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
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("重新执行（空消息安全探测）") }
                        retryMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Text(
                "用户处理结果（${records.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
            matchMode = config.matchMode,
            maxVideos = config.maxVideos,
            maxUsersPerVideo = config.maxUsersPerVideo,
            skipPinnedVideos = config.skipPinnedVideos,
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
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                Text(
                    visibleName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
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
    PageKind.LIVE_ROOM -> "直播内容（已划走）"
    PageKind.LIVE_ROOM_SESSION -> "直播间（已退出）"
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
    var overlayAllowed by remember { mutableStateOf(FloatingOverlayService.canDrawOverlays(context)) }
    val licenseState by AuthStore.uiState.collectAsState()
    val authConfig by AuthStore.session.collectAsState()
    var authorizedRemoteTaskIds by rememberSaveable { mutableStateOf(RemoteTaskAuthorizationStore.loadRaw(context)) }
    var remoteAuthorizationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var actionIntervalMillis by rememberSaveable {
        mutableStateOf(AutomationActionIntervalSettingsStore.loadRaw(context))
    }
    var actionIntervalMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var actionIntervalMessageIsError by rememberSaveable { mutableStateOf(false) }
    val actionIntervalError = AutomationActionIntervalPolicy.validationError(actionIntervalMillis)
    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(AutomationSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSectionCard(title = "账号与授权", icon = Icons.Default.Cloud) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("授权状态", style = MaterialTheme.typography.labelLarge)
                    StatusBadge(
                        licenseStatusLabel(licenseState.status),
                        when (licenseState.status) {
                            LicenseStatus.VERIFIED -> StatusTone.SUCCESS
                            LicenseStatus.REJECTED -> StatusTone.ERROR
                            else -> StatusTone.WARNING
                        },
                    )
                }
                Text(
                    licenseState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (
                    authConfig?.accountName?.takeIf(String::isNotBlank)
                        ?: authConfig?.accountUsername?.takeIf(String::isNotBlank)
                )?.let { account ->
                    Text(
                        "当前登录账号：$account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "密码不会保存；授权与设备哈希仅以 Android Keystore 加密保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { AuthStore.verifyNow() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("立即验证 heartbeat")
                }
            }
        }
        SettingsSectionCard(title = "自动化服务", icon = Icons.Default.Accessibility) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsRow("目标应用", "抖音")
                SettingsRow("执行方式", "Android 无障碍服务")
                SettingsRow("安全模式", "空白消息探测")
                HorizontalDivider()
                Text("全局动作间隔", style = MaterialTheme.typography.labelLarge)
                Text(
                    "留空：不额外节流；填写范围 1000–5000ms，下一次动作生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactOutlinedTextField(
                    value = actionIntervalMillis,
                    onValueChange = { value ->
                        actionIntervalMillis = AutomationActionIntervalPolicy.sanitizeInput(value)
                        actionIntervalMessage = null
                        actionIntervalMessageIsError = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "全局动作间隔（ms）",
                    placeholder = "留空：不启用",
                    taskForm = true,
                    singleLine = true,
                )
                actionIntervalError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    enabled = actionIntervalError == null,
                    onClick = {
                        val saveError = AutomationActionIntervalSettingsStore.save(context, actionIntervalMillis)
                        actionIntervalMessageIsError = saveError != null
                        actionIntervalMessage = saveError ?: if (actionIntervalMillis.isBlank()) {
                            "已关闭全局动作间隔"
                        } else {
                            "已保存 ${actionIntervalMillis}ms；下一次动作起生效"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("保存全局动作间隔")
                }
                actionIntervalMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (actionIntervalMessageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                OutlinedButton(
                    onClick = { openAccessibilitySettings(context) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("无障碍服务设置")
                }
            }
        }
        SettingsSectionCard(title = "任务悬浮窗", icon = Icons.Default.Info) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("显示任务进度", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (overlayAllowed) "已允许：启动任务后会显示在抖音上方" else "未允许：任务仍可执行，但不会显示悬浮窗",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(
                        if (overlayAllowed) "已开启" else "未开启",
                        if (overlayAllowed) StatusTone.SUCCESS else StatusTone.WARNING,
                    )
                }
                OutlinedButton(
                    onClick = {
                        FloatingOverlayService.openPermissionSettings(context)
                        overlayAllowed = FloatingOverlayService.canDrawOverlays(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (overlayAllowed) "管理悬浮窗权限" else "开启悬浮窗权限")
                }
            }
        }
        SettingsSectionCard(title = "远程任务", icon = Icons.Default.Cloud) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("显示远程任务", style = MaterialTheme.typography.labelLarge)
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
                HorizontalDivider()
                Text(
                    "仅清单中的远程任务可以领取、启动或恢复；本机新建任务无需配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactOutlinedTextField(
                    value = authorizedRemoteTaskIds,
                    onValueChange = { authorizedRemoteTaskIds = it },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = "远程授权任务 ID",
                    placeholder = "例如 101, 102",
                    taskForm = true,
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        val saved = RemoteTaskAuthorizationStore.save(context, authorizedRemoteTaskIds)
                        authorizedRemoteTaskIds = saved.sorted().joinToString(",")
                        remoteAuthorizationMessage = "已保存 ${saved.size} 个远程授权任务 ID"
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("保存远程任务授权清单")
                }
                remoteAuthorizationMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SettingsSectionCard(title = "开发者选项", icon = Icons.Default.Tune) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "截图、OCR、节点树和原始事件仅在诊断页显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("打开诊断页") }
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
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
    AutomationPhase.SUSPENDED_BEFORE_START -> "已挂起，等待导航"
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
