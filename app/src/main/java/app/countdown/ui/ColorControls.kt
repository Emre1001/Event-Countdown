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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.countdown.R
import app.countdown.model.EventStyle
import app.countdown.model.Fill
import app.countdown.model.FontChoice
import app.countdown.model.Material
import app.countdown.render.Fonts
import kotlin.math.roundToInt

val Swatches = listOf(
    0xFF000000, 0xFF1C1C1E, 0xFF3A3A3C, 0xFFFFFFFF, 0xFFF2F2F7,
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF6B6B,
    0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFF7D9CC4,
).map { it.toInt() }

/** Komplette Farbschemata – dezent, auch mit Verläufen. */
data class StylePreset(val background: Fill, val numbers: Fill, val accent: Fill)

private fun c(v: Long) = v.toInt()

val StylePresets = listOf(
    StylePreset(Fill.solid(c(0xFF0B0B0F)), Fill.solid(c(0xFFFFFFFF)), Fill.solid(c(0xFF7D9CC4))),
    StylePreset(Fill.solid(c(0xFFF5F5F2)), Fill.solid(c(0xFF141416)), Fill.solid(c(0xFF6E6E73))),
    StylePreset(Fill.gradient(c(0xFF1A1033), c(0xFF3B1F5C), angle = 135f), Fill.gradient(c(0xFFFFFFFF), c(0xFFD8C8FF), angle = 90f), Fill.solid(c(0xFFC4B5FD))),
    StylePreset(Fill.gradient(c(0xFF2A0F1A), c(0xFF5B1E2D), angle = 135f), Fill.gradient(c(0xFFFFE9E3), c(0xFFFFB4A2), angle = 90f), Fill.solid(c(0xFFFF8A80))),
    StylePreset(Fill.gradient(c(0xFF061A2B), c(0xFF0E3A5A), angle = 135f), Fill.gradient(c(0xFFE6F3FF), c(0xFF9AD4FF), angle = 90f), Fill.solid(c(0xFF64D2FF))),
    StylePreset(Fill.gradient(c(0xFF0D1F17), c(0xFF1C3D2C), angle = 135f), Fill.solid(c(0xFFEAF7EF)), Fill.solid(c(0xFF6EE7A0))),
    StylePreset(Fill.gradient(c(0xFFF3E9DC), c(0xFFE6D2BC), angle = 135f), Fill.solid(c(0xFF3B2F2F)), Fill.solid(c(0xFFA47148))),
    StylePreset(Fill.solid(c(0xFF000000)), Fill.gradient(c(0xFFFFD60A), c(0xFFFF9F0A), angle = 90f), Fill.solid(c(0xFF8E8E93))),
    // Glas: Milchglas (frosty) und Flüssigglas (liquid)
    StylePreset(Fill.glass(c(0xFF1C1C22), Material.FROST), Fill.solid(c(0xFFFFFFFF)), Fill.solid(c(0xFFE6E6EA))),
    StylePreset(Fill.glass(c(0xFF9AD4FF), Material.LIQUID), Fill.glass(c(0xFF9AD4FF), Material.LIQUID), Fill.solid(c(0xFFFFFFFF))),
    StylePreset(Fill.glass(c(0xFFFFB4A2), Material.FROST), Fill.solid(c(0xFF2A0F1A)), Fill.solid(c(0xFF5B1E2D))),
    StylePreset(Fill.glass(c(0xFFFFFFFF), Material.LIQUID), Fill.solid(c(0xFFFFFFFF)), Fill.solid(c(0xFFFFFFFF))),
)

fun EventStyle.matches(p: StylePreset) = background == p.background && numbers == p.numbers && accent == p.accent

fun EventStyle.apply(p: StylePreset) = copy(background = p.background, numbers = p.numbers, accent = p.accent)

@Composable
fun StylePresetTile(preset: StylePreset, font: FontChoice, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .size(width = 84.dp, height = 64.dp)
            .pressable(onClick)
            .clip(shape)
            .background(WallpaperBrush)
            .background(preset.background.brush(), alpha = if (preset.background.isGlass) 0.55f else 1f)
            .border(
                if (selected) 2.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("12", style = TextStyle(brush = preset.numbers.brush(), fontSize = 22.sp, fontFamily = Fonts.family(font), fontWeight = Fonts.weight(app.countdown.model.WeightChoice.LIGHT)))
        Text(stringResource(R.string.short_days), style = TextStyle(brush = preset.accent.brush(), fontSize = 10.sp, fontFamily = Fonts.family(font)))
    }
}

/** Farbe, Verlauf (2–3 Farben, Winkel), Milchglas oder Flüssigglas – für alles mit Farbe. */
@Composable
fun FillEditor(label: String, fill: Fill, onChange: (Fill) -> Unit) {
    var stop by remember { mutableIntStateOf(0) }
    val sel = stop.coerceIn(0, fill.colors.lastIndex)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        val kind = when {
            fill.material == Material.FROST -> 2
            fill.material == Material.LIQUID -> 3
            fill.isGradient -> 1
            else -> 0
        }
        SegmentedControl(
            listOf(
                stringResource(R.string.fill_solid),
                stringResource(R.string.fill_gradient),
                stringResource(R.string.fill_frost),
                stringResource(R.string.fill_liquid),
            ),
            kind,
            height = 40.dp,
        ) { i, _ ->
            val base = fill.colors[sel]
            stop = 0
            onChange(
                when (i) {
                    1 -> if (fill.isGradient) fill else Fill(listOf(base, shifted(base)), 135f)
                    2 -> Fill.glass(base, Material.FROST)
                    3 -> Fill.glass(base, Material.LIQUID)
                    else -> Fill.solid(base)
                },
            )
        }
        if (fill.isGlass) {
            Text(
                stringResource(R.string.glass_tint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (fill.isGradient) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                fill.colors.forEachIndexed { i, col ->
                    Box(Modifier.size(48.dp).clickable { stop = i }, contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .border(if (i == sel) 3.dp else 1.dp, if (i == sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                }
                if (fill.colors.size < 3) {
                    Box(Modifier.size(48.dp).clickable { onChange(fill.copy(colors = fill.colors + shifted(fill.colors.last()))); stop = fill.colors.size }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, stringResource(R.string.add_color), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box(Modifier.size(48.dp).clickable { onChange(fill.copy(colors = fill.colors.filterIndexed { i, _ -> i != sel })); stop = 0 }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, stringResource(R.string.remove_color), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(fill.colors.map { Color(it) })),
                )
            }
        }
        ColorRow(null, fill.colors[sel]) { col ->
            onChange(fill.copy(colors = fill.colors.toMutableList().also { it[sel] = col }))
        }
        if (fill.isGradient) {
            SliderRow(stringResource(R.string.gradient_angle), fill.angle, 0f..360f, "${fill.angle.roundToInt()}°") {
                onChange(fill.copy(angle = (it / 15f).roundToInt() * 15f))
            }
        }
    }
}

/** Zweite Verlaufsfarbe: gleicher Farbton, leicht verschoben – wirkt ruhig statt bunt. */
private fun shifted(color: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    hsv[0] = (hsv[0] + 28f) % 360f
    hsv[2] = if (hsv[2] > 0.5f) hsv[2] * 0.82f else (hsv[2] + 0.18f).coerceAtMost(1f)
    if (hsv[1] < 0.08f) hsv[2] = if (hsv[2] > 0.5f) hsv[2] * 0.85f else hsv[2] + 0.12f
    return android.graphics.Color.HSVToColor(hsv)
}

private val fontNames = mapOf(
    FontChoice.SANS to R.string.font_sans,
    FontChoice.ROUNDED to R.string.font_rounded,
    FontChoice.SERIF to R.string.font_serif,
    FontChoice.MONO to R.string.font_mono,
    FontChoice.GEO to R.string.font_geo,
    FontChoice.GROTESK to R.string.font_grotesk,
    FontChoice.DISPLAY to R.string.font_display,
    FontChoice.HAND to R.string.font_hand,
    FontChoice.TECH to R.string.font_tech,
)

/** Schriftwahl als waagerecht scrollbare Kacheln; [allowSame] fügt "wie Text" vorne an (null). */
@Composable
fun FontPicker(selected: FontChoice?, allowSame: Boolean = false, onSelect: (FontChoice?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options: List<FontChoice?> = (if (allowSame) listOf<FontChoice?>(null) else emptyList()) + FontChoice.entries
        options.forEach { f ->
            val on = f == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .size(width = 78.dp, height = 72.dp)
                    .pressable { onSelect(f) }
                    .clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (f == null) {
                    Text("=", fontSize = 24.sp)
                    Text(stringResource(R.string.font_same), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                } else {
                    Text("12", fontFamily = Fonts.family(f), fontSize = 24.sp, fontWeight = Fonts.weight(app.countdown.model.WeightChoice.REGULAR))
                    Text(stringResource(fontNames.getValue(f)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ColorRow(label: String?, color: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (label != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "#%06X".format(color and 0xFFFFFF),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatches.forEach { c ->
                val selected = (c or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())
                Box(Modifier.size(46.dp).clickable { onChange(c) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(32.dp)
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
            Box(Modifier.size(46.dp).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(
                            if (expanded) 3.dp else 1.dp,
                            if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
        AnimatedVisibility(expanded) {
            HsvPicker(color, onChange, Modifier.padding(top = 4.dp, bottom = 6.dp))
        }
    }
}

@Composable
fun HsvPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    fun hsvOf(c: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }.toList()
    var hsv by remember { mutableStateOf(hsvOf(color)) }
    var last by remember { mutableIntStateOf(color) }
    // Von außen geändert (Farbfeld, Stil): übernehmen. Eigene Änderungen behalten den Farbton,
    // auch wenn Sättigung oder Helligkeit kurz auf 0 stehen.
    if (color != last) {
        hsv = hsvOf(color)
        last = color
    }
    fun push(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        hsv = listOf(h, s, v)
        val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        last = c
        onChange(c)
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientSlider(
            hsv[0] / 360f,
            { push(h = it * 360f) },
            Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
        )
        GradientSlider(hsv[1], { push(s = it) }, Brush.horizontalGradient(listOf(Color.White, hueColor)))
        GradientSlider(hsv[2], { push(v = it) }, Brush.horizontalGradient(listOf(Color.Black, hueColor)))
    }
}

@Composable
fun GradientSlider(value: Float, onValueChange: (Float) -> Unit, brush: Brush, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableIntStateOf(1) }
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
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
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
