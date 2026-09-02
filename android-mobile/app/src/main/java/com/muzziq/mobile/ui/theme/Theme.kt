package com.muzziq.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Design system MuzziQ Android — palette reprise du serveur web
 * (src/app/globals.css : --brand #1ed760, --bg #0a0a0c) pour une identité
 * cohérente entre le web et le mobile. Thème sombre custom, jamais le
 * Material par défaut visible tel quel (§56.1).
 */
object MuzziQColors {
    val Brand = Color(0xFF1ED760)
    val BrandDark = Color(0xFF17B850)
    val Bg = Color(0xFF0A0A0C)
    val BgElevated = Color(0xFF0F0F12)
    val Surface = Color(0xFF141417)
    val TextPrimary = Color(0xFFF5F7F5)
    val TextMuted = Color(0xFF9AA09A)
    val TextFaint = Color(0xFF60655F)
}

private val MuzziQDarkScheme = darkColorScheme(
    primary = MuzziQColors.Brand,
    onPrimary = Color(0xFF00210A),
    secondary = MuzziQColors.BrandDark,
    background = MuzziQColors.Bg,
    onBackground = MuzziQColors.TextPrimary,
    surface = MuzziQColors.Surface,
    onSurface = MuzziQColors.TextPrimary,
    surfaceVariant = MuzziQColors.BgElevated,
    onSurfaceVariant = MuzziQColors.TextMuted,
)

@Composable
fun MuzziQTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MuzziQDarkScheme, content = content)
}
