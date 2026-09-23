package io.yannickfan.avero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78E5FF),
    onPrimary = Color(0xFF002028),
    primaryContainer = Color(0xFF103944),
    onPrimaryContainer = Color(0xFFD2F5FF),
    secondary = Color(0xFFB1B8FF),
    secondaryContainer = Color(0xFF2C315A),
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
