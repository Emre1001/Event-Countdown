package app.countdown.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import app.countdown.MainActivity
import app.countdown.R
import app.countdown.data.EventRepository
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import kotlin.math.roundToInt

/**
 * Baut die RemoteViews. Widgets zeigen Tage, Stunden und Minuten (Android-Widgets können
 * nicht im Sekundentakt aktualisieren); die Zahlen wechseln per ViewFlipper mit
 * Slide-/Fade-Animation, sobald sich ein Wert ändert.
 */
object WidgetRenderer {

    /** Einheiten, die im Widget darstellbar sind. */
    fun widgetUnits(event: CountdownEvent): List<CountdownUnit> =
        event.orderedUnits.filter { it != CountdownUnit.SECONDS }.ifEmpty { listOf(CountdownUnit.MINUTES) }

    private class Slots(
        val unit: CountdownUnit,
        val container: Int, val flipper: Int, val a: Int, val b: Int, val label: Int, val labelRes: Int,
    )

    private val slots = listOf(
        Slots(CountdownUnit.DAYS, R.id.unit_days, R.id.flip_days, R.id.num_days_a, R.id.num_days_b, R.id.lbl_days, R.string.short_days),
        Slots(CountdownUnit.HOURS, R.id.unit_hours, R.id.flip_hours, R.id.num_hours_a, R.id.num_hours_b, R.id.lbl_hours, R.string.short_hours),
        Slots(CountdownUnit.MINUTES, R.id.unit_minutes, R.id.flip_minutes, R.id.num_minutes_a, R.id.num_minutes_b, R.id.lbl_minutes, R.string.short_minutes),
    )

    fun render(context: Context, widgetId: Int, layoutRes: Int): RemoteViews {
        EventRepository.init(context)
        val rv = RemoteViews(context.packageName, layoutRes)
        val event = EventRepository.widgetEvent(widgetId)

        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("countdown://widget/$widgetId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            event?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it.id) }
        }
        rv.setOnClickPendingIntent(
            R.id.root,
            PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )

        if (event == null) {
            rv.setInt(R.id.bg, "setColorFilter", 0xFF1C1C1E.toInt())
            rv.setInt(R.id.bg, "setImageAlpha", 255)
            rv.setTextViewText(R.id.tv_title, context.getString(R.string.widget_placeholder))
            rv.setTextColor(R.id.tv_title, Color.WHITE)
            rv.setTextViewText(R.id.tv_sub, "")
            slots.forEach { rv.setViewVisibility(it.container, View.GONE) }
            return rv
        }

        // Hintergrund: Farbe per Filter (deckend), Transparenz separat per Bild-Alpha.
        rv.setInt(R.id.bg, "setColorFilter", event.background or 0xFF000000.toInt())
        rv.setInt(R.id.bg, "setImageAlpha", (event.backgroundAlpha.coerceIn(0f, 1f) * 255).roundToInt())

        val remaining = Countdown.remaining(event.targetMillis, System.currentTimeMillis(), widgetUnits(event))
        val date = Countdown.formatDate(event.targetMillis)

        rv.setTextViewText(R.id.tv_title, event.title.ifBlank { context.getString(R.string.untitled) })
        rv.setTextColor(R.id.tv_title, event.accent)
        rv.setTextViewText(
            R.id.tv_sub,
            if (remaining.isPast) context.getString(R.string.elapsed) + " · " + date else date,
        )
        rv.setTextColor(R.id.tv_sub, (event.accent and 0xFFFFFF) or (0xB3 shl 24))

        val byUnit = remaining.values.associateBy { it.unit }
        val speech = StringBuilder(event.title).append(": ")
        slots.forEach { s ->
            val uv = byUnit[s.unit]
            if (uv == null) {
                rv.setViewVisibility(s.container, View.GONE)
                return@forEach
            }
            rv.setViewVisibility(s.container, View.VISIBLE)
            val isFirst = remaining.values.first().unit == s.unit
            val text = if (isFirst) uv.value.toString() else uv.value.toString().padStart(2, '0')
            setFlipped(rv, widgetId, s, text, event.textColor)
            val label = context.getString(s.labelRes)
            rv.setTextViewText(s.label, label)
            rv.setTextColor(s.label, event.accent)
            speech.append(uv.value).append(' ').append(label).append(' ')
        }
        rv.setContentDescription(R.id.root, speech.toString().trim())
        return rv
    }

    /**
     * Schreibt den neuen Wert in den unsichtbaren der beiden Flipper-Slots und blättert dorthin,
     * wenn sich der Wert geändert hat – das löst die In-/Out-Animation aus.
     */
    private fun setFlipped(rv: RemoteViews, widgetId: Int, s: Slots, text: String, color: Int) {
        val (last, parity) = EventRepository.flipState(widgetId, s.unit.name)
        val next = if (last != null && last != text) 1 - parity else parity
        rv.setTextViewText(if (next == 0) s.a else s.b, text)
        rv.setTextColor(s.a, color)
        rv.setTextColor(s.b, color)
        rv.setDisplayedChild(s.flipper, next)
        EventRepository.saveFlipState(widgetId, s.unit.name, text, next)
    }
}
