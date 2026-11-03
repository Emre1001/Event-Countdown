package app.countdown.ui

import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.countdown.model.DigitAnimation
import kotlin.math.PI
import kotlin.math.sin

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Zahl, deren Ziffern einzeln animiert wechseln. Die Ziffern werden von rechts an ihre Position
 * gebunden, damit die Einerstelle immer dieselbe Komposition behält.
 */
@Composable
fun RollingNumber(
    value: Long,
    minDigits: Int,
    style: TextStyle,
    color: Color,
    animation: DigitAnimation = DigitAnimation.ROLL_UP,
    modifier: Modifier = Modifier,
) {
    val text = value.toString().padStart(minDigits, '0')
    Row(modifier) {
        text.forEachIndexed { index, ch ->
            key(text.length - index) { AnimatedDigit(ch, style, color, animation) }
        }
    }
}

@Composable
fun AnimatedDigit(digit: Char, style: TextStyle, color: Color, animation: DigitAnimation) {
    when (animation) {
        DigitAnimation.NONE -> Text(digit.toString(), style = style, color = color, maxLines = 1, softWrap = false)
        DigitAnimation.FLIP -> TwoPhaseDigit(digit, style, color, melt = false)
        DigitAnimation.MELT -> TwoPhaseDigit(digit, style, color, melt = true)
        else -> TransitionDigit(digit, style, color, animation)
    }
}

@Composable
private fun TransitionDigit(digit: Char, style: TextStyle, color: Color, animation: DigitAnimation) {
    // Weiche Maske oben/unten: Ziffern gleiten ein und aus, statt hart abgeschnitten zu werden.
    val mask = remember {
        Brush.verticalGradient(0f to Color.Transparent, 0.16f to Color.Black, 0.84f to Color.Black, 1f to Color.Transparent)
    }
    val rolling = animation == DigitAnimation.ROLL_UP || animation == DigitAnimation.ROLL_DOWN
    AnimatedContent(
        targetState = digit,
        modifier = if (!rolling) Modifier else Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            },
        transitionSpec = { spec(animation) },
        label = "digit",
    ) { d ->
        Text(d.toString(), style = style, color = color, maxLines = 1, softWrap = false)
    }
}

private fun spec(animation: DigitAnimation): ContentTransform = when (animation) {
    DigitAnimation.ROLL_DOWN ->
        (slideInVertically(tween(620, easing = EmphasizedDecelerate)) { -it } + fadeIn(tween(360, delayMillis = 70)) +
            scaleIn(tween(620, easing = EmphasizedDecelerate), initialScale = 0.82f)) togetherWith
            (slideOutVertically(tween(460, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(260)) +
                scaleOut(tween(460, easing = EmphasizedAccelerate), targetScale = 0.82f))
    DigitAnimation.FADE ->
        fadeIn(tween(420, delayMillis = 60)) togetherWith fadeOut(tween(260))
    DigitAnimation.ZOOM ->
        (scaleIn(tween(520, easing = EmphasizedDecelerate), initialScale = 0.45f) + fadeIn(tween(320))) togetherWith
            (scaleOut(tween(380, easing = EmphasizedAccelerate), targetScale = 1.6f) + fadeOut(tween(260)))
    else ->
        (slideInVertically(tween(620, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(360, delayMillis = 70)) +
            scaleIn(tween(620, easing = EmphasizedDecelerate), initialScale = 0.82f)) togetherWith
            (slideOutVertically(tween(460, easing = EmphasizedAccelerate)) { -it } + fadeOut(tween(260)) +
                scaleOut(tween(460, easing = EmphasizedAccelerate), targetScale = 0.82f))
}

/**
 * Klappen (Flip-Board: alte Ziffer klappt weg, neue klappt auf) oder Schmelzen (die alte Ziffer
 * zerfließt nach unten und formt sich zur neuen – auf Android 12+ mit echtem "Goo"-Effekt:
 * Weichzeichnen + harte Alpha-Schwelle verbindet beide Formen flüssig).
 */
@Composable
private fun TwoPhaseDigit(digit: Char, style: TextStyle, color: Color, melt: Boolean) {
    var current by remember { mutableStateOf(digit) }
    var previous by remember { mutableStateOf<Char?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(digit) {
        if (digit != current) {
            previous = current
            current = digit
            progress.snapTo(0f)
            progress.animateTo(1f, tween(if (melt) 820 else 560, easing = FastOutSlowInEasing))
            previous = null
        }
    }
    val t = progress.value
    val prev = previous
    Box(
        modifier = if (melt && prev != null) Modifier.gooey(t) else Modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (prev != null) {
            Text(
                prev.toString(), style = style, color = color, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer {
                    if (melt) {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        alpha = 1f - t
                        scaleY = 1f - 0.55f * t
                        scaleX = 1f + 0.18f * t
                    } else {
                        cameraDistance = 14f * density
                        val a = (t * 2f).coerceAtMost(1f)
                        rotationX = -90f * a
                        alpha = if (t < 0.5f) 1f else 0f
                    }
                },
            )
        }
        Text(
            current.toString(), style = style, color = color, maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer {
                if (prev == null) return@graphicsLayer
                if (melt) {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = t
                    scaleY = 1.35f - 0.35f * t
                    scaleX = 0.85f + 0.15f * t
                    translationY = -size.height * 0.12f * (1f - t)
                } else {
                    cameraDistance = 14f * density
                    val b = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
                    rotationX = 90f * (1f - b)
                    alpha = if (t >= 0.5f) 1f else 0f
                }
            },
        )
    }
}

/** Metaball-Effekt: Weichzeichnen + Alpha-Schwelle, nur während der Animation (Android 12+). */
private fun Modifier.gooey(t: Float): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || t <= 0f || t >= 1f) this
    else graphicsLayer {
        val r = (sin(PI * t).toFloat() * 4.5.dp.toPx()).coerceAtLeast(0.5f)
        val threshold = ColorMatrixColorFilter(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 18f, -7f * 255f,
            ),
        )
        renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
            threshold,
            android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.DECAL),
        ).asComposeRenderEffect()
    }
