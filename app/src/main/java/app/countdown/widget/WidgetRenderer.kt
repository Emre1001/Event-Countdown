package app.countdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.countdown.MainActivity
import app.countdown.R
import app.countdown.data.AppSettings
import app.countdown.data.EventRepository
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.DigitAnimation
import app.countdown.model.EventMode
import app.countdown.model.WidgetFormat
import app.countdown.render.FontRes
import app.countdown.render.WidgetPainter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Baut die RemoteViews eines Widgets:
 *  1. Grundbild: Karte, Fotos, Texte, Beschriftungen (frei gestaltet, als Bild)
 *  2. je Einheit eine Zahl als eigene Ebene in einem ViewFlipper – ändert sich der Wert, läuft die
 *     gewählte Animation (von unten, von oben, Überblenden, Zoom, Klappen, Schmelzen)
 *  3. Sekunden als TextClock, der im Launcher selbst tickt
 *  4. Elemente "im Vordergrund" ganz oben.
 */
object WidgetRenderer {

    private val slots = arrayOf(R.id.u0, R.id.u1, R.id.u2, R.id.u3, R.id.u4, R.id.u5)
    private val flips = arrayOf(R.id.u0_flip, R.id.u1_flip, R.id.u2_flip, R.id.u3_flip, R.id.u4_flip, R.id.u5_flip)
    private val imgA = arrayOf(R.id.u0_a, R.id.u1_a, R.id.u2_a, R.id.u3_a, R.id.u4_a, R.id.u5_a)
    private val imgB = arrayOf(R.id.u0_b, R.id.u1_b, R.id.u2_b, R.id.u3_b, R.id.u4_b, R.id.u5_b)

    private fun rootLayout(anim: DigitAnimation) = when (anim) {
        DigitAnimation.ROLL_UP -> R.layout.widget_root_roll_up
        DigitAnimation.ROLL_DOWN -> R.layout.widget_root_roll_down
        DigitAnimation.FADE -> R.layout.widget_root_fade
        DigitAnimation.ZOOM -> R.layout.widget_root_zoom
        DigitAnimation.FLIP -> R.layout.widget_root_flip
        DigitAnimation.MELT -> R.layout.widget_root_melt
        DigitAnimation.NONE -> R.layout.widget_root_none
    }

    fun render(baseContext: Context, widgetId: Int, fallbackFormat: WidgetFormat): RemoteViews {
        EventRepository.init(baseContext)
        AppSettings.init(baseContext)
        val context = AppSettings.wrap(baseContext)
        val event = EventRepository.widgetEvent(widgetId)
        val options = AppWidgetManager.getInstance(baseContext).getAppWidgetOptions(widgetId)
        val sizes = sizesDp(options, fallbackFormat)
        val now = System.currentTimeMillis()

        // Welche Zahlen haben sich geändert? Nur die blättern um (je Einheit eigener Zustand).
        val parity = HashMap<String, Int>()
        if (event != null) {
            val rem = Countdown.remaining(event.effectiveTarget(now), now, event.widgetUnits)
            rem.values.forEachIndexed { i, uv ->
                val text = if (i == 0) uv.value.toString() else uv.value.toString().padStart(2, '0')
                val (last, p) = EventRepository.unitState(widgetId, uv.unit.name)
                val next = if (last != null && last != text) 1 - p else p
                parity[uv.unit.name] = next
                EventRepository.saveUnitState(widgetId, uv.unit.name, text, next)
            }
        }

        val dm = context.resources.displayMetrics
        val layersPerSize = if (event != null && sizes.any { WidgetPainter.hasTopLayer(event, formatOf(it.first, it.second)) }) 2 else 1
        val budgetPx = dm.widthPixels.toDouble() * dm.heightPixels * 1.5 * 0.65 / (sizes.size * layersPerSize)

        val views = sizes.map { (w, h) -> build(context, widgetId, event, w, h, now, parity, budgetPx) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
            RemoteViews(sizes.indices.associate { SizeF(sizes[it].first, sizes[it].second) to views[it] })
        } else {
            views.first()
        }
    }

    fun formatOf(wDp: Float, hDp: Float): WidgetFormat {
        val ratio = wDp / hDp
        return when {
            ratio > 2.8f -> WidgetFormat.BAR
            ratio > 1.45f -> WidgetFormat.WIDE
            else -> WidgetFormat.SQUARE
        }
    }

    /** Hochformat-Größe immer, Querformat zusätzlich (Android 12+), damit alles genau sitzt. */
    private fun sizesDp(options: Bundle, fallback: WidgetFormat): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val s = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            s?.filter { it.width > 0 && it.height > 0 }?.forEach { list += it.width to it.height }
        }
        if (list.isEmpty()) {
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (minW > 0 && maxH > 0) list += minW.toFloat() to maxH.toFloat()
            if (maxW > 0 && minH > 0 && (maxW != minW || minH != maxH)) list += maxW.toFloat() to minH.toFloat()
        }
        if (list.isEmpty()) list += when (fallback) {
            WidgetFormat.WIDE -> 320f to 160f
            WidgetFormat.BAR -> 320f to 80f
            WidgetFormat.SQUARE -> 160f to 160f
        }
        return list.distinct().take(2)
    }

    private fun openIntent(context: Context, widgetId: Int, eventId: String?): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("countdown://widget/$widgetId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            eventId?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it) }
        }
        return PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun canvasBitmap(w: Float, h: Float, budgetPx: Double): Pair<Bitmap, Canvas> {
        val scale = min(1.0, sqrt(budgetPx / (w * h))).toFloat()
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        return bmp to canvas
    }

    private fun build(
        context: Context,
        widgetId: Int,
        event: CountdownEvent?,
        wDp: Float,
        hDp: Float,
        now: Long,
        parity: Map<String, Int>,
        budgetPx: Double,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, rootLayout(event?.style?.animation ?: DigitAnimation.NONE))
        rv.setOnClickPendingIntent(R.id.root, openIntent(context, widgetId, event?.id))
        val density = context.resources.displayMetrics.density
        val w = wDp * density
        val h = hDp * density
        rv.removeAllViews(R.id.sec_slot)
        slots.forEach { rv.setViewVisibility(it, View.GONE) }

        val (bg, canvas) = canvasBitmap(w, h, budgetPx)
        if (event == null) {
            WidgetPainter.paintPlaceholder(context, canvas, w, h, context.getString(R.string.widget_placeholder))
            rv.setImageViewBitmap(R.id.bg, bg)
            rv.setViewVisibility(R.id.top, View.GONE)
            return rv
        }

        val format = formatOf(wDp, hDp)
        val locale = AppSettings.locale()
        val result = WidgetPainter.Result()
        WidgetPainter.paint(context, canvas, w, h, event, format, now, locale, numberLayers = true, result = result, layer = WidgetPainter.Layer.BASE)
        rv.setImageViewBitmap(R.id.bg, bg)

        // Zahlen-Ebenen
        val numbers = result.numbers.toList()
        numbers.take(slots.size).forEachIndexed { i, layer ->
            val bmp = WidgetPainter.renderNumberLayer(context, event.style, layer)
            val pad = WidgetPainter.numberPadding(event.style, layer.size)
            val p = parity[layer.unit.name] ?: 0
            rv.setViewVisibility(slots[i], View.VISIBLE)
            rv.setViewPadding(slots[i], max(0f, layer.bounds.left - pad).toInt(), max(0f, layer.bounds.top - pad).toInt(), 0, 0)
            rv.setImageViewBitmap(if (p == 0) imgA[i] else imgB[i], bmp)
            rv.setDisplayedChild(flips[i], p)
        }

        // Sekunden (tickt im Launcher)
        result.seconds?.let { s ->
            val clock = RemoteViews(context.packageName, FontRes.secondsLayout(event.style.digitFont, event.style.numberWeight, s.countdown))
            clock.setTextViewTextSize(R.id.sec_clock, TypedValue.COMPLEX_UNIT_PX, s.textSizePx)
            clock.setTextColor(R.id.sec_clock, s.color)
            rv.addView(R.id.sec_slot, clock)
            rv.setViewPadding(R.id.sec_slot, s.left.toInt(), s.top.toInt(), 0, 0)
        }

        // Vordergrund
        if (WidgetPainter.hasTopLayer(event, format)) {
            val (top, topCanvas) = canvasBitmap(w, h, budgetPx)
            WidgetPainter.paint(context, topCanvas, w, h, event, format, now, locale, numberLayers = true, layer = WidgetPainter.Layer.TOP)
            rv.setImageViewBitmap(R.id.top, top)
            rv.setViewVisibility(R.id.top, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.top, View.GONE)
        }
        rv.setContentDescription(R.id.root, describe(context, event, now))
        return rv
    }

    private fun describe(context: Context, event: CountdownEvent, now: Long): String {
        val rem = Countdown.remaining(event.effectiveTarget(now), now, event.widgetUnits)
        val prefix = if (event.mode == EventMode.SINCE) context.getString(R.string.since_prefix) + " " else ""
        return event.title + ": " + prefix + rem.values.joinToString(" ") {
            it.value.toString() + " " + context.getString(WidgetPainter.shortLabel(it.unit))
        }
    }

    /** Übersicht: die nächsten Events untereinander. */
    fun renderOverview(baseContext: Context, widgetId: Int): RemoteViews {
        EventRepository.init(baseContext)
        AppSettings.init(baseContext)
        val context = AppSettings.wrap(baseContext)
        val options = AppWidgetManager.getInstance(baseContext).getAppWidgetOptions(widgetId)
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }?.toFloat() ?: 320f
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: 180f
        val density = context.resources.displayMetrics.density
        val now = System.currentTimeMillis()
        val events = EventRepository.state.value
            .sortedWith(compareBy({ it.mode == EventMode.SINCE || it.effectiveTarget(now) < now }, { it.effectiveTarget(now) }))
        val dm = context.resources.displayMetrics
        val (bmp, canvas) = canvasBitmap(wDp * density, hDp * density, dm.widthPixels.toDouble() * dm.heightPixels)
        WidgetPainter.paintOverview(
            context, canvas, wDp * density, hDp * density, events, now,
            context.getString(R.string.widget_overview_label), context.getString(R.string.widget_pick_empty),
        )
        val rv = RemoteViews(context.packageName, R.layout.widget_overview)
        rv.setImageViewBitmap(R.id.bg, bmp)
        rv.setOnClickPendingIntent(R.id.bg, openIntent(context, widgetId, null))
        return rv
    }
}
