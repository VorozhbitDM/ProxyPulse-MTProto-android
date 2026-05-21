package com.proxypulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2AABEE),
    onPrimary = Color.White,
    secondary = Color(0xFF1A3A52),
    surface = Color(0xFFF5F7FA),
    onSurface = Color(0xFF1A1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BC0F8),
    onPrimary = Color(0xFF0D1B2A),
    secondary = Color(0xFF8ECAE6),
    surface = Color(0xFF121820),
    onSurface = Color(0xFFE8EEF4)
)

@Composable
fun ProxyPulseTheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
