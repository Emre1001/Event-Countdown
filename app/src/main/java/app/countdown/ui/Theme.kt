package app.countdown.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import app.countdown.data.AppSettings
import app.countdown.data.ThemePref
import kotlinx.coroutines.delay

/** Aktuelle Zeit, einmal pro Sekunde (an der Sekundengrenze) aktualisiert. */
val LocalNow = compositionLocalOf<State<Long>> { mutableLongStateOf(System.currentTimeMillis()) }

@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000 - (value % 1000) + 5)
    }
}

// Ruhige, leicht getönte Grautöne und genau ein Akzent – keine Dynamic Colors, damit der Look stabil bleibt.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7D9CC4),
    onPrimary = Color(0xFF101114),
    primaryContainer = Color(0xFF2A3647),
    onPrimaryContainer = Color(0xFFDCE6F5),
    background = Color(0xFF0E0E11),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF242428),
    onSurfaceVariant = Color(0xFF9A9AA2),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF1B1B20),
    surfaceContainerHighest = Color(0xFF242428),
    outline = Color(0xFF45454C),
    outlineVariant = Color(0x1AFFFFFF),
    error = Color(0xFFE5837A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D5A80),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6F5),
    onPrimaryContainer = Color(0xFF16263B),
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF1A1A1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFEBEBE8),
    onSurfaceVariant = Color(0xFF6B6B70),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEBEBE8),
    outline = Color(0xFFB4B4BA),
    outlineVariant = Color(0x14000000),
    error = Color(0xFFB3261E),
)

@Composable
fun CountdownTheme(dark: Boolean, content: @Composable () -> Unit) {
    val now = rememberNow()
    CompositionLocalProvider(LocalNow provides now) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

/** Gewähltes Theme (System/Hell/Dunkel) aufgelöst – für Bildschirme außerhalb des Reveal-Hosts. */
@Composable
fun rememberDarkTheme(): Boolean {
    val pref by AppSettings.theme.collectAsState()
    return when (pref) {
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
        ThemePref.SYSTEM -> isSystemInDarkTheme()
    }
}
