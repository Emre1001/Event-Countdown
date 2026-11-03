package app.countdown.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.countdown.R
import app.countdown.model.Align
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.ElementType
import app.countdown.model.EventStyle
import app.countdown.model.StickerShape
import app.countdown.model.WeightChoice
import app.countdown.model.WidgetFormat
import app.countdown.model.WidgetLayout
import app.countdown.render.ImageStore
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

private const val PICK_BACKGROUND = 0
private const val PICK_STICKER = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    initial: CountdownEvent?,
    onSave: (CountdownEvent) -> Unit,
    onDelete: ((String) -> Unit)?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember(initial?.id) { initial ?: newEvent() }
    var draft by remember(initial?.id) { mutableStateOf(original) }
    val imported = remember { mutableStateListOf<String>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(WidgetFormat.SQUARE) }
    var selected by remember { mutableStateOf<ElementType?>(ElementType.COUNTDOWN) }
    var showDate by remember { mutableStateOf(false) }
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
    val save = {
        val e = draft.copy(title = draft.title.trim(), targetMillis = CountdownEvent.normalize(draft.targetMillis))
        cleanup(e)
        onSave(e)
    }
    BackHandler(onBack = requestClose)

    var pickTarget by remember { mutableIntStateOf(PICK_BACKGROUND) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = ImageStore.import(context, uri) ?: return@launch
            imported += name
            if (pickTarget == PICK_BACKGROUND) {
                style { it.copy(image = name) }
            } else {
                style { s ->
                    s.copy(
                        sticker = name,
                        square = s.square.with(ElementType.STICKER, s.square.sticker.copy(visible = true)),
                        wide = s.wide.with(ElementType.STICKER, s.wide.sticker.copy(visible = true)),
                    )
                }
                selected = ElementType.STICKER
            }
        }
    }
    fun pick(target: Int) {
        pickTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Kleine/Querformat-Fenster und offene Tastatur: Vorschau nicht fest anheften, sonst bleibt kaum Platz.
    val config = LocalConfiguration.current
    val imeVisible = WindowInsets.isImeVisible
    val pinPreview = config.screenHeightDp >= 640 && !imeVisible
    val focusManager = LocalFocusManager.current

    val preview: @Composable () -> Unit = {
        if (tab == 0) {
            Box(
                Modifier.fillMaxWidth().background(WallpaperBrush, RoundedCornerShape(32.dp)).padding(14.dp),
            ) { EventCard(draft) }
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
                                onTitle = { draft = draft.copy(title = it.take(40)) },
                                onUnits = { draft = draft.copy(units = it) },
                                onDate = { showDate = true },
                                onTime = { showTime = true },
                                onDone = { focusManager.clearFocus() },
                            )
                            1 -> StyleTab(draft.style, ::style, onPickBackground = { pick(PICK_BACKGROUND) })
                            else -> WidgetTab(
                                draft.style,
                                format,
                                selected,
                                onFormat = { format = it },
                                onSelect = { selected = it },
                                onStyle = ::style,
                                onPickSticker = { pick(PICK_STICKER) },
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

    val zone = ZoneId.systemDefault()
    val ldt = Instant.ofEpochMilli(draft.targetMillis).atZone(zone).toLocalDateTime()
    fun setDateTime(l: LocalDateTime) {
        draft = draft.copy(targetMillis = CountdownEvent.normalize(l.atZone(zone).toInstant().toEpochMilli()))
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
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                TextButton(onClick = { showDiscard = false; close() }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
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

// --- Tab "Event" -------------------------------------------------------------------------------

@Composable
private fun EventTab(
    draft: CountdownEvent,
    onTitle: (String) -> Unit,
    onUnits: (Set<CountdownUnit>) -> Unit,
    onDate: () -> Unit,
    onTime: () -> Unit,
    onDone: () -> Unit,
) {
    val config = LocalConfiguration.current
    val locale = config.locales[0]
    val ldt = Instant.ofEpochMilli(draft.targetMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    TextField(
        value = draft.title,
        onValueChange = onTitle,
        placeholder = { Text(stringResource(R.string.field_title_hint)) },
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

    Group {
        ValueRow(stringResource(R.string.field_date), DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(ldt), onDate)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ValueRow(stringResource(R.string.field_time), DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(ldt), onTime)
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.section_display), Modifier.weight(1f))
        TextButton(onClick = { onUnits(CountdownUnit.entries.toSet()) }) { Text(stringResource(R.string.select_all)) }
    }
    val unitLabels = listOf(
        CountdownUnit.DAYS to R.plurals.unit_days,
        CountdownUnit.HOURS to R.plurals.unit_hours,
        CountdownUnit.MINUTES to R.plurals.unit_minutes,
        CountdownUnit.SECONDS to R.plurals.unit_seconds,
    )
    val res = LocalContext.current.resources
    // Bei großer Schrift 2 × 2 statt 4 in einer Reihe, damit nichts abgeschnitten wird.
    val perRow = if (config.fontScale > 1.25f || config.screenWidthDp < 340) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        unitLabels.chunked(perRow).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (unit, plural) ->
                    UnitToggle(res.getQuantityString(plural, 2), unit in draft.units, Modifier.weight(1f)) {
                        val next = if (unit in draft.units) draft.units - unit else draft.units + unit
                        if (next.isNotEmpty()) onUnits(next)
                    }
                }
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
            SliderRow(stringResource(R.string.image_dim), style.imageDim, 0f..0.8f, "${(style.imageDim * 100).roundToInt()} %") { v ->
                update { it.copy(imageDim = v) }
            }
        }
        SliderRow(stringResource(R.string.opacity), style.backgroundAlpha, 0f..1f, "${(style.backgroundAlpha * 100).roundToInt()} %") { v ->
            update { it.copy(backgroundAlpha = v) }
        }
    }

    Group(padding = 16.dp) {
        FillEditor(stringResource(R.string.color_numbers), style.numbers) { f -> update { it.copy(numbers = f) } }
    }
    Group(padding = 16.dp) {
        FillEditor(stringResource(R.string.color_accent), style.accent) { f -> update { it.copy(accent = f) } }
    }

    Group(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.section_font), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
            FontPicker(style.font) { f -> update { it.copy(font = f) } }
            WeightControl(stringResource(R.string.weight_numbers), style.numberWeight) { w -> update { it.copy(numberWeight = w) } }
            WeightControl(stringResource(R.string.weight_text), style.textWeight) { w -> update { it.copy(textWeight = w) } }
        }
    }

    Group(padding = 16.dp) {
        SliderRow(stringResource(R.string.corners), style.cornerRadius, 0f..44f, "${style.cornerRadius.roundToInt()} dp") { v ->
            update { it.copy(cornerRadius = v.roundToInt().toFloat()) }
        }
        SwitchRow(stringResource(R.string.show_labels), style.showLabels) { on -> update { it.copy(showLabels = on) } }
    }
}

@Composable
private fun WeightControl(label: String, value: WeightChoice, onChange: (WeightChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
        SegmentedControl(
            listOf(
                stringResource(R.string.weight_light),
                stringResource(R.string.weight_regular),
                stringResource(R.string.weight_semibold),
                stringResource(R.string.weight_bold),
            ),
            WeightChoice.entries.indexOf(value),
            height = 40.dp,
        ) { i, _ -> onChange(WeightChoice.entries[i]) }
    }
}

@Composable
private fun ImageRow(label: String, name: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val img by rememberStoredImage(name)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            img?.let { Image(it, null, Modifier.size(48.dp), contentScale = ContentScale.Crop) }
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
        if (name != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_image)) }
        TextButton(onClick = onPick) { Text(stringResource(R.string.choose_image)) }
    }
}

// --- Tab "Widget" ------------------------------------------------------------------------------

@Composable
private fun WidgetTab(
    style: EventStyle,
    format: WidgetFormat,
    selected: ElementType?,
    onFormat: (WidgetFormat) -> Unit,
    onSelect: (ElementType?) -> Unit,
    onStyle: ((EventStyle) -> EventStyle) -> Unit,
    onPickSticker: () -> Unit,
) {
    SegmentedControl(
        listOf(stringResource(R.string.format_square), stringResource(R.string.format_wide)),
        if (format == WidgetFormat.WIDE) 1 else 0,
        height = 40.dp,
    ) { i, _ -> onFormat(if (i == 1) WidgetFormat.WIDE else WidgetFormat.SQUARE) }

    Text(
        stringResource(R.string.designer_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    val elements = listOf(ElementType.TITLE, ElementType.DATE, ElementType.COUNTDOWN, ElementType.STICKER)
    SegmentedControl(
        listOf(
            stringResource(R.string.element_title),
            stringResource(R.string.field_date),
            stringResource(R.string.element_countdown),
            stringResource(R.string.element_sticker),
        ),
        elements.indexOf(selected),
        height = 40.dp,
    ) { i, _ -> onSelect(elements[i]) }

    val layout = style.layout(format)
    fun set(t: ElementType, block: (app.countdown.model.Element) -> app.countdown.model.Element) {
        onStyle { it.withLayout(format, it.layout(format).with(t, block(it.layout(format)[t]))) }
    }

    if (selected != null) {
        val e = layout[selected]
        Group(padding = 16.dp) {
            Column(Modifier.heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow(stringResource(R.string.element_visible), e.visible) { on -> set(selected) { it.copy(visible = on) } }
                SliderRow(stringResource(R.string.element_size), e.scale, 0.4f..2.5f, "${(e.scale * 100).roundToInt()} %") { v ->
                    set(selected) { it.copy(scale = v) }
                }
                Text(stringResource(R.string.element_align), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
                SegmentedControl(
                    listOf(stringResource(R.string.align_start), stringResource(R.string.align_center), stringResource(R.string.align_end)),
                    Align.entries.indexOf(e.align),
                    height = 40.dp,
                ) { i, _ ->
                    val a = Align.entries[i]
                    // Anker passend setzen: links/rechts an den Rand, Mitte genau mittig.
                    val x = when (a) { Align.START -> 0.08f; Align.CENTER -> 0.5f; Align.END -> 0.92f }
                    set(selected) { it.copy(align = a, x = x) }
                }
                if (selected == ElementType.STICKER) {
                    ImageRow(stringResource(R.string.element_sticker), style.sticker, onPickSticker) { onStyle { it.copy(sticker = null) } }
                    SegmentedControl(
                        listOf(stringResource(R.string.shape_circle), stringResource(R.string.shape_rounded)),
                        if (style.stickerShape == StickerShape.CIRCLE) 0 else 1,
                        height = 40.dp,
                    ) { i, _ -> onStyle { it.copy(stickerShape = if (i == 0) StickerShape.CIRCLE else StickerShape.ROUNDED) } }
                }
            }
        }
    }

    TextButton(
        onClick = { onStyle { it.withLayout(format, WidgetLayout.default(format).with(ElementType.STICKER, WidgetLayout.default(format).sticker.copy(visible = it.sticker != null))) } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.reset_layout)) }
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
            .heightIn(min = 48.dp)
            .pressable(onClick)
            .background(bg, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp)) }
}
