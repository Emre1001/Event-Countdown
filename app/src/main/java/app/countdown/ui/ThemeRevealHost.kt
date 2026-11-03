package app.countdown.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import app.countdown.data.AppSettings
import app.countdown.data.ThemePref
import kotlin.math.hypot
import kotlin.math.max

private class RevealShape(val center: Offset, val radius: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/**
 * Löst das aufgelöste Theme (System/Hell/Dunkel) auf. Bei einem Wechsel per Schalter wächst das neue
 * Theme als Kreis aus dem gedrückten Schalter über den ganzen Bildschirm; darunter bleibt die alte
 * Oberfläche stehen. `content` wird dabei kurz doppelt komponiert (`overlay` = obere Kopie).
 */
@Composable
fun ThemeRevealHost(content: @Composable (dark: Boolean, overlay: Boolean) -> Unit) {
    val pref by AppSettings.theme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val target = when (pref) {
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
        ThemePref.SYSTEM -> systemDark
    }
    var shown by remember { mutableStateOf(target) }
    var revealing by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        val o = AppSettings.takeRevealOrigin()
        if (o == null || size == IntSize.Zero) {
            shown = target // z. B. das System wechselt das Theme: ohne Kreis
            return@LaunchedEffect
        }
        origin = o
        revealing = true
        try {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
            shown = target
        } finally {
            revealing = false
        }
    }

    // Statusleisten-Symbole passen zum App-Theme, nicht zum System-Theme.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !shown
                isAppearanceLightNavigationBars = !shown
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        content(shown, false)
        if (revealing) {
            val maxRadius = hypot(
                max(origin.x, size.width - origin.x),
                max(origin.y, size.height - origin.y),
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    shape = RevealShape(origin, maxRadius * progress.value)
                    clip = true
                },
            ) { content(target, true) }
        }
    }
}
