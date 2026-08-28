package com.example.douyinautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationCardShape
import com.example.douyinautomation.ui.theme.AutomationField
import com.example.douyinautomation.ui.theme.AutomationLoginOnBlue
import com.example.douyinautomation.ui.theme.AutomationPill
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationSuccessSurface
import com.example.douyinautomation.ui.theme.AutomationText
import com.example.douyinautomation.ui.theme.AutomationTextSecondary
import com.example.douyinautomation.ui.theme.AutomationTileShape
import com.example.douyinautomation.ui.theme.AutomationWarning
import com.example.douyinautomation.ui.theme.AutomationWarningSurface

@Composable
fun AppSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AutomationText,
    )
}

@Composable
fun AppNoticeRow(
    title: String,
    enabled: Boolean,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabledLabel: String = "已开启",
    actionLabel: String = "开启",
) {
    val tone = if (enabled) AutomationSuccess else AutomationWarning
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (enabled) AutomationSuccessSurface else AutomationWarningSurface,
        shape = AutomationCardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AutomationSizing.Tile)
                .then(if (!enabled) Modifier.clickable(onClick = onEnable) else Modifier)
                .padding(horizontal = AutomationSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(AutomationSizing.Icon),
                    tint = tone,
                )
                Spacer(modifier = Modifier.width(AutomationSpacing.Item))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = AutomationText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (enabled) enabledLabel else actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) AutomationSuccess else AutomationBlue,
            )
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AutomationCard,
        shape = AutomationCardShape,
        shadowElevation = 1.dp,
        content = { Column(content = content) },
    )
}

@Composable
fun AppSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AutomationText,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AutomationTextSecondary,
                    )
                }
            }
            if (onClose != null) {
                CompactControl {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(AutomationSizing.Control),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(AutomationSizing.Icon),
                            tint = AutomationTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBackground: Color = AutomationBlue,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Tight),
    ) {
        Box(
            modifier = Modifier
                .shadow(3.dp, AutomationTileShape, clip = false)
                .size(AutomationSizing.Tile)
                .clip(AutomationTileShape)
                .background(AutomationCard),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AutomationSizing.TileIcon)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(AutomationSizing.Icon),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AutomationText,
        )
    }
}

/** Horizontal color-block entry. Width:height is about 4:3. */
@Composable
fun AppColorBlock(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AutomationBlue,
) {
    Box(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(AutomationCardShape)
            .background(containerColor)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(AutomationSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(AutomationSpacing.Tight),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AutomationLoginOnBlue,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AutomationLoginOnBlue.copy(alpha = 0.82f),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AutomationLoginOnBlue.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AutomationSpacing.Item)
                .size(AutomationSizing.BlockIcon),
        )
    }
}

@Composable
fun AppPillAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AutomationPill)
            .background(AutomationField)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(AutomationSizing.Icon),
            tint = AutomationText,
        )
        Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AutomationText,
        )
    }
}
