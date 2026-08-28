package com.example.douyinautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueSoft
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationErrorSurface
import com.example.douyinautomation.ui.theme.AutomationNeutralSurface
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationSuccessSurface
import com.example.douyinautomation.ui.theme.AutomationText
import com.example.douyinautomation.ui.theme.AutomationTextSecondary
import com.example.douyinautomation.ui.theme.AutomationTextTertiary
import com.example.douyinautomation.ui.theme.AutomationWarning
import com.example.douyinautomation.ui.theme.AutomationWarningSurface

enum class AppStatusTone {
    Neutral,
    Primary,
    Pending,
    Success,
    Failure,
}

@Composable
fun AppStatusBadge(
    text: String,
    tone: AppStatusTone,
    modifier: Modifier = Modifier,
) {
    val (foreground, background) = when (tone) {
        AppStatusTone.Neutral -> AutomationTextSecondary to AutomationNeutralSurface
        AppStatusTone.Pending -> AutomationWarning to AutomationWarningSurface
        AppStatusTone.Success -> AutomationSuccess to AutomationSuccessSurface
        AppStatusTone.Failure -> AutomationError to AutomationErrorSurface
        AppStatusTone.Primary -> AutomationBlue to AutomationBlueSoft
    }
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
    )
}

@Composable
fun AppUnderlineTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) AutomationBlue else AutomationTextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (selected) AutomationBlue else Color.Transparent),
                )
            }
        }
    }
}

/** Flat record row with a hairline divider. Layout from app5; used for 用户处理结果. */
@Composable
fun AppRecordLine(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    badgeText: String? = null,
    badgeTone: AppStatusTone = AppStatusTone.Neutral,
    trailing: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    titleColor: Color = AutomationText,
    iconTint: Color = AutomationBlue,
    iconBackground: Color = AutomationBlueSoft,
    rowMinHeight: Dp = Dp.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowMinHeight != Dp.Unspecified) {
                        Modifier.height(rowMinHeight)
                    } else {
                        Modifier.padding(vertical = AutomationSpacing.Compact)
                    },
                )
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(AutomationSizing.Icon),
                    )
                }
                Spacer(modifier = Modifier.width(AutomationSpacing.Item))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                    )
                    if (!badgeText.isNullOrBlank()) {
                        AppStatusBadge(text = badgeText, tone = badgeTone)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AutomationTextSecondary,
                    )
                }
            }
            if (!trailing.isNullOrBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AutomationText,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AutomationTextTertiary,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = AutomationDivider, thickness = 0.5.dp)
        }
    }
}
