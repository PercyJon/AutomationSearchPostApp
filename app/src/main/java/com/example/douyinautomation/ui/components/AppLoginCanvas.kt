package com.example.douyinautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationCardShape
import com.example.douyinautomation.ui.theme.AutomationLoginBottom
import com.example.douyinautomation.ui.theme.AutomationLoginField
import com.example.douyinautomation.ui.theme.AutomationLoginMid
import com.example.douyinautomation.ui.theme.AutomationLoginOnBlue
import com.example.douyinautomation.ui.theme.AutomationLoginTop
import com.example.douyinautomation.ui.theme.AutomationPill
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing

/** Login canvas from app1. Form controls on this canvas use [AppLoginField] and [AppLoginButton]. */
@Composable
fun AppLoginCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    UseOnBlueStatusBars()
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AutomationLoginTop,
                        AutomationLoginMid,
                        AutomationLoginBottom,
                    ),
                ),
            ),
        content = content,
    )
}

@Composable
fun AppLoginMark(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AutomationSizing.Tile)
            .border(1.dp, AutomationLoginOnBlue.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AutomationLoginOnBlue,
            modifier = Modifier.size(AutomationSizing.TileIcon),
        )
    }
}

@Composable
fun AppLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AutomationSizing.Control)
            .clip(AutomationCardShape)
            .background(AutomationLoginField)
            .padding(horizontal = AutomationSpacing.Card, vertical = AutomationSpacing.Item),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(AutomationSizing.Icon),
                tint = AutomationLoginOnBlue.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
        }
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = AutomationLoginOnBlue),
                ),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(AutomationLoginOnBlue),
            )
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AutomationLoginOnBlue.copy(alpha = 0.55f),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
            trailing()
        }
    }
}

@Composable
fun AppLoginButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    CompactControl {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.heightIn(min = AutomationSizing.Control),
            shape = AutomationPill,
            colors = ButtonDefaults.buttonColors(
                containerColor = AutomationLoginOnBlue,
                contentColor = AutomationBlue,
                disabledContainerColor = AutomationLoginOnBlue.copy(alpha = 0.55f),
                disabledContentColor = AutomationBlue.copy(alpha = 0.7f),
            ),
            contentPadding = PaddingValues(horizontal = AutomationSpacing.Card, vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = AutomationBlue,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
