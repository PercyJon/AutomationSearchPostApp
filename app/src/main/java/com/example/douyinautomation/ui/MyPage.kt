package com.example.douyinautomation.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.FloatingOverlayService
import com.example.douyinautomation.automation.LicenseStatus
import com.example.douyinautomation.ui.components.AppCard
import com.example.douyinautomation.ui.components.AppNoticeRow
import com.example.douyinautomation.ui.components.AppRecordLine
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueSoft
import com.example.douyinautomation.ui.theme.AutomationCardShape
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationErrorSurface
import com.example.douyinautomation.ui.theme.AutomationLoginOnBlue
import com.example.douyinautomation.ui.theme.AutomationPill
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationText
import kotlinx.coroutines.launch

@Composable
internal fun MyPage(
    padding: PaddingValues,
    onOpenMarketing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenComponentGallery: () -> Unit,
    onSignOut: suspend () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val licenseState by AuthStore.uiState.collectAsState()
    val automationState by AutomationStore.uiState.collectAsState()
    var overlayAllowed by remember { mutableStateOf(FloatingOverlayService.canDrawOverlays(context)) }
    val account = licenseState.accountName
        ?: AuthStore.currentConfig()?.accountUsername
        ?: "未登录"
    val signedIn = AuthStore.currentConfig() != null
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signOutError by rememberSaveable { mutableStateOf<String?>(null) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openAccessibilitySettings = remember(context) {
        {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val openOverlaySettings = remember(context) {
        { FloatingOverlayService.openPermissionSettings(context) }
    }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = FloatingOverlayService.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("退出登录") },
            text = {
                Text(
                    signOutError
                        ?: "将先通知服务端解除本机设备绑定，再清除本机已加密保存的授权信息。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signingOut = true
                        signOutError = null
                        scope.launch {
                            runCatching { onSignOut() }
                                .onSuccess { showSignOutConfirmation = false }
                                .onFailure { error ->
                                    signOutError = "退出未同步：${error.message ?: "请检查网络后重试"}"
                                }
                            signingOut = false
                        }
                    },
                    enabled = !signingOut,
                ) {
                    Text(if (signingOut) "退出中…" else "退出", color = AutomationError)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !signingOut,
                    onClick = {
                        signOutError = null
                        showSignOutConfirmation = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AutomationSpacing.Page, vertical = AutomationSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Card),
    ) {
        AccountHero(
            account = account,
            status = licenseState.status,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
        ) {
            AppNoticeRow(
                title = "自动化服务",
                enabled = automationState.serviceConnected,
                onEnable = openAccessibilitySettings,
                modifier = Modifier.weight(1f),
            )
            AppNoticeRow(
                title = "悬浮窗",
                enabled = overlayAllowed,
                onEnable = openOverlaySettings,
                modifier = Modifier.weight(1f),
            )
        }

        AppCard {
            MyMenuLine(
                icon = Icons.Default.Devices,
                title = "账号与设备",
                trailing = myStatusLabel(licenseState.status),
                onClick = onOpenSettings,
            )
            MyMenuLine(
                icon = Icons.Default.Edit,
                title = "营销内容编辑",
                showDivider = false,
                onClick = onOpenMarketing,
            )
        }
        AppCard {
            MyMenuLine(
                icon = Icons.Default.Settings,
                title = "自动化设置",
                onClick = onOpenSettings,
            )
            MyMenuLine(
                icon = Icons.Default.Accessibility,
                title = "服务与诊断",
                onClick = onOpenDiagnostics,
            )
            MyMenuLine(
                icon = Icons.Default.Info,
                title = "组件预览",
                onClick = onOpenComponentGallery,
            )
            MyMenuLine(
                icon = Icons.Default.Cloud,
                title = "远程任务设置",
                showDivider = false,
                onClick = onOpenSettings,
            )
        }
        if (signedIn) {
            AppCard {
                MyMenuLine(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    titleColor = AutomationError,
                    iconTint = AutomationError,
                    iconBackground = AutomationErrorSurface,
                    showChevron = false,
                    showDivider = false,
                    onClick = { showSignOutConfirmation = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(AutomationSpacing.Card))
    }
}

@Composable
private fun MyMenuLine(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    trailing: String? = null,
    titleColor: Color = AutomationText,
    iconTint: Color = AutomationBlue,
    iconBackground: Color = AutomationBlueSoft,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
) {
    AppRecordLine(
        title = title,
        subtitle = "",
        modifier = Modifier.padding(horizontal = AutomationSpacing.Card),
        leadingIcon = icon,
        trailing = trailing,
        showChevron = showChevron,
        showDivider = showDivider,
        titleColor = titleColor,
        iconTint = iconTint,
        iconBackground = iconBackground,
        rowMinHeight = AutomationSizing.Tile,
        onClick = onClick,
    )
}

@Composable
private fun AccountHero(account: String, status: LicenseStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AutomationCardShape,
        color = AutomationBlue,
        contentColor = AutomationLoginOnBlue,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AutomationSpacing.Hero,
                vertical = AutomationSpacing.Section,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(AutomationSizing.Tile)
                    .clip(CircleShape)
                    .background(AutomationLoginOnBlue.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(AutomationSizing.TileIcon),
                )
            }
            Spacer(modifier = Modifier.width(AutomationSpacing.Card))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Tight),
            ) {
                Text(account, style = MaterialTheme.typography.titleLarge)
                Text(
                    "自动化获客助手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AutomationLoginOnBlue.copy(alpha = 0.82f),
                )
            }
            AccountStatusPill(status = status)
        }
    }
}

@Composable
private fun AccountStatusPill(status: LicenseStatus) {
    Surface(
        color = AutomationLoginOnBlue.copy(alpha = 0.18f),
        contentColor = AutomationLoginOnBlue,
        shape = AutomationPill,
    ) {
        Text(
            text = myStatusLabel(status),
            modifier = Modifier.padding(
                horizontal = AutomationSpacing.Item,
                vertical = AutomationSpacing.Tight,
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun myStatusLabel(status: LicenseStatus): String = when (status) {
    LicenseStatus.NOT_CONFIGURED -> "未登录"
    LicenseStatus.VERIFIED -> "已授权"
    LicenseStatus.REJECTED -> "需重新登录"
    LicenseStatus.TEMPORARILY_UNAVAILABLE -> "服务暂不可用"
}
