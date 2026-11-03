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
import app.countdown.model.CountdownEvent
import app.countdown.model.WidgetFormat
import app.countdown.render.FontRes
import app.countdown.render.WidgetPainter
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Baut die RemoteViews eines Widgets: das komplette Design als Bild (Farben, Verläufe, Bilder,
 * Schriften, freie Positionen) und – falls gewählt – einen TextClock für die Sekunden, der im
 * Launcher selbst tickt. Bei jedem Minutenwechsel wird weich übergeblendet.
 */
object WidgetRenderer {

    fun render(baseContext: Context, widgetId: Int, fallbackFormat: WidgetFormat): RemoteViews {
        EventRepository.init(baseContext)
        AppSettings.init(baseContext)
        val context = AppSettings.wrap(baseContext)
        val event = EventRepository.widgetEvent(widgetId)
        val options = AppWidgetManager.getInstance(baseContext).getAppWidgetOptions(widgetId)
        val sizes = sizesDp(options, fallbackFormat)
        val parity = 1 - EventRepository.flipParity(widgetId) // jedes Update blendet auf das andere Bild
        EventRepository.saveFlipParity(widgetId, parity)

        // Speicherbudget des Launchers für Widget-Bitmaps: Bildschirmfläche × 4 B × 1,5 (mit Reserve).
        val dm = context.resources.displayMetrics
        val budgetPx = dm.widthPixels.toLong() * dm.heightPixels * 1.5 * 0.7 / sizes.size

        val views = sizes.map { (w, h) -> build(context, widgetId, event, w, h, parity, budgetPx) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
            RemoteViews(sizes.indices.associate { SizeF(sizes[it].first, sizes[it].second) to views[it] })
        } else {
            views.first()
        }
    }

    /** Hochformat-Größe immer, Querformat zusätzlich (Android 12+), damit die Sekunden genau sitzen. */
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
        if (list.isEmpty()) list += if (fallback == WidgetFormat.WIDE) 320f to 160f else 160f to 160f
        return list.distinct().take(2)
    }

    private fun build(
        context: Context,
        widgetId: Int,
        event: CountdownEvent?,
        wDp: Float,
        hDp: Float,
        parity: Int,
        budgetPx: Double,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_root)
        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("countdown://widget/$widgetId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            event?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it.id) }
        }
        rv.setOnClickPendingIntent(
            R.id.root,
            PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )

        val density = context.resources.displayMetrics.density
        val w = wDp * density
        val h = hDp * density
        val scale = min(1.0, sqrt(budgetPx / (w * h))).toFloat()
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)

        rv.removeAllViews(R.id.sec_slot)
        if (event == null) {
            WidgetPainter.paintPlaceholder(context, canvas, w, h, context.getString(R.string.widget_placeholder))
        } else {
            val format = if (wDp / hDp > 1.45f) WidgetFormat.WIDE else WidgetFormat.SQUARE
            val result = WidgetPainter.Result()
            WidgetPainter.paint(
                context, canvas, w, h, event, format, System.currentTimeMillis(),
                drawSeconds = false, locale = AppSettings.locale(), result = result,
            )
            result.seconds?.let { s ->
                val clock = RemoteViews(
                    context.packageName,
                    FontRes.secondsLayout(event.style.font, event.style.numberWeight, s.countdown),
                )
                clock.setTextViewTextSize(R.id.sec_clock, TypedValue.COMPLEX_UNIT_PX, s.textSizePx)
                clock.setTextColor(R.id.sec_clock, s.color)
                rv.addView(R.id.sec_slot, clock)
                rv.setViewPadding(R.id.sec_slot, s.left.toInt(), s.top.toInt(), 0, 0)
            }
            rv.setContentDescription(R.id.root, describe(context, event))
        }
        rv.setViewVisibility(R.id.sec_slot, View.VISIBLE)
        rv.setImageViewBitmap(if (parity == 0) R.id.img_a else R.id.img_b, bmp)
        rv.setDisplayedChild(R.id.flip, parity)
        return rv
    }

    private fun describe(context: Context, event: CountdownEvent): String {
        val rem = app.countdown.model.Countdown.remaining(event.targetMillis, System.currentTimeMillis(), event.widgetUnits)
        return event.title + ": " + rem.values.joinToString(" ") {
            it.value.toString() + " " + context.getString(WidgetPainter.shortLabel(it.unit))
        }
    }
}
