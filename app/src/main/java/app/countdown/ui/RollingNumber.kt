package app.countdown.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Zahl, deren Ziffern einzeln wie ein Zählwerk abrollen. Die Ziffern werden von rechts an
 * ihre Position gebunden, damit die Einerstelle immer dieselbe Komposition behält.
 */
@Composable
fun RollingNumber(
    value: Long,
    minDigits: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val text = value.toString().padStart(minDigits, '0')
    Row(modifier) {
        text.forEachIndexed { index, ch ->
            key(text.length - index) { RollingDigit(ch, style, color) }
        }
    }
}

@Composable
fun RollingDigit(digit: Char, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    // Weiche Maske oben/unten: Ziffern gleiten ein und aus, statt hart abgeschnitten zu werden.
    val mask = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.16f to Color.Black,
            0.84f to Color.Black,
            1f to Color.Transparent,
        )
    }
    AnimatedContent(
        targetState = digit,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            },
        transitionSpec = {
            (slideInVertically(tween(620, easing = EmphasizedDecelerate)) { it } +
                fadeIn(tween(360, delayMillis = 70)) +
                scaleIn(tween(620, easing = EmphasizedDecelerate), initialScale = 0.82f)) togetherWith
                (slideOutVertically(tween(460, easing = EmphasizedAccelerate)) { -it } +
                    fadeOut(tween(260)) +
                    scaleOut(tween(460, easing = EmphasizedAccelerate), targetScale = 0.82f))
        },
        label = "digit",
    ) { d ->
        Text(d.toString(), style = style, color = color, maxLines = 1, softWrap = false)
    }
}
