package app.countdown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.countdown.model.Align
import app.countdown.model.CountdownEvent
import app.countdown.model.Element
import app.countdown.model.ElementType
import app.countdown.model.WidgetFormat
import app.countdown.render.WidgetPainter
import kotlin.math.abs
import kotlin.math.min

/** Ruhiger "Hintergrundbild"-Verlauf, damit Transparenz und Ecken des Widgets sichtbar sind. */
val WallpaperBrush = Brush.linearGradient(listOf(Color(0xFF2B2D42), Color(0xFF4A4E69), Color(0xFF9A8C98)))

/**
 * Widget-Vorschau mit exakt demselben Renderer wie das echte Widget. Im interaktiven Modus lassen
 * sich Elemente antippen, verschieben (ein Finger) und skalieren (zwei Finger); sie rasten an der
 * Mitte und an den Rändern ein.
 */
@Composable
fun WidgetDesigner(
    event: CountdownEvent,
    format: WidgetFormat,
    interactive: Boolean,
    selected: ElementType?,
    onSelect: (ElementType?) -> Unit,
    onElementChange: (ElementType, Element) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now = LocalNow.current
    val result = remember { WidgetPainter.Result() }
    val current by rememberUpdatedState(event)
    val select by rememberUpdatedState(onSelect)
    val change by rememberUpdatedState(onElementChange)
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val aspect = when (format) {
        WidgetFormat.WIDE -> 2.1f
        WidgetFormat.BAR -> 4.2f
        WidgetFormat.SQUARE -> 1f
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(WallpaperBrush, RoundedCornerShape(32.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, if (format == WidgetFormat.BAR) maxWidth else 172.dp * aspect)
            val h = w / aspect
            val gestures = if (!interactive) Modifier else Modifier.pointerInput(format) {
                val slack = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = result.bounds.entries.lastOrNull { (_, r) ->
                        down.position.x in (r.left - slack)..(r.right + slack) &&
                            down.position.y in (r.top - slack)..(r.bottom + slack)
                    }?.key
                    select(hit)
                    if (hit == null) return@awaitEachGesture
                    val start = current.style.layout(format)[hit]
                    val cw = size.width.toFloat()
                    val ch = size.height.toFloat()
                    val mx = 0.07f * min(cw, ch) / cw
                    val my = 0.07f * min(cw, ch) / ch
                    var rawX = start.x
                    var rawY = start.y
                    var scale = start.scale
                    var rotation = start.rotation
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                        val pan = ev.calculatePan()
                        val zoom = ev.calculateZoom()
                        val turn = if (hit.rotatable) ev.calculateRotation() else 0f
                        if (pan == Offset.Zero && zoom == 1f && turn == 0f) continue
                        rotation = (rotation + turn).coerceIn(-45f, 45f)
                        rawX += pan.x / cw
                        rawY += pan.y / ch
                        scale = (scale * zoom).coerceIn(0.4f, 2.5f)

                        val b = result.bounds[hit]
                        val bw = (b?.width() ?: 0f) / cw
                        val bh = (b?.height() ?: 0f) / ch
                        val left = when (start.align) {
                            Align.START -> rawX
                            Align.CENTER -> rawX - bw / 2f
                            Align.END -> rawX - bw
                        }
                        var sx = rawX
                        var gx = false
                        val cx = left + bw / 2f
                        when {
                            abs(cx - 0.5f) < 0.025f -> { sx += 0.5f - cx; gx = true }
                            abs(left - mx) < 0.02f -> sx += mx - left
                            abs(left + bw - (1f - mx)) < 0.02f -> sx += (1f - mx) - (left + bw)
                        }
                        var sy = rawY
                        var gy = false
                        val cy = rawY + bh / 2f
                        when {
                            abs(cy - 0.5f) < 0.025f -> { sy = 0.5f - bh / 2f; gy = true }
                            abs(rawY - my) < 0.02f -> sy = my
                            abs(rawY + bh - (1f - my)) < 0.02f -> sy = 1f - my - bh
                        }
                        guideX = gx
                        guideY = gy
                        // Fotos dürfen über den Rand hinaus, Texte bleiben im Widget.
                        val loose = hit.isPhoto
                        val snappedRotation = if (abs(rotation) < 3f) 0f else rotation
                        change(
                            hit,
                            start.copy(
                                x = if (loose) sx.coerceIn(-0.4f, 1.4f) else sx.coerceIn(0f, 1f),
                                y = if (loose) sy.coerceIn(-0.4f, 1.4f) else sy.coerceIn(0f, 1f),
                                scale = scale,
                                rotation = snappedRotation,
                            ),
                        )
                        ev.changes.forEach { it.consume() }
                    }
                    guideX = false
                    guideY = false
                }
            }
            Canvas(Modifier.size(w, h).then(gestures)) {
                val n = now.value
                drawIntoCanvas { c ->
                    WidgetPainter.paint(
                        context, c.nativeCanvas, size.width, size.height, event, format, n,
                        locale = locale, result = result, placeholders = interactive,
                    )
                }
                if (interactive) {
                    val pad = 5.dp.toPx()
                    selected?.let { t ->
                        result.bounds[t]?.let { r ->
                            drawRoundRect(
                                primary,
                                Offset(r.left - pad, r.top - pad),
                                Size(r.width() + 2 * pad, r.height() + 2 * pad),
                                CornerRadius(8.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                            )
                        }
                    }
                    val guide = primary.copy(alpha = 0.8f)
                    if (guideX) drawLine(guide, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                    if (guideY) drawLine(guide, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                }
            }
        }
    }
}
