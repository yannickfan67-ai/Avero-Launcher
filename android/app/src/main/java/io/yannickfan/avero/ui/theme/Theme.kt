package io.yannickfan.avero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF74E6FF),
    onPrimary = Color(0xFF002027),
    primaryContainer = Color(0xFF123845),
    onPrimaryContainer = Color(0xFFD3F5FF),
    secondary = Color(0xFFAEB7FF),
    secondaryContainer = Color(0xFF2A305A),
    background = Color(0xFF080B10),
    surface = Color(0xFF10141B),
    surfaceVariant = Color(0xFF1A202A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00677A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8EAF5),
    onPrimaryContainer = Color(0xFF062F38),
    secondary = Color(0xFF4E5AA7),
    secondaryContainer = Color(0xFFE1E4FF),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EEF3)
)

@Composable
fun AveroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
