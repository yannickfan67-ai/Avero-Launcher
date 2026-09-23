package io.yannickfan.avero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CE7FF),
    secondary = Color(0xFFA8B3FF),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF12151C),
    surfaceVariant = Color(0xFF1A1E27)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00677A),
    secondary = Color(0xFF4E5AA7),
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF3)
)

@Composable
fun AveroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
