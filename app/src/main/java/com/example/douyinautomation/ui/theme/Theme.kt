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
    primaryContainer = AutomationBlueSoft,
    onPrimaryContainer = AutomationBlueDark,
    secondary = AutomationTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = AutomationField,
    onSecondaryContainer = AutomationText,
    background = AutomationPage,
    onBackground = AutomationText,
    surface = AutomationCard,
    onSurface = AutomationText,
    surfaceVariant = AutomationField,
    onSurfaceVariant = AutomationTextSecondary,
    outline = AutomationFieldBorder,
    outlineVariant = AutomationDivider,
    error = AutomationError,
    onError = Color.White,
    errorContainer = AutomationErrorSurface,
    onErrorContainer = AutomationError,
)

private val AutomationTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Normal, fontSize = 24.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Normal, fontSize = 18.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
        bodyLarge = bodyLarge.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = bodyMedium.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp),
        bodySmall = bodySmall.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Normal, fontSize = 10.sp),
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
