package app.countdown.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Einheitliche Bewegungssprache: Eintritt ease-out, Austritt ease-in, Interaktion mit leichtem Überschwingen. */
object Motion {
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val EaseIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
    const val STAGGER_MS = 50L
    const val MAX_STAGGER_STEPS = 8
}

/** Klick mit federndem Eindrücken (kurz zusammenziehen, dann zurückfedern) statt Ripple. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Listeneinträge gleiten gestaffelt (50 ms Versatz, maximal 8 Stufen) von unten ein. */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(index, Motion.MAX_STAGGER_STEPS) * Motion.STAGGER_MS)
        progress.animateTo(1f, tween(450, easing = Motion.EaseOut))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

