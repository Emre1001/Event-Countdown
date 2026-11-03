package app.countdown.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.countdown.R
import app.countdown.data.EventShare
import app.countdown.data.Reminders
import app.countdown.model.Align
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.DigitAnimation
import app.countdown.model.Element
import app.countdown.model.ElementType
import app.countdown.model.EventMode
import app.countdown.model.EventStyle
import app.countdown.model.Photo
import app.countdown.model.Repeat
import app.countdown.model.WeightChoice
import app.countdown.model.WidgetFormat
import app.countdown.model.WidgetLayout
import app.countdown.render.Fonts
import app.countdown.render.ImageStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private fun newEvent() = CountdownEvent(
    targetMillis = LocalDate.now().plusDays(7).atTime(9, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
)

private const val PICK_BACKGROUND = -1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    initial: CountdownEvent?,
    onSave: (CountdownEvent) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDuplicate: ((CountdownEvent) -> Unit)?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalView.current.context
    val scope = rememberCoroutineScope()
    val original = remember(initial?.id) { initial ?: newEvent() }
    var draft by remember(initial?.id) { mutableStateOf(original) }
    val imported = remember { mutableStateListOf<String>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(WidgetFormat.SQUARE) }
    var selected by remember { mutableStateOf<ElementType?>(ElementType.COUNTDOWN) }
    var dateFor by remember { mutableIntStateOf(-1) } // 0 = Termin, 1 = Fortschritt ab
    var showTime by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val dirty = draft != original

    fun style(block: (EventStyle) -> EventStyle) { draft = draft.copy(style = block(draft.style)) }

    /** Nicht mehr benutzte Bilddateien löschen (neu importierte beim Verwerfen, ersetzte beim Speichern). */
    fun cleanup(saved: CountdownEvent?) {
        val keep = (saved ?: original).style.images.toSet()
        val candidates = imported.toList() + if (saved != null) original.style.images else emptyList()
        ImageStore.delete(context, candidates.filter { it !in keep })
    }

    val close = { cleanup(null); onClose() }
    val requestClose = { if (dirty) showDiscard = true else close() }
    fun finished() = draft.copy(title = draft.title.trim(), note = draft.note.trim(), targetMillis = CountdownEvent.normalize(draft.targetMillis))
    val save = {
        val e = finished()
        cleanup(e)
        onSave(e)
    }
    BackHandler(onBack = requestClose)

    var pickTarget by remember { mutableIntStateOf(PICK_BACKGROUND) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = ImageStore.import(context, uri) ?: return@launch
            imported += name
            val target = pickTarget
            if (target == PICK_BACKGROUND) {
                style { it.copy(image = name) }
            } else {
                val type = ElementType.photos[target]
                style { s ->
                    var st = s.withPhoto(target, (s.photo(target) ?: Photo(name)).copy(image = name))
                    for (f in WidgetFormat.entries) st = st.withLayout(f, st.layout(f).with(type, st.layout(f)[type].copy(visible = true)))
                    st
                }
                selected = type
            }
        }
    }
    fun pick(target: Int) {
        pickTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) draft = draft.copy(reminder = true)
    }
    val setReminder: (Boolean) -> Unit = { on ->
        if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else draft = draft.copy(reminder = on)
    }

    // Kleine/Querformat-Fenster und offene Tastatur: Vorschau nicht fest anheften, sonst bleibt kaum Platz.
    val config = LocalConfiguration.current
    val imeVisible = WindowInsets.isImeVisible
    val pinPreview = config.screenHeightDp >= 640 && !imeVisible
    val focusManager = LocalFocusManager.current

    val preview: @Composable () -> Unit = {
        if (tab == 0) {
            Box(Modifier.fillMaxWidth().background(WallpaperBrush, RoundedCornerShape(32.dp)).padding(14.dp)) { EventCard(draft) }
        } else {
            WidgetDesigner(
                event = draft,
                format = format,
                interactive = tab == 2,
                selected = selected,
                onSelect = { selected = it },
                onElementChange = { t, e -> style { it.withLayout(format, it.layout(format).with(t, e)) } },
            )
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).readableWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                Text(
                    stringResource(if (initial == null) R.string.add_event else R.string.edit_event),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = {
                    scope.launch { runCatching { activity.startActivity(EventShare.shareIntent(context, finished())) } }
                }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                TextButton(onClick = save) { Text(stringResource(R.string.done), fontWeight = FontWeight.SemiBold) }
            }
            SegmentedControl(
                listOf(stringResource(R.string.tab_event), stringResource(R.string.tab_style), stringResource(R.string.tab_widget)),
                tab,
                Modifier.padding(horizontal = 16.dp),
                height = 40.dp,
            ) { i, _ -> tab = i }

            if (pinPreview) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { preview() }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!pinPreview && !imeVisible) preview()
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = Motion.EaseOut)) { dir * it / 12 }) togetherWith
                            fadeOut(tween(140, easing = Motion.EaseIn))
                    },
                    label = "tab",
                ) { t ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (t) {
                            0 -> EventTab(
                                draft,
                                onChange = { draft = it },
                                onDate = { dateFor = 0 },
                                onStart = { dateFor = 1 },
                                onTime = { showTime = true },
                                onReminder = setReminder,
                                onDone = { focusManager.clearFocus() },
                            )
                            1 -> StyleTab(draft.style, ::style, onPickBackground = { pick(PICK_BACKGROUND) })
                            else -> WidgetTab(
                                draft.style, format, selected,
                                onFormat = { format = it },
                                onSelect = { selected = it },
                                onStyle = ::style,
                                onPickPhoto = { pick(it) },
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (onDuplicate != null && initial != null) {
                        TextButton(onClick = { onDuplicate(finished()) }) { Text(stringResource(R.string.duplicate)) }
                    }
                    if (onDelete != null && initial != null) {
                        TextButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Text("  " + stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val zone = ZoneId.systemDefault()
    val ldt = Instant.ofEpochMilli(draft.targetMillis).atZone(zone).toLocalDateTime()
    fun setDateTime(l: LocalDateTime) {
        draft = draft.copy(targetMillis = CountdownEvent.normalize(l.atZone(zone).toInstant().toEpochMilli()))
    }

    if (dateFor >= 0) {
        val base = if (dateFor == 0) ldt else Instant.ofEpochMilli(draft.startMillis).atZone(zone).toLocalDateTime()
        val state = rememberDatePickerState(initialSelectedDateMillis = base.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateFor = -1 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (dateFor == 0) setDateTime(LocalDateTime.of(d, ldt.toLocalTime()))
                        else draft = draft.copy(startMillis = d.atStartOfDay(zone).toInstant().toEpochMilli())
                    }
                    dateFor = -1
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { dateFor = -1 }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(ldt.hour, ldt.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (config.screenHeightDp < 520) TimeInput(state) else TimePicker(state)
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

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; close() }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }

    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    ImageStore.delete(context, (imported + original.style.images).distinct())
                    onDelete?.invoke(initial.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun plainField(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
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
}

// --- Tab "Event" -------------------------------------------------------------------------------

@Composable
private fun EventTab(
    draft: CountdownEvent,
    onChange: (CountdownEvent) -> Unit,
    onDate: () -> Unit,
    onStart: () -> Unit,
    onTime: () -> Unit,
    onReminder: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    val zone = ZoneId.systemDefault()
    val ldt = Instant.ofEpochMilli(draft.targetMillis).atZone(zone).toLocalDateTime()
    val since = draft.mode == EventMode.SINCE

    SegmentedControl(
        listOf(stringResource(R.string.mode_countdown), stringResource(R.string.mode_since)),
        if (since) 1 else 0,
        height = 40.dp,
    ) { i, _ ->
        val mode = if (i == 1) EventMode.SINCE else EventMode.COUNTDOWN
        if (mode != draft.mode) {
            // Beim Wechsel sinnvolle Einheiten vorschlagen: "seit" eher in Jahren, Monaten, Tagen.
            val units = if (mode == EventMode.SINCE) setOf(CountdownUnit.YEARS, CountdownUnit.MONTHS, CountdownUnit.DAYS) else draft.units
            onChange(draft.copy(mode = mode, units = units, repeat = if (mode == EventMode.SINCE) Repeat.NONE else draft.repeat))
        }
    }
    Text(
        stringResource(if (since) R.string.mode_since_hint else R.string.mode_countdown_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    plainField(draft.title, stringResource(if (since) R.string.field_title_hint_since else R.string.field_title_hint), { onChange(draft.copy(title = it.take(40))) }, onDone)
    plainField(draft.note, stringResource(R.string.field_note_hint), { onChange(draft.copy(note = it.take(60))) }, onDone)

    Group {
        ValueRow(
            stringResource(if (since) R.string.field_since else R.string.field_date),
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(ldt), onDate,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ValueRow(stringResource(R.string.field_time), DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(ldt), onTime)
        if (!since) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(
                stringResource(R.string.field_progress_start),
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochMilli(draft.startMillis).atZone(zone)),
                onStart,
            )
        }
    }

    if (!since) {
        SectionTitle(stringResource(R.string.section_repeat), Modifier.padding(top = 6.dp))
        SegmentedControl(
            listOf(stringResource(R.string.repeat_none), stringResource(R.string.repeat_yearly), stringResource(R.string.repeat_monthly), stringResource(R.string.repeat_weekly)),
            Repeat.entries.indexOf(draft.repeat),
            height = 40.dp,
        ) { i, _ -> onChange(draft.copy(repeat = Repeat.entries[i])) }
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.section_display), Modifier.weight(1f))
        TextButton(onClick = { onChange(draft.copy(units = CountdownUnit.entries.toSet())) }) { Text(stringResource(R.string.select_all)) }
    }
    val unitLabels = listOf(
        CountdownUnit.YEARS to R.plurals.unit_years,
        CountdownUnit.MONTHS to R.plurals.unit_months,
        CountdownUnit.WEEKS to R.plurals.unit_weeks,
        CountdownUnit.DAYS to R.plurals.unit_days,
        CountdownUnit.HOURS to R.plurals.unit_hours,
        CountdownUnit.MINUTES to R.plurals.unit_minutes,
        CountdownUnit.SECONDS to R.plurals.unit_seconds,
    )
    val res = LocalContext.current.resources
    val perRow = if (config.fontScale > 1.25f || config.screenWidthDp < 340) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        unitLabels.chunked(perRow).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (unit, plural) ->
                    UnitToggle(res.getQuantityString(plural, 2), unit in draft.units, Modifier.weight(1f)) {
                        val next = if (unit in draft.units) draft.units - unit else draft.units + unit
                        if (next.isNotEmpty()) onChange(draft.copy(units = next))
                    }
                }
                repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (CountdownUnit.SECONDS in draft.units) {
        Text(
            stringResource(R.string.seconds_widget_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    if (!since) {
        Group(padding = 12.dp) {
            SwitchRow(stringResource(R.string.reminder_switch), draft.reminder, onReminder)
        }
    }
}

// --- Tab "Stil" --------------------------------------------------------------------------------

@Composable
private fun StyleTab(style: EventStyle, update: ((EventStyle) -> EventStyle) -> Unit, onPickBackground: () -> Unit) {
    SectionTitle(stringResource(R.string.section_styles))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StylePresets.forEach { p ->
            StylePresetTile(p, style.font, selected = style.matches(p)) { update { it.apply(p) } }
        }
    }

    Group(padding = 16.dp) {
        FillEditor(stringResource(R.string.color_background), style.background) { f -> update { it.copy(background = f) } }
        Spacer(Modifier.height(12.dp))
        ImageRow(stringResource(R.string.background_image), style.image, onPickBackground) { update { it.copy(image = null) } }
        if (style.image != null) {
            SliderRow(stringResource(R.string.image_blur), style.imageBlur, 0f..40f, "${style.imageBlur.roundToInt()}") { v -> update { it.copy(imageBlur = v) } }
            SliderRow(stringResource(R.string.image_dim), style.imageDim, 0f..0.8f, "${(style.imageDim * 100).roundToInt()} %") { v -> update { it.copy(imageDim = v) } }
        }
        SliderRow(stringResource(R.string.opacity), style.backgroundAlpha, 0f..1f, "${(style.backgroundAlpha * 100).roundToInt()} %") { v -> update { it.copy(backgroundAlpha = v) } }
        SliderRow(stringResource(R.string.card_inset), style.cardInset, 0f..0.2f, "${(style.cardInset * 100).roundToInt()} %") { v -> update { it.copy(cardInset = v) } }
        Text(
            stringResource(R.string.card_inset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    Group(padding = 16.dp) {
        SliderRow(stringResource(R.string.border_width), style.borderWidth, 0f..8f, "${"%.1f".format(style.borderWidth)} dp") { v -> update { it.copy(borderWidth = (v * 2).roundToInt() / 2f) } }
        AnimatedVisibility(style.borderWidth > 0f) {
            FillEditor(stringResource(R.string.border_color), style.border) { f -> update { it.copy(border = f) } }
        }
    }

    Group(padding = 16.dp) {
        FillEditor(stringResource(R.string.color_numbers), style.numbers) { f -> update { it.copy(numbers = f) } }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.number_font), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(6.dp))
        FontPicker(style.numberFont, allowSame = true) { f -> update { it.copy(numberFont = f) } }
        Spacer(Modifier.height(10.dp))
        WeightControl(stringResource(R.string.weight_numbers), style.numberWeight) { w -> update { it.copy(numberWeight = w) } }
    }

    Group(padding = 16.dp) {
        FillEditor(stringResource(R.string.color_accent), style.accent) { f -> update { it.copy(accent = f) } }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.section_font), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(6.dp))
        FontPicker(style.font) { f -> if (f != null) update { it.copy(font = f) } }
        Spacer(Modifier.height(10.dp))
        WeightControl(stringResource(R.string.weight_text), style.textWeight) { w -> update { it.copy(textWeight = w) } }
    }

    Group(padding = 16.dp) {
        Text(stringResource(R.string.section_effects), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        val fx = style.effects
        SliderRow(stringResource(R.string.outline), fx.outlineWidth, 0f..4f, "${"%.1f".format(fx.outlineWidth)}") { v ->
            update { it.copy(effects = it.effects.copy(outlineWidth = (v * 4).roundToInt() / 4f)) }
        }
        AnimatedVisibility(fx.outlineWidth > 0f) {
            ColorRow(stringResource(R.string.outline_color), fx.outlineColor) { c -> update { it.copy(effects = it.effects.copy(outlineColor = c)) } }
        }
        SwitchRow(stringResource(R.string.shadow), fx.shadow) { on -> update { it.copy(effects = it.effects.copy(shadow = on)) } }
        AnimatedVisibility(fx.shadow) {
            Column {
                SliderRow(stringResource(R.string.shadow_blur), fx.shadowBlur, 1f..20f, "${fx.shadowBlur.roundToInt()}") { v -> update { it.copy(effects = it.effects.copy(shadowBlur = v)) } }
                ColorRow(stringResource(R.string.shadow_color), fx.shadowColor or 0xFF000000.toInt()) { c ->
                    update { it.copy(effects = it.effects.copy(shadowColor = (c and 0x00FFFFFF) or (0x99 shl 24))) }
                }
            }
        }
    }

    Group(padding = 16.dp) {
        Text(stringResource(R.string.section_animation), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
        val anims = DigitAnimation.entries
        ChipRow(anims.map { stringResource(animationName(it)) }, anims.indexOf(style.animation)) { i -> update { it.copy(animation = anims[i]) } }
        Spacer(Modifier.height(10.dp))
        AnimationDemo(style)
    }

    Group(padding = 16.dp) {
        SliderRow(stringResource(R.string.corners), style.cornerRadius, 0f..44f, "${style.cornerRadius.roundToInt()} dp") { v ->
            update { it.copy(cornerRadius = v.roundToInt().toFloat()) }
        }
        SwitchRow(stringResource(R.string.show_labels), style.showLabels) { on -> update { it.copy(showLabels = on) } }
        SwitchRow(stringResource(R.string.separators), style.separators) { on -> update { it.copy(separators = on) } }
    }
}

private fun animationName(a: DigitAnimation) = when (a) {
    DigitAnimation.ROLL_UP -> R.string.anim_roll_up
    DigitAnimation.ROLL_DOWN -> R.string.anim_roll_down
    DigitAnimation.FADE -> R.string.anim_fade
    DigitAnimation.ZOOM -> R.string.anim_zoom
    DigitAnimation.FLIP -> R.string.anim_flip
    DigitAnimation.MELT -> R.string.anim_melt
    DigitAnimation.NONE -> R.string.anim_none
}

/** Zählt einmal pro Sekunde, damit man die gewählte Animation sofort sieht. */
@Composable
private fun AnimationDemo(style: EventStyle) {
    var n by remember { mutableLongStateOf(59L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            n = if (n <= 0L) 59L else n - 1
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WallpaperBrush).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        RollingNumber(
            n, 2,
            TextStyle(fontSize = 44.sp, fontFamily = Fonts.family(style.digitFont), fontWeight = Fonts.weight(style.numberWeight)),
            Color.White, style.animation,
        )
    }
}

@Composable
private fun WeightControl(label: String, value: WeightChoice, onChange: (WeightChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        SegmentedControl(
            listOf(stringResource(R.string.weight_light), stringResource(R.string.weight_regular), stringResource(R.string.weight_semibold), stringResource(R.string.weight_bold)),
            WeightChoice.entries.indexOf(value),
            height = 40.dp,
        ) { i, _ -> onChange(WeightChoice.entries[i]) }
    }
}

@Composable
private fun ImageRow(label: String, name: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val img by rememberStoredImage(name)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            img?.let { Image(it, null, Modifier.size(48.dp), contentScale = ContentScale.Crop) }
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
        if (name != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_image)) }
        TextButton(onClick = onPick) { Text(stringResource(R.string.choose_image)) }
    }
}

// --- Tab "Widget" ------------------------------------------------------------------------------

private val designerElements = listOf(
    ElementType.TITLE, ElementType.DATE, ElementType.NOTE, ElementType.COUNTDOWN, ElementType.PROGRESS,
    ElementType.PHOTO1, ElementType.PHOTO2, ElementType.PHOTO3,
)

@Composable
private fun elementName(t: ElementType): String = when (t) {
    ElementType.TITLE -> stringResource(R.string.element_title)
    ElementType.DATE -> stringResource(R.string.field_date)
    ElementType.NOTE -> stringResource(R.string.element_note)
    ElementType.COUNTDOWN -> stringResource(R.string.element_countdown)
    ElementType.PROGRESS -> stringResource(R.string.element_progress)
    else -> stringResource(R.string.element_photo_n, t.photoIndex + 1)
}

@Composable
private fun WidgetTab(
    style: EventStyle,
    format: WidgetFormat,
    selected: ElementType?,
    onFormat: (WidgetFormat) -> Unit,
    onSelect: (ElementType?) -> Unit,
    onStyle: ((EventStyle) -> EventStyle) -> Unit,
    onPickPhoto: (Int) -> Unit,
) {
    SegmentedControl(
        listOf(stringResource(R.string.format_square), stringResource(R.string.format_wide), stringResource(R.string.format_bar)),
        WidgetFormat.entries.indexOf(format),
        height = 40.dp,
    ) { i, _ -> onFormat(WidgetFormat.entries[i]) }

    Text(
        stringResource(R.string.designer_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    ChipRow(designerElements.map { elementName(it) }, designerElements.indexOf(selected)) { i -> onSelect(designerElements[i]) }

    val layout = style.layout(format)
    fun set(t: ElementType, block: (Element) -> Element) {
        onStyle { it.withLayout(format, it.layout(format).with(t, block(it.layout(format)[t]))) }
    }

    if (selected != null) {
        val e = layout[selected]
        Group(padding = 16.dp) {
            Column(Modifier.heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow(stringResource(R.string.element_visible), e.visible) { on -> set(selected) { it.copy(visible = on) } }
                SliderRow(stringResource(R.string.element_size), e.scale, 0.4f..2.5f, "${(e.scale * 100).roundToInt()} %") { v -> set(selected) { it.copy(scale = v) } }
                if (selected.rotatable) {
                    SliderRow(stringResource(R.string.element_rotation), e.rotation, -45f..45f, "${e.rotation.roundToInt()}°") { v ->
                        val snapped = if (kotlin.math.abs(v) < 2f) 0f else v
                        set(selected) { it.copy(rotation = snapped) }
                    }
                }
                Text(stringResource(R.string.element_align), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
                SegmentedControl(
                    listOf(stringResource(R.string.align_start), stringResource(R.string.align_center), stringResource(R.string.align_end)),
                    Align.entries.indexOf(e.align),
                    height = 40.dp,
                ) { i, _ ->
                    val a = Align.entries[i]
                    val x = when (a) { Align.START -> 0.08f; Align.CENTER -> 0.5f; Align.END -> 0.92f }
                    set(selected) { it.copy(align = a, x = x) }
                }
                if (selected.isPhoto) {
                    val idx = selected.photoIndex
                    val photo = style.photo(idx)
                    SwitchRow(stringResource(R.string.element_on_top), e.onTop) { on -> set(selected) { it.copy(onTop = on) } }
                    ImageRow(elementName(selected), photo?.image, { onPickPhoto(idx) }) { onStyle { it.withPhoto(idx, null) } }
                    if (photo != null) {
                        val cornerLabel = if (photo.corner >= 0.5f) stringResource(R.string.shape_circle) else "${(photo.corner * 200).roundToInt()} %"
                        SliderRow(stringResource(R.string.photo_corners), photo.corner, 0f..0.5f, cornerLabel) { v ->
                            onStyle { it.withPhoto(idx, photo.copy(corner = v)) }
                        }
                        SliderRow(stringResource(R.string.photo_border), photo.borderWidth, 0f..10f, "${photo.borderWidth.roundToInt()} dp") { v ->
                            onStyle { it.withPhoto(idx, photo.copy(borderWidth = v.roundToInt().toFloat())) }
                        }
                        AnimatedVisibility(photo.borderWidth > 0f) {
                            FillEditor(stringResource(R.string.border_color), photo.border) { f -> onStyle { it.withPhoto(idx, photo.copy(border = f)) } }
                        }
                        SwitchRow(stringResource(R.string.shadow), photo.shadow) { on -> onStyle { it.withPhoto(idx, photo.copy(shadow = on)) } }
                    }
                    Text(
                        stringResource(R.string.photo_overflow_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    TextButton(
        onClick = {
            onStyle { s ->
                var l = WidgetLayout.default(format)
                ElementType.photos.forEach { t -> l = l.with(t, l[t].copy(visible = s.photo(t.photoIndex) != null)) }
                s.withLayout(format, l)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.reset_layout)) }
}

@Composable
private fun UnitToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHigh, label = "toggleBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "toggleFg")
    Box(
        modifier.heightIn(min = 48.dp).pressable(onClick).background(bg, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp)) }
}
