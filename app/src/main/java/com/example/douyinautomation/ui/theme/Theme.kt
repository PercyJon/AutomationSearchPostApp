package com.example.douyinautomation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AutomationColorScheme = lightColorScheme(
    primary = AutomationBlue,
    onPrimary = Color.White,
    primaryContainer = AutomationBlueLight,
    onPrimaryContainer = AutomationBlueDark,
    secondary = AutomationTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = AutomationNeutralSurface,
    onSecondaryContainer = AutomationText,
    background = AutomationPage,
    onBackground = AutomationText,
    surface = AutomationCard,
    onSurface = AutomationText,
    surfaceVariant = AutomationNeutralSurface,
    onSurfaceVariant = AutomationTextSecondary,
    outline = AutomationDivider,
    error = AutomationError,
    onError = Color.White,
    errorContainer = AutomationErrorSurface,
    onErrorContainer = AutomationError,
)

private val AutomationTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp),
    )
}

@Composable
fun AutomationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutomationColorScheme,
        typography = AutomationTypography,
        shapes = AutomationShapes,
        content = content,
    )
}
