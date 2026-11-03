package app.countdown.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.EventStyle
import app.countdown.model.StickerShape
import app.countdown.model.UnitValue
import app.countdown.render.Fonts

private fun unitLabel(context: Context, unit: CountdownUnit, value: Long): String {
    val res = when (unit) {
        CountdownUnit.DAYS -> R.plurals.unit_days
        CountdownUnit.HOURS -> R.plurals.unit_hours
        CountdownUnit.MINUTES -> R.plurals.unit_minutes
        CountdownUnit.SECONDS -> R.plurals.unit_seconds
    }
    return context.resources.getQuantityString(res, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labels = values.map { unitLabel(context, it.unit, it.value) }
        // Grobe Breitenschätzung in Schrifthöhen: so passen auch 4 Einheiten auf 320 dp Breite.
        var em = (values.size - 1) * 0.42f
        values.forEachIndexed { i, uv ->
            val digits = uv.value.toString().padStart(if (i == 0) 1 else 2, '0').length
            em += maxOf(digits * 0.6f, if (style.showLabels) labels[i].length * 0.17f else 0f)
        }
        val sizeDp = minOf(numberSize.value, maxWidth.value / em).coerceAtLeast(20f)
        // Zahlen sind dp-basiert (unabhängig von der Systemschrift); Beschriftungen wachsen moderat mit.
        val numberSp = with(density) { sizeDp.dp.toSp() }
        val labelSp = with(density) { (sizeDp * 0.27f).coerceAtLeast(11f).dp.toSp() } * density.fontScale.coerceAtMost(1.3f)
        val gradientNumbers = style.numbers.isGradient
        val numberStyle = TextStyle(
            fontSize = numberSp,
            lineHeight = numberSp * 1.18f,
            fontFamily = family,
            fontWeight = Fonts.weight(style.numberWeight),
            letterSpacing = (-0.5).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        val labelStyle = TextStyle(
            brush = style.accent.brush(),
            fontSize = labelSp,
            fontFamily = family,
            fontWeight = Fonts.weight(style.textWeight),
        )
        Row(horizontalArrangement = Arrangement.spacedBy((sizeDp * 0.42f).dp)) {
            values.forEachIndexed { index, uv ->
                key(uv.unit) {
                    Column {
                        RollingNumber(
                            uv.value,
                            if (index == 0) 1 else 2,
                            numberStyle,
                            if (gradientNumbers) Color.White else Color(style.numbers.first),
                            Modifier.fillOverlay(style.numbers),
                        )
                        if (style.showLabels) Text(labels[index], style = labelStyle, maxLines = 1)
                    }
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
    val style = event.style
    val now = LocalNow.current.value
    val remaining = Countdown.remaining(event.targetMillis, now, event.orderedUnits)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(event.targetMillis, locale) { Countdown.formatDate(event.targetMillis, locale) }
    val shape = RoundedCornerShape(style.cornerRadius.coerceIn(0f, 44f).dp)
    val bgImage by rememberStoredImage(style.image)
    val sticker by rememberStoredImage(style.sticker)
    val family = Fonts.family(style.font)
    val image = bgImage

    Box(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(shape)
            .then(
                if (image == null) Modifier.background(style.background.brush(), alpha = style.backgroundAlpha)
                else Modifier,
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
    ) {
        if (image != null) {
            Image(image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = style.backgroundAlpha)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = style.imageDim * style.backgroundAlpha)))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        event.title.ifBlank { stringResource(R.string.untitled) },
                        style = TextStyle(
                            brush = style.accent.brush(),
                            fontSize = 17.sp,
                            fontFamily = family,
                            fontWeight = Fonts.weight(style.textWeight),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (remaining.isPast) stringResource(R.string.elapsed) + " · " + date else date,
                        style = TextStyle(brush = style.accent.brush(), alpha = 0.72f, fontSize = 13.sp, fontFamily = family),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val s = sticker
                if (s != null) {
                    Image(
                        s, null,
                        Modifier
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .clip(if (style.stickerShape == StickerShape.CIRCLE) CircleShape else RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            CountdownDisplay(remaining.values, style, numberSize)
        }
    }
}
