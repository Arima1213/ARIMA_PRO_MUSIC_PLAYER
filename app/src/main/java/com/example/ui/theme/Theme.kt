package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ScreenHorizontalPadding = 20.dp

// Forced Dark-Only Color Scheme for pure analog luxury vibe
private val DarkColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = Color.Black,
    primaryContainer = AmberMuted,
    onPrimaryContainer = TextPrimary,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundSurface,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderDefault,
    outlineVariant = BorderSubtle,
    error = VUPeak
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
