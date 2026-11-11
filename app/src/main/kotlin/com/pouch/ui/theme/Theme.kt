package com.pouch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PouchColorScheme = darkColorScheme(
    primary = PouchColors.Violet,
    onPrimary = Color.White,
    primaryContainer = PouchColors.VioletDark,
    onPrimaryContainer = Color.White,
    secondary = PouchColors.Success,
    tertiary = PouchColors.Warning,
    background = PouchColors.Background,
    onBackground = PouchColors.TextPrimary,
    surface = PouchColors.Surface,
    onSurface = PouchColors.TextPrimary,
    surfaceVariant = PouchColors.SurfaceElevated,
    onSurfaceVariant = PouchColors.TextSecondary,
    surfaceContainer = PouchColors.Surface,
    surfaceContainerHigh = PouchColors.SurfaceElevated,
    surfaceContainerLow = PouchColors.Surface,
    outline = PouchColors.Border,
    outlineVariant = PouchColors.Border,
    error = PouchColors.Destructive,
    onError = Color.White,
)

private val PouchShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Dark-only theme matching the Figma design system. */
@Composable
fun PouchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PouchColorScheme,
        typography = PouchTypography,
        shapes = PouchShapes,
        content = content,
    )
}
