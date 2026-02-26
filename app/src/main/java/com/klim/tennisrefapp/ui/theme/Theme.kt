package com.klim.tennisrefapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF73508F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADFFE),
    onPrimaryContainer = Color(0xFF28143A),
    secondary = Color(0xFFB35387),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCD7EB),
    onSecondaryContainer = Color(0xFF3A1128),
    tertiary = Color(0xFF5A5FA9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE1FF),
    onTertiaryContainer = Color(0xFF14194F),
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF1B1822),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1B1822),
    surfaceVariant = Color(0xFFE8E1F0),
    onSurfaceVariant = Color(0xFF4A4358),
    outline = Color(0xFF7C748B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD2BDFF),
    onPrimary = Color(0xFF33204B),
    primaryContainer = Color(0xFF4B3466),
    onPrimaryContainer = Color(0xFFF0E6FF),
    secondary = Color(0xFFF2B8D8),
    onSecondary = Color(0xFF4D1F3B),
    secondaryContainer = Color(0xFF663350),
    onSecondaryContainer = Color(0xFFFFD9EC),
    tertiary = Color(0xFFC5C9FF),
    onTertiary = Color(0xFF2A2E67),
    tertiaryContainer = Color(0xFF414785),
    onTertiaryContainer = Color(0xFFE2E4FF),
    background = Color(0xFF0F1118),
    onBackground = Color(0xFFE9E1F2),
    surface = Color(0xFF151823),
    onSurface = Color(0xFFE9E1F2),
    surfaceVariant = Color(0xFF2A2D3A),
    onSurfaceVariant = Color(0xFFC8C3D4),
    outline = Color(0xFF918AA5),
)

@Composable
fun TennisRefTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
