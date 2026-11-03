package app.countdown.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Gruppierte Fläche mit Haarlinie – der Grundbaustein aller Einstellungen. */
@Composable
internal fun Group(padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
internal fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Beschrifteter Regler mit Wertanzeige. */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

/**
 * Segmentierte Auswahl mit federnd gleitendem Indikator. [onSelect] bekommt zusätzlich den
 * Mittelpunkt des Segments in Fenster-Koordinaten (für den Kreis-Reveal beim Theme-Wechsel).
 */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onSelect: (index: Int, center: Offset) -> Unit,
) {
    val centers = remember { mutableStateMapOf<Int, Offset>() }
    val slots = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val density = LocalDensity.current
    val target = slots[selected]
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(target != null) { if (target != null) ready = true }
    val spec: AnimationSpec<Float> = if (ready) spring(dampingRatio = 0.72f, stiffness = 380f) else snap()
    val x by animateFloatAsState(target?.first?.toFloat() ?: 0f, spec, label = "segX")
    val w by animateFloatAsState(target?.second?.toFloat() ?: 0f, spec, label = "segW")

    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(4.dp),
    ) {
        if (target != null) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .width(with(density) { w.toDp() })
                    .height(height)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .onGloballyPositioned { c ->
                            centers[i] = c.boundsInRoot().center
                            slots[i] = c.positionInParent().x.roundToInt() to c.size.width
                        }
                        .pressable { onSelect(i, centers[i] ?: Offset.Zero) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Waagerecht scrollbare Auswahl-Chips (für viele Optionen, z. B. Elemente oder Animationen). */
@Composable
internal fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), label = "chip")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "chipText")
            Box(
                Modifier
                    .height(40.dp)
                    .pressable { onSelect(i) }
                    .background(bg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}
