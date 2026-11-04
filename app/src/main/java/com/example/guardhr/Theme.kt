package com.example.guardhr

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** 0 — как в системе, 1 — светлая, 2 — тёмная. */
object ThemeState {
    var mode by mutableIntStateOf(0)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFBFD5EE), onPrimaryContainer = Color(0xFF0B2239),
    secondary = Color(0xFFB8901A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFBFD5EE), onSecondaryContainer = Color(0xFF0B2239),
    background = Color(0xFFD9E2EE), onBackground = Color(0xFF111B27),
    surface = Color(0xFFF4F7FB), onSurface = Color(0xFF111B27),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF1F2A37),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF3F9),
    surfaceContainer = Color(0xFFC9D8EA), surfaceContainerHigh = Color(0xFFF4F7FB),
    surfaceContainerHighest = Color(0xFFE3EBF5),
    outline = Color(0xFF5C6B7D)
)

// Тёмная тема — мягкие синие тона, без чисто белого и чёрного
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5B9BDE), onPrimary = Color(0xFF06192E),
    primaryContainer = Color(0xFF244A75), onPrimaryContainer = Color(0xFFD0E2F7),
    secondary = Color(0xFFD9B650), onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFF244A75), onSecondaryContainer = Color(0xFFD0E2F7),
    background = Color(0xFF0D1A2B), onBackground = Color(0xFFC3D2E4),
    surface = Color(0xFF13253B), onSurface = Color(0xFFC3D2E4),
    surfaceVariant = Color(0xFF1A3250), onSurfaceVariant = Color(0xFFB0C1D6),
    surfaceContainerLowest = Color(0xFF0A1523), surfaceContainerLow = Color(0xFF112238),
    surfaceContainer = Color(0xFF16304B), surfaceContainerHigh = Color(0xFF1C3857),
    surfaceContainerHighest = Color(0xFF234266),
    outline = Color(0xFF6F86A3)
)

@Composable
fun GuardTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}
