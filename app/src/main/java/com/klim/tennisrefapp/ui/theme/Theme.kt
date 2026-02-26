package com.klim.tennisrefapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFE53978),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E7),
    onPrimaryContainer = Color(0xFF3D001D),
    secondary = Color(0xFFD81B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E6),
    onSecondaryContainer = Color(0xFF3A0019),
    tertiary = Color(0xFFEF6C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0C4),
    onTertiaryContainer = Color(0xFF331600),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FB8),
    onPrimary = Color(0xFF5A0031),
    primaryContainer = Color(0xFF8B1C4F),
    onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = Color(0xFFFF8BB2),
    onSecondary = Color(0xFF5A002B),
    secondaryContainer = Color(0xFF8A1C47),
    onSecondaryContainer = Color(0xFFFFD8E6),
    tertiary = Color(0xFFFFB77F),
    onTertiary = Color(0xFF512400),
    tertiaryContainer = Color(0xFF743700),
    onTertiaryContainer = Color(0xFFFFE0C4),
)

@Composable
fun TennisRefTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
