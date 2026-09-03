package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArushiDarkColorScheme = darkColorScheme(
    primary = AmberGlow,
    onPrimary = Color.Black,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = Color.White,
    secondary = DeepOrange,
    onSecondary = Color.Black,
    secondaryContainer = SurfaceCard,
    onSecondaryContainer = Color.White,
    tertiary = GoldWarm,
    onTertiary = Color.Black,
    background = DarkObsidian,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardBorder
)

@Composable
fun ArushiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArushiDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ArushiTheme(content = content)
}
