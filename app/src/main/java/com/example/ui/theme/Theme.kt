package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PyCyan,
    onPrimary = Color(0xFF031427),
    primaryContainer = Color(0xFF1F436B),
    onPrimaryContainer = Color(0xFFD3E7FF),
    secondary = PyGreen,
    onSecondary = Color(0xFF022B0C),
    secondaryContainer = Color(0xFF144D24),
    onSecondaryContainer = Color(0xFFC4F2CD),
    tertiary = PyPurple,
    background = PyDarkBackground,
    onBackground = TerminalText,
    surface = PyDarkSurface,
    onSurface = TerminalText,
    surfaceVariant = PyDarkSurfaceVariant,
    onSurfaceVariant = TerminalMuted,
    outline = PyCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    secondary = Color(0xFF1A7F37),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEAEEF2),
    outline = Color(0xFFD0D7DE)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek dark developer mode
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
