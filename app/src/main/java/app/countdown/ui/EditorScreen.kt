package app.countdown.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.countdown.R
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun newEvent() = CountdownEvent(
    targetMillis = LocalDate.now().plusDays(7).atTime(9, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initial: CountdownEvent?,
    onSave: (CountdownEvent) -> Unit,
    onDelete: ((String) -> Unit)?,
    onClose: () -> Unit,
) {
    var draft by remember(initial?.id) { mutableStateOf(initial ?: newEvent()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    val zone = ZoneId.systemDefault()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val ldt = Instant.ofEpochMilli(draft.targetMillis).atZone(zone).toLocalDateTime()
    fun setDateTime(l: LocalDateTime) {
        draft = draft.copy(targetMillis = l.atZone(zone).toInstant().toEpochMilli())
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Kopfzeile
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                Text(
                    stringResource(if (initial == null) R.string.add_event else R.string.edit_event),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { onSave(draft.copy(title = draft.title.trim())) }) {
                    Text(stringResource(R.string.done), fontWeight = FontWeight.SemiBold)
                }
            }

            // Live-Vorschau auf einem "Hintergrundbild", damit Deckkraft sichtbar wird.
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))),
                        RoundedCornerShape(32.dp),
                    )
                    .padding(14.dp),
            ) { EventCard(draft) }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it.take(40)) },
                    placeholder = { Text(stringResource(R.string.field_title_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )

                Group {
                    ValueRow(
                        stringResource(R.string.field_date),
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(ldt),
                    ) { showDate = true }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow(
                        stringResource(R.string.field_time),
                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(ldt),
                    ) { showTime = true }
                }

                // Anzeige: beliebige Kombination aus Tage / Stunden / Minuten / Sekunden
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.section_display), Modifier.weight(1f))
                    TextButton(onClick = { draft = draft.copy(units = CountdownUnit.entries.toSet()) }) {
                        Text(stringResource(R.string.select_all))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(
                        CountdownUnit.DAYS to R.plurals.unit_days,
                        CountdownUnit.HOURS to R.plurals.unit_hours,
                        CountdownUnit.MINUTES to R.plurals.unit_minutes,
                        CountdownUnit.SECONDS to R.plurals.unit_seconds,
                    )
                    val res = androidx.compose.ui.platform.LocalContext.current.resources
                    labels.forEach { (unit, plural) ->
                        UnitToggle(
                            label = res.getQuantityString(plural, 2),
                            selected = unit in draft.units,
                            modifier = Modifier.weight(1f),
                        ) {
                            val next = if (unit in draft.units) draft.units - unit else draft.units + unit
                            if (next.isNotEmpty()) draft = draft.copy(units = next)
                        }
                    }
                }

                SectionTitle(stringResource(R.string.section_styles), Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StylePresets.forEach { p ->
                        StylePresetTile(
                            p,
                            selected = p.background == draft.background && p.text == draft.textColor && p.accent == draft.accent,
                        ) { draft = draft.copy(background = p.background, textColor = p.text, accent = p.accent) }
                    }
                }

                SectionTitle(stringResource(R.string.section_colors), Modifier.padding(top = 10.dp))
                Group(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorRow(stringResource(R.string.color_background), draft.background) { draft = draft.copy(background = it) }
                        ColorRow(stringResource(R.string.color_numbers), draft.textColor) { draft = draft.copy(textColor = it) }
                        ColorRow(stringResource(R.string.color_accent), draft.accent) { draft = draft.copy(accent = it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.opacity), style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = draft.backgroundAlpha,
                                onValueChange = { draft = draft.copy(backgroundAlpha = it) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.widget_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )

                if (onDelete != null && initial != null) {
                    TextButton(onClick = { showDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text("  " + stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ldt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        setDateTime(LocalDateTime.of(d, ldt.toLocalTime()))
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(ldt.hour, ldt.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            setDateTime(LocalDateTime.of(ldt.toLocalDate(), LocalTime.of(state.hour, state.minute)))
                            showTime = false
                        }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }

    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete?.invoke(initial.id) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun Group(padding: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) { Column(Modifier.padding(padding)) { content() } }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun UnitToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh, label = "toggleBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "toggleFg",
    )
    Box(
        modifier
            .height(46.dp)
            .pressable(onClick)
            .background(bg, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}
