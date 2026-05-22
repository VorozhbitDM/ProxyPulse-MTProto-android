package com.proxypulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.proxypulse.domain.ProxyEntry

/** Same thresholds as Desktop `ProxyCardControl.GetPingColor`. */
object PingColor {
    val Good = Color(0xFF27AE60)
    val Medium = Color(0xFFE67E22)
    val Bad = Color(0xFFE74C3C)

    fun forMs(ms: Int): Color = when {
        ms < 200 -> Good
        ms <= 600 -> Medium
        else -> Bad
    }
}

@Composable
fun ProxyEntry.pingAccentColor(): Color {
    if (!isAvailable || pingMs == null) {
        return MaterialTheme.colorScheme.outline
    }
    return PingColor.forMs(pingMs!!)
}

@Composable
fun ProxyEntry.pingTextColor(isRechecking: Boolean): Color {
    if (isRechecking) {
        return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    if (!isAvailable || pingMs == null) {
        return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    return pingAccentColor()
}
