package com.example.douyinautomation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AuthConfig
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.MarketingContentResolver
import com.example.douyinautomation.automation.MarketingContentStore
import com.example.douyinautomation.automation.MarketingContentType
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationSpacing
import kotlinx.coroutines.launch

private val EditorCardShape = RoundedCornerShape(6.dp)
private val RadioSize = 20.dp
private val RadioInnerSize = 10.dp

@Composable
internal fun MarketingContentScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val types = MarketingContentType.entries
    var drafts by remember {
        mutableStateOf(types.associateWith { MarketingContentStore.profile(it) })
    }
    var saving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MarketingContentStore.initialize(context)
        drafts = types.associateWith { MarketingContentStore.profile(it) }
        val signedIn = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable) != null
        if (!signedIn) {
            statusIsError = false
            statusMessage = "当前未登录，内容仅保存在本机。登录后会与后台同步。"
            return@LaunchedEffect
        }
        runCatching { MarketingContentStore.syncFromServer() }
            .onSuccess { bundle ->
                drafts = types.associateWith { bundle.profile(it) }
                statusIsError = false
                statusMessage = "已从后台同步营销内容"
            }
            .onFailure {
                statusIsError = true
                statusMessage = "后台同步失败，仍可编辑本机内容"
            }
    }

    val currentType = types[selectedTab]
    val current = drafts.getValue(currentType)

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            divider = { HorizontalDivider(color = AutomationDivider) },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("B端客户营销内容") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("评论用户营销内容") },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AutomationSpacing.Page, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            repeat(MarketingContentResolver.SLOT_COUNT) { index ->
                MarketingSlotRow(
                    index = index,
                    text = current.slots.getOrElse(index) { "" },
                    selected = current.selectedIndex == index,
                    onSelect = {
                        drafts = drafts + (currentType to current.copy(selectedIndex = index))
                    },
                    onTextChange = { value ->
                        val slots = current.slots.toMutableList()
                        slots[index] = value.take(MarketingContentResolver.MAX_SLOT_CHARS)
                        drafts = drafts + (currentType to current.copy(slots = slots))
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EditorCardShape)
                    .background(AutomationCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("随机发送", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后，任务启动时会从已填写的内容中随机选一条并冻结。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = current.randomEnabled,
                    onCheckedChange = { enabled ->
                        drafts = drafts + (currentType to current.copy(randomEnabled = enabled))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = AutomationBlue,
                    ),
                )
            }
        }
        Button(
            onClick = {
                saving = true
                scope.launch {
                    val result = MarketingContentStore.saveProfile(current)
                    saving = false
                    result.onSuccess { saved ->
                        drafts = drafts + (saved.contentType to saved)
                        val signedIn = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable) != null
                        statusIsError = false
                        statusMessage = when {
                            !signedIn -> "已保存到本机。登录后可同步到后台。"
                            saved.randomEnabled && saved.filledCount() < 2 ->
                                "已保存。有效内容不足 2 条时随机不会生效。"
                            else -> "已保存并同步到后台"
                        }
                    }.onFailure {
                        statusIsError = true
                        statusMessage = "保存失败，请稍后重试"
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AutomationSpacing.Page, vertical = 8.dp)
                .height(46.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AutomationBlue),
        ) {
            Text(if (saving) "保存中…" else "保存")
        }
    }
}

@Composable
private fun MarketingSlotRow(
    index: Int,
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(RadioSize)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) AutomationBlue else AutomationDivider,
                    shape = CircleShape,
                )
                .clickable(onClick = onSelect)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(RadioInnerSize)
                        .clip(CircleShape)
                        .background(AutomationBlue),
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .height(96.dp),
            label = { Text("内容 ${index + 1}") },
            placeholder = { Text("输入第 ${index + 1} 条营销内容") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AutomationBlue,
                focusedLabelColor = AutomationBlue,
            ),
            shape = RoundedCornerShape(6.dp),
        )
    }
}
