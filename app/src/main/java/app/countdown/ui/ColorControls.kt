package app.countdown.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val Swatches = listOf(
    0xFF000000, 0xFF1C1C1E, 0xFFFFFFFF, 0xFFF2F2F7,
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F,
    0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF,
).map { it.toInt() }

/** Komplette Farbschemata (Hintergrund, Zahlen, Akzent). */
data class StylePreset(val background: Int, val text: Int, val accent: Int)

val StylePresets = listOf(
    StylePreset(0xFF0B0B0F.toInt(), 0xFFFFFFFF.toInt(), 0xFF0A84FF.toInt()),
    StylePreset(0xFFF5F5F7.toInt(), 0xFF111114.toInt(), 0xFF6E6E73.toInt()),
    StylePreset(0xFF2A0F1A.toInt(), 0xFFFFE9E3.toInt(), 0xFFFF6B7A.toInt()),
    StylePreset(0xFF0E1F17.toInt(), 0xFFEAF7EF.toInt(), 0xFF30D158.toInt()),
    StylePreset(0xFF071A2B.toInt(), 0xFFE6F3FF.toInt(), 0xFF64D2FF.toInt()),
    StylePreset(0xFF150F2E.toInt(), 0xFFF1ECFF.toInt(), 0xFFBF5AF2.toInt()),
)

@Composable
fun StylePresetTile(preset: StylePreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 84.dp, height = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(preset.background))
            .border(
                if (selected) 2.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("12", color = Color(preset.text), fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(androidx.compose.ui.res.stringResource(app.countdown.R.string.short_days), color = Color(preset.accent), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "#%06X".format(color and 0xFFFFFF),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatches.forEach { c ->
                val selected = (c or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())
                Box(Modifier.size(48.dp).clickable { onChange(c) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            Box(Modifier.size(48.dp).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                            ),
                        )
                        .border(
                            if (expanded) 3.dp else 1.dp,
                            if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
        AnimatedVisibility(expanded) {
            HsvPicker(color, onChange, Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
fun HsvPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var hsv by remember {
        mutableStateOf(FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }.toList())
    }
    fun push(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        hsv = listOf(h, s, v)
        onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientSlider(
            hsv[0] / 360f,
            { push(h = it * 360f) },
            Brush.horizontalGradient(
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            ),
        )
        GradientSlider(hsv[1], { push(s = it) }, Brush.horizontalGradient(listOf(Color.White, hueColor)))
        GradientSlider(hsv[2], { push(v = it) }, Brush.horizontalGradient(listOf(Color.Black, hueColor)))
    }
}

@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1) }
    val thumb = 40.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    val onChange by rememberUpdatedState(onValueChange)
    Box(
        modifier
            .fillMaxWidth()
            .height(thumb)
            .systemGestureExclusion()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset((value.coerceIn(0f, 1f) * (widthPx - thumbPx)).roundToInt(), 0) }
                .size(thumb)
                .padding(3.dp)
                .border(3.dp, Color.White, CircleShape),
        )
    }
}
