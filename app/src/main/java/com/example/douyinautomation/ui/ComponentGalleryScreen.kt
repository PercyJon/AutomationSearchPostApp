package com.example.douyinautomation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.components.AppButtonRow
import com.example.douyinautomation.ui.components.AppCard
import com.example.douyinautomation.ui.components.AppChoiceChip
import com.example.douyinautomation.ui.components.AppColorBlock
import com.example.douyinautomation.ui.components.AppDetailHero
import com.example.douyinautomation.ui.components.AppEmptyState
import com.example.douyinautomation.ui.components.AppFilterPills
import com.example.douyinautomation.ui.components.AppHeroStats
import com.example.douyinautomation.ui.components.AppHistoryRow
import com.example.douyinautomation.ui.components.AppIconTile
import com.example.douyinautomation.ui.components.AppKeyValue
import com.example.douyinautomation.ui.components.AppKeyValueGroups
import com.example.douyinautomation.ui.components.AppLoginButton
import com.example.douyinautomation.ui.components.AppLoginCanvas
import com.example.douyinautomation.ui.components.AppLoginField
import com.example.douyinautomation.ui.components.AppLoginMark
import com.example.douyinautomation.ui.components.AppNoticeRow
import com.example.douyinautomation.ui.components.AppPageHero
import com.example.douyinautomation.ui.components.AppPillAction
import com.example.douyinautomation.ui.components.AppPrimaryButton
import com.example.douyinautomation.ui.components.AppRecordLine
import com.example.douyinautomation.ui.components.AppSectionTitle
import com.example.douyinautomation.ui.components.AppSegmentedTab
import com.example.douyinautomation.ui.components.AppSheetHeader
import com.example.douyinautomation.ui.components.AppStatColumn
import com.example.douyinautomation.ui.components.AppStatusTone
import com.example.douyinautomation.ui.components.AppSwitchRow
import com.example.douyinautomation.ui.components.AppTextField
import com.example.douyinautomation.ui.components.AppUnderlineTabs
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueDark
import com.example.douyinautomation.ui.theme.AutomationPage
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComponentGalleryScreen(onBack: () -> Unit) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var fieldValue by rememberSaveable { mutableStateOf("") }
    var chipSelected by rememberSaveable { mutableStateOf(true) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var resultTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var switchOn by rememberSaveable { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = AutomationPage,
        topBar = {
            TopAppBar(
                title = { Text("组件预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AutomationPage),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AutomationSpacing.Page)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Card),
        ) {
            GallerySection("分段页签 · pic1") {
                AppSectionTitle("选择生成方式")
                AppSegmentedTab(
                    options = listOf("图片生成", "视频生成"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                )
            }

            GallerySection("表单卡片 · pic1") {
                AppCard {
                    Column(
                        modifier = Modifier.padding(AutomationSpacing.Card),
                        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact),
                    ) {
                        AppTextField(
                            value = fieldValue,
                            onValueChange = { fieldValue = it },
                            placeholder = "描述你想生成的画面",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact)) {
                            AppChoiceChip(
                                text = "智能扩写",
                                selected = chipSelected,
                                onClick = { chipSelected = !chipSelected },
                                leadingIcon = Icons.Default.AutoAwesome,
                            )
                            AppChoiceChip(
                                text = "模型 3.0",
                                selected = false,
                                onClick = {},
                                dropdown = true,
                            )
                        }
                    }
                }
                AppPrimaryButton(
                    text = "立即生成",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            GallerySection("标题 / 输入 / 双按钮 · pic2") {
                AppSheetHeader(
                    title = "网页 AI 总结",
                    subtitle = "粘贴链接，将为您分析并总结网页内容",
                    onClose = {},
                )
                AppTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "请在这里粘贴网页链接",
                    leadingIcon = Icons.Default.Link,
                )
                AppButtonRow(
                    secondaryText = "一键粘贴",
                    onSecondary = {},
                    primaryText = "提交",
                    onPrimary = {},
                )
                AppSwitchRow(
                    checked = switchOn,
                    onCheckedChange = { switchOn = it },
                    label = "开启进阶分析",
                    caption = "开启后可使用更多分析选项",
                )
                AppNoticeRow(
                    icon = Icons.Default.Lock,
                    title = "自动化服务",
                    enabled = switchOn,
                    onEnable = { switchOn = true },
                )
            }

            GallerySection("首页顶部色块") {
                AppPageHero(
                    title = "自动化获客助手",
                    subtitle = "晚上好，欢迎回来",
                    columns = listOf(
                        AppStatColumn("100", "任务"),
                        AppStatColumn("277", "已发送"),
                        AppStatColumn("100%", "完成"),
                    ),
                    edgeToStatusBar = false,
                )
            }

            GallerySection("功能色块 4:3") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Card),
                ) {
                    AppColorBlock(
                        title = "B端私信",
                        subtitle = "搜索用户并安全探测",
                        icon = Icons.Default.Business,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    AppColorBlock(
                        title = "评论私信",
                        subtitle = "评论区触达功能",
                        icon = Icons.Default.Forum,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        containerColor = AutomationBlueDark,
                    )
                }
            }

            GallerySection("登录结构 · app1") {
                AppLoginCanvas(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(AutomationSpacing.Page),
                        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
                    ) {
                        AppLoginMark(icon = Icons.Default.Lock)
                        AppLoginField(value = "", onValueChange = {}, placeholder = "账号")
                        AppLoginButton(text = "登录", onClick = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            GallerySection("图标按钮 · pic3") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AppIconTile(
                        icon = Icons.Default.PhotoLibrary,
                        label = "相册",
                        iconBackground = AutomationBlue,
                        onClick = {},
                    )
                    AppIconTile(
                        icon = Icons.Default.Folder,
                        label = "本地文件夹",
                        iconBackground = AutomationWarning,
                        onClick = {},
                    )
                    AppIconTile(
                        icon = Icons.Default.Description,
                        label = "文档",
                        iconBackground = AutomationSuccess,
                        onClick = {},
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
                ) {
                    AppPillAction(
                        icon = Icons.Default.Description,
                        label = "PDF 文档分析",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    AppPillAction(
                        icon = Icons.Default.PlayCircle,
                        label = "视频分析",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            GallerySection("数据与历史列表 · pic4 布局") {
                AppHeroStats(
                    headline = "150",
                    headlineIcon = Icons.Default.Bolt,
                    columns = listOf(
                        AppStatColumn("80", "已完成"),
                        AppStatColumn("20", "失败"),
                        AppStatColumn("50", "进行中"),
                    ),
                )
                AppFilterPills(
                    options = listOf("全部", "已完成", "失败"),
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                )
                AppHistoryRow(
                    title = "红木茶桌 · B 端私信",
                    subtitle = "搜索 红木茶桌 · 屏蔽 无",
                    timestamp = "2026-08-27 16:42",
                    badgeText = "已完成",
                    badgeTone = AppStatusTone.Success,
                )
                AppHistoryRow(
                    title = "红木沙发 · B 端私信",
                    subtitle = "搜索 红木沙发 · 屏蔽 无",
                    timestamp = "2026-08-27 16:16",
                    badgeText = "执行失败",
                    badgeTone = AppStatusTone.Failure,
                )
            }

            GallerySection("空状态 · app2") {
                AppEmptyState(
                    icon = Icons.Default.Inbox,
                    message = "今日没有任务",
                    caption = "点击功能中的 B 端私信创建任务",
                    actionText = "立即创建",
                    onAction = {},
                )
            }

            GallerySection("任务详情排版 · app4") {
                AppDetailHero(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = "B 端私信",
                    value = "红木茶桌",
                )
                AppKeyValueGroups(
                    groups = listOf(
                        listOf(
                            AppKeyValue("当前状态", "已完成", AutomationSuccess),
                            AppKeyValue("用户数", "5"),
                            AppKeyValue("开始时间", "2026-08-27 16:40"),
                        ),
                        listOf(
                            AppKeyValue("搜索词", "红木茶桌"),
                            AppKeyValue("发送模式", "空探针"),
                        ),
                    ),
                )
            }

            GallerySection("处理结果列表 · app5") {
                AppUnderlineTabs(
                    options = listOf("全部", "成功", "失败"),
                    selectedIndex = resultTabIndex,
                    onSelect = { resultTabIndex = it },
                )
                AppRecordLine(
                    leadingIcon = Icons.Default.Person,
                    title = "梧空室界木作旗舰店",
                    badgeText = "失败",
                    badgeTone = AppStatusTone.Failure,
                    subtitle = "2026-08-27 16:41",
                    trailing = "店铺",
                    showChevron = true,
                    onClick = {},
                )
                AppRecordLine(
                    leadingIcon = Icons.Default.Person,
                    title = "室内设计师阿木",
                    badgeText = "成功",
                    badgeTone = AppStatusTone.Success,
                    subtitle = "2026-08-27 16:42",
                    trailing = "私信",
                    showChevron = true,
                    onClick = {},
                )
                AppRecordLine(
                    leadingIcon = Icons.Default.Person,
                    title = "红木家具严选",
                    badgeText = "跳过",
                    badgeTone = AppStatusTone.Pending,
                    subtitle = "2026-08-27 16:43",
                    trailing = "身份",
                    showChevron = true,
                    showDivider = false,
                    onClick = {},
                )
            }

            Spacer(modifier = Modifier.height(AutomationSpacing.Page))
        }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
