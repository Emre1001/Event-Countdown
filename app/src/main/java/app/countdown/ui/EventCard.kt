package app.countdown.ui

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.EventMode
import app.countdown.model.EventStyle
import app.countdown.model.Fill
import app.countdown.model.TextEffects
import app.countdown.model.UnitValue
import app.countdown.render.Fonts
import app.countdown.render.WidgetPainter

private fun unitLabel(context: Context, unit: CountdownUnit, value: Long): String {
    val res = when (unit) {
        CountdownUnit.YEARS -> R.plurals.unit_years
        CountdownUnit.MONTHS -> R.plurals.unit_months
        CountdownUnit.WEEKS -> R.plurals.unit_weeks
        CountdownUnit.DAYS -> R.plurals.unit_days
        CountdownUnit.HOURS -> R.plurals.unit_hours
        CountdownUnit.MINUTES -> R.plurals.unit_minutes
        CountdownUnit.SECONDS -> R.plurals.unit_seconds
    }
    return context.resources.getQuantityString(res, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
}

/** Text mit Füllung (Farbe/Verlauf/Glas), optionaler Kontur und Schatten – wie im Widget. */
@Composable
fun StyledText(
    text: String,
    fill: Fill,
    effects: TextEffects,
    fontSize: TextUnit,
    family: FontFamily,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { fontSize.toPx() } * effects.outlineWidth * 0.045f * 2f
    val blurPx = with(density) { fontSize.toPx() } * effects.shadowBlur * 0.012f
    val shadow = if (effects.shadow) Shadow(Color(effects.shadowColor), Offset(0f, blurPx * 0.35f), blurPx) else null
    Box(modifier) {
        if (strokePx > 0f) {
            Text(
                text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(effects.outlineColor).copy(alpha = alpha), fontSize = fontSize, fontFamily = family, fontWeight = weight,
                    drawStyle = Stroke(width = strokePx, join = androidx.compose.ui.graphics.StrokeJoin.Round), shadow = shadow,
                ),
            )
        }
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                brush = fill.brush(), alpha = alpha, fontSize = fontSize, fontFamily = family, fontWeight = weight,
                shadow = if (strokePx > 0f) null else shadow,
            ),
        )
    }
}

@Composable
fun CountdownDisplay(
    values: List<UnitValue>,
    style: EventStyle,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val family = Fonts.family(style.font)
    val digitFamily = Fonts.family(style.digitFont)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labels = values.map { unitLabel(context, it.unit, it.value) }
        // Grobe Breitenschätzung in Schrifthöhen: so passen auch viele Einheiten auf 320 dp Breite.
        val gapEm = if (style.separators) 0.62f else 0.42f
        var em = (values.size - 1) * gapEm
        values.forEachIndexed { i, uv ->
            val digits = uv.value.toString().padStart(if (i == 0) 1 else 2, '0').length
            em += maxOf(digits * 0.62f, if (style.showLabels) labels[i].length * 0.17f else 0f)
        }
        val sizeDp = minOf(numberSize.value, maxWidth.value / em).coerceAtLeast(18f)
        // Zahlen sind dp-basiert (unabhängig von der Systemschrift); Beschriftungen wachsen moderat mit.
        val numberSp = with(density) { sizeDp.dp.toSp() }
        val labelSp = with(density) { (sizeDp * 0.27f).coerceAtLeast(11f).dp.toSp() } * density.fontScale.coerceAtMost(1.3f)
        val fx = style.effects
        val numberPx = with(density) { numberSp.toPx() }
        val strokePx = numberPx * fx.outlineWidth * 0.045f * 2f
        val blurPx = numberPx * fx.shadowBlur * 0.012f
        val shadow = if (fx.shadow) Shadow(Color(fx.shadowColor), Offset(0f, blurPx * 0.35f), blurPx) else null
        val base = TextStyle(
            fontSize = numberSp,
            lineHeight = numberSp * 1.18f,
            fontFamily = digitFamily,
            fontWeight = Fonts.weight(style.numberWeight),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        val fillStyle = base.copy(shadow = if (strokePx > 0f) null else shadow)
        val strokeStyle = base.copy(drawStyle = Stroke(width = strokePx, join = androidx.compose.ui.graphics.StrokeJoin.Round), shadow = shadow)
        val plain = style.numbers.isPlain
        Row(horizontalArrangement = Arrangement.spacedBy((sizeDp * (if (style.separators) 0.2f else 0.42f)).dp)) {
            values.forEachIndexed { index, uv ->
                key(uv.unit) {
                    Column {
                        Box {
                            if (strokePx > 0f) {
                                RollingNumber(uv.value, if (index == 0) 1 else 2, strokeStyle, Color(fx.outlineColor), style.animation)
                            }
                            RollingNumber(
                                uv.value,
                                if (index == 0) 1 else 2,
                                fillStyle,
                                if (plain) Color(style.numbers.first) else Color.White,
                                style.animation,
                                Modifier.fillOverlay(style.numbers),
                            )
                        }
                        if (style.showLabels) {
                            StyledText(labels[index], style.accent, fx, labelSp, family, Fonts.weight(style.textWeight))
                        }
                    }
                }
                if (style.separators && index < values.lastIndex) {
                    StyledText(":", style.accent, fx, numberSp, digitFamily, Fonts.weight(style.numberWeight), alpha = 0.75f)
                }
            }
        }
    }
}

/** Die App-Karte und zugleich die Vorschau im Editor – nutzt denselben Stil wie die Widgets. */
@Composable
fun EventCard(
    event: CountdownEvent,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 46.sp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val style = event.style
    val now = LocalNow.current.value
    val target = event.effectiveTarget(now)
    val remaining = Countdown.remaining(target, now, event.orderedUnits)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(target, event.mode, locale) {
        if (event.mode == EventMode.SINCE) Countdown.formatDay(event.targetMillis, locale) else Countdown.formatDate(target, locale)
    }
    val radius = style.cornerRadius.coerceIn(0f, 44f)
    val shape = RoundedCornerShape(radius.dp)
    val photo = style.photos.firstOrNull { it != null }
    val thumb by rememberStoredImage(photo?.image)
    val family = Fonts.family(style.font)
    val cardStyle = remember(style) { style.copy(cardInset = 0f) }
    val dateLine = when {
        event.mode == EventMode.SINCE -> stringResource(R.string.since_prefix) + " " + date
        remaining.isPast -> stringResource(R.string.elapsed) + " · " + date
        else -> date
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(shape)
            .drawBehind {
                val d = density
                drawIntoCanvas { c ->
                    WidgetPainter.drawCard(context, c.nativeCanvas, RectF(0f, 0f, size.width, size.height), cardStyle, d, radius * d)
                }
            }
            .then(if (style.borderWidth <= 0f && style.background.material == app.countdown.model.Material.COLOR) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape) else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    StyledText(
                        event.title.ifBlank { stringResource(R.string.untitled) }, style.accent, style.effects,
                        17.sp, family, Fonts.weight(style.textWeight),
                    )
                    StyledText(dateLine, style.accent, style.effects, 13.sp, family, FontWeight.Normal, alpha = 0.75f)
                    if (event.note.isNotBlank()) {
                        StyledText(event.note, style.accent, style.effects, 13.sp, family, FontWeight.Normal, alpha = 0.75f)
                    }
                }
                val t = thumb
                if (t != null && photo != null) {
                    Image(
                        t, null,
                        Modifier
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape((photo.corner.coerceIn(0f, 0.5f) * 44f).dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            CountdownDisplay(remaining.values, style, numberSize)
        }
    }
}
