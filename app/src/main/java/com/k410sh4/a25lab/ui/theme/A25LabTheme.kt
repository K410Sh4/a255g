package com.k410sh4.a25lab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E3FF),
    onPrimaryContainer = Color(0xFF211A52),
    secondary = Color(0xFF3457D5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5FF),
    onSecondaryContainer = Color(0xFF0B1B52),
    tertiary = Color(0xFF007C7C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F3F0),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFF0EFF6),
    onSurfaceVariant = Color(0xFF5C5D67),
    outline = Color(0xFF7A7B86),
    outlineVariant = Color(0xFFD8D8E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9BEFF),
    onPrimary = Color(0xFF342673),
    primaryContainer = Color(0xFF493B8C),
    onPrimaryContainer = Color(0xFFE8E3FF),
    secondary = Color(0xFFB7C4FF),
    onSecondary = Color(0xFF13296D),
    secondaryContainer = Color(0xFF2E4285),
    onSecondaryContainer = Color(0xFFDDE5FF),
    tertiary = Color(0xFF82DAD6),
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF005050),
    onTertiaryContainer = Color(0xFFB8F3F0),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE7E7EF),
    surface = Color(0xFF151821),
    onSurface = Color(0xFFE7E7EF),
    surfaceVariant = Color(0xFF20242F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909C),
    outlineVariant = Color(0xFF3C404C),
)

@Composable
fun A25LabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
