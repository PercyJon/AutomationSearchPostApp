package com.example.douyinautomation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.douyinautomation.BuildConfig
import com.example.douyinautomation.CommentRegressionPreset
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.LoginEndpointPolicy
import com.example.douyinautomation.ui.components.AppLoginButton
import com.example.douyinautomation.ui.components.AppLoginCanvas
import com.example.douyinautomation.ui.components.AppLoginField
import com.example.douyinautomation.ui.components.AppLoginMark
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationLoginOnBlue
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import kotlinx.coroutines.launch

/**
 * The app entry gate is intentionally driven by the encrypted local mobile-license session.
 * A login success updates [AuthStore.session], which atomically replaces this screen with the
 * existing application rather than duplicating navigation state in the login page.
 */
@Composable
internal fun AutomationAppSessionGate(
    initialKeyword: String = "",
    initialSection: String? = null,
    initialCommentTask: Boolean = false,
    autoStartCommentP0: Boolean = false,
    commentP0LaunchToken: Int = 0,
    commentRegressionPreset: CommentRegressionPreset? = null,
) {
    val session by AuthStore.session.collectAsState()

    if (session?.isUsable() == true) {
        AppHomeScreen(
            initialKeyword = initialKeyword,
            initialSection = initialSection,
            initialCommentTask = initialCommentTask,
            autoStartCommentP0 = autoStartCommentP0,
            commentP0LaunchToken = commentP0LaunchToken,
            commentRegressionPreset = commentRegressionPreset,
        )
    } else {
        LoginScreen(endpoint = BuildConfig.AUTOMATION_API_ENDPOINT)
    }
}

@Composable
private fun LoginScreen(endpoint: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val rememberedEndpoint by AuthStore.lastEndpoint.collectAsState()
    val showEndpointField = LoginEndpointPolicy.shouldShowEndpointField(endpoint)
    var typedEndpoint by rememberSaveable {
        mutableStateOf(LoginEndpointPolicy.normalize(rememberedEndpoint).orEmpty())
    }
    val resolvedEndpoint = LoginEndpointPolicy.resolvedEndpoint(
        buildConfig = endpoint,
        remembered = rememberedEndpoint,
        typed = typedEndpoint,
    )
    val endpointReady = resolvedEndpoint != null
    var username by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately stay out of SavedState so process restore cannot retain one.
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val origin = resolvedEndpoint ?: return
        if (submitting) return
        scope.launch {
            submitting = true
            message = null
            runCatching {
                AuthStore.login(
                    context = context,
                    endpoint = origin,
                    username = username,
                    password = password,
                )
            }.onSuccess {
                password = ""
            }.onFailure { error ->
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "登录失败，请检查账号或密码后重试"
            }
            submitting = false
        }
    }

    AppLoginCanvas(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AutomationSpacing.Page, vertical = AutomationSpacing.Hero),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Section)) {
                AppLoginMark(icon = Icons.Default.Lock)
                Column(verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Tight)) {
                    Text(
                        "您好,",
                        style = MaterialTheme.typography.displaySmall,
                        color = AutomationLoginOnBlue,
                    )
                    Text(
                        "欢迎来到自动化获客助手",
                        style = MaterialTheme.typography.titleMedium,
                        color = AutomationLoginOnBlue.copy(alpha = 0.9f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Card)) {
                    if (showEndpointField) {
                        AppLoginField(
                            value = typedEndpoint,
                            onValueChange = {
                                typedEndpoint = it
                                message = null
                            },
                            placeholder = "服务地址",
                            leadingIcon = Icons.Default.Lock,
                            enabled = !submitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    }
                    AppLoginField(
                        value = username,
                        onValueChange = {
                            username = it
                            message = null
                        },
                        placeholder = "账号",
                        leadingIcon = Icons.Default.Person,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    AppLoginField(
                        value = password,
                        onValueChange = {
                            password = it
                            message = null
                        },
                        placeholder = "密码",
                        leadingIcon = Icons.Default.Lock,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = AutomationLoginOnBlue.copy(alpha = 0.78f),
                                modifier = Modifier
                                    .size(AutomationSizing.Icon)
                                    .clickable(enabled = !submitting) {
                                        passwordVisible = !passwordVisible
                                    },
                            )
                        },
                    )
                    if (showEndpointField && !endpointReady) {
                        Text(
                            "请填写以 https:// 开头的服务地址后再登录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AutomationLoginOnBlue.copy(alpha = 0.9f),
                        )
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = AutomationError)
                    }
                    AppLoginButton(
                        text = if (submitting) "登录中" else "登录",
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = endpointReady && username.isNotBlank() && password.isNotBlank(),
                        loading = submitting,
                    )
                }
            }
            Text(
                "账号首次登录会绑定本设备。密码不会保存，本机仅加密保存设备绑定后的移动端授权。",
                modifier = Modifier.padding(top = AutomationSpacing.Section),
                style = MaterialTheme.typography.bodySmall,
                color = AutomationLoginOnBlue.copy(alpha = 0.78f),
            )
        }
    }
}
