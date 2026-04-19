package com.android.jizhangmiao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Night,
    primaryContainer = ForestGreen,
    onPrimaryContainer = Paper,
    secondary = ClayOrange,
    onSecondary = Paper,
    tertiary = HoneyGold,
    onTertiary = Night,
    background = Night,
    onBackground = Paper,
    surface = NightSurface,
    onSurface = Paper,
    surfaceVariant = ColorTokens.darkSurfaceVariant,
    onSurfaceVariant = ColorTokens.darkOnSurfaceVariant,
    outline = ColorTokens.darkOutline,
    error = ColorTokens.error
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Paper,
    primaryContainer = ForestGreenSoft,
    onPrimaryContainer = Ink,
    secondary = ClayOrange,
    onSecondary = Paper,
    tertiary = HoneyGold,
    onTertiary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = ColorTokens.lightSurface,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Mist,
    outline = OutlineSoft,
    error = ColorTokens.error
)

@Composable
fun JizhangmiaoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val lightSurface = Color(0xFFFDF9F2)
    val darkSurfaceVariant = Color(0xFF22302B)
    val darkOnSurfaceVariant = Color(0xFFBCC8C2)
    val darkOutline = Color(0xFF42534C)
    val error = Color(0xFFB74D3E)
}
