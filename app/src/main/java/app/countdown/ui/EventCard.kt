package app.countdown.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.UnitValue

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
    numberColor: Color,
    labelColor: Color,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labels = values.map { unitLabel(context, it.unit, it.value) }
        // Grobe Breitenschätzung in Schrifthöhen: so passen auch 4 Einheiten auf 320 dp Breite.
        var em = (values.size - 1) * 0.42f
        values.forEachIndexed { i, uv ->
            val digits = uv.value.toString().padStart(if (i == 0) 1 else 2, '0').length
            em += maxOf(digits * 0.58f, labels[i].length * 0.17f)
        }
        val sizeDp = minOf(numberSize.value, maxWidth.value / em).coerceAtLeast(20f)
        // Zahlen sind dp-basiert (unabhängig von der Systemschrift); Beschriftungen wachsen moderat mit.
        val numberSp = with(density) { sizeDp.dp.toSp() }
        val labelSp = with(density) { (sizeDp * 0.27f).coerceAtLeast(11f).dp.toSp() } * density.fontScale.coerceAtMost(1.3f)
        val numberStyle = TextStyle(
            fontSize = numberSp,
            lineHeight = numberSp * 1.18f,
            fontWeight = FontWeight.Light,
            fontFeatureSettings = "tnum",
            letterSpacing = (-0.5).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        Row(horizontalArrangement = Arrangement.spacedBy((sizeDp * 0.42f).dp)) {
            values.forEachIndexed { index, uv ->
                androidx.compose.runtime.key(uv.unit) {
                    Column {
                        RollingNumber(uv.value, if (index == 0) 1 else 2, numberStyle, numberColor)
                        Text(
                            labels[index],
                            color = labelColor,
                            fontSize = labelSp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Die App-Karte und zugleich die Live-Vorschau im Editor. */
@Composable
fun EventCard(
    event: CountdownEvent,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 46.sp,
    onClick: (() -> Unit)? = null,
) {
    val spec = tween<Color>(350)
    val bg by animateColorAsState(Color(event.background).copy(alpha = event.backgroundAlpha), spec, label = "bg")
    val fg by animateColorAsState(Color(event.textColor), spec, label = "fg")
    val accent by animateColorAsState(Color(event.accent), spec, label = "accent")

    val now = LocalNow.current.value
    val remaining = Countdown.remaining(event.targetMillis, now, event.orderedUnits)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = remember(event.targetMillis, locale) { Countdown.formatDate(event.targetMillis, locale) }

    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text(
            event.title.ifBlank { stringResource(R.string.untitled) },
            color = accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            if (remaining.isPast) stringResource(R.string.elapsed) + " · " + date else date,
            color = accent.copy(alpha = 0.7f),
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(20.dp))
        CountdownDisplay(remaining.values, fg, accent, numberSize)
    }
}
