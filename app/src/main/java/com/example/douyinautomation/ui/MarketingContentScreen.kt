package com.example.douyinautomation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import com.example.douyinautomation.automation.AuthConfig
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.MarketingContentResolver
import com.example.douyinautomation.automation.MarketingContentStore
import com.example.douyinautomation.automation.MarketingContentType
import com.example.douyinautomation.ui.components.AppPrimaryButton
import com.example.douyinautomation.ui.components.AppRadio
import com.example.douyinautomation.ui.components.AppSegmentedTab
import com.example.douyinautomation.ui.components.AppSwitchRow
import com.example.douyinautomation.ui.components.AppTextField
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationTextSecondary
import kotlinx.coroutines.launch

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
            .fillMaxSize(),
    ) {
        AppSegmentedTab(
            options = listOf("B端客户", "评论用户"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AutomationSpacing.Page)
                .padding(top = AutomationSpacing.Item),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AutomationSpacing.Page, vertical = AutomationSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Card),
        ) {
            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) AutomationError else AutomationTextSecondary,
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
            AppSwitchRow(
                checked = current.randomEnabled,
                onCheckedChange = { enabled ->
                    drafts = drafts + (currentType to current.copy(randomEnabled = enabled))
                },
                label = "随机发送",
            )
        }
        AppPrimaryButton(
            text = "保存",
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
            loading = saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AutomationSpacing.Page)
                .padding(bottom = AutomationSpacing.Compact),
        )
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
        horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
    ) {
        AppRadio(
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.padding(top = AutomationSpacing.Compact),
        )
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = AutomationSizing.Control * 2),
            placeholder = "输入第 ${index + 1} 条营销内容",
            singleLine = false,
            minLines = 3,
        )
    }
}
