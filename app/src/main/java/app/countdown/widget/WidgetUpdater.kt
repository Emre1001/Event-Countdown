package app.countdown.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import app.countdown.data.EventRepository
import app.countdown.model.Countdown
import app.countdown.model.CountdownUnit
import app.countdown.model.WidgetFormat

object WidgetUpdater {
    private const val ACTION_TICK = "app.countdown.TICK"

    private val providers = listOf(
        SmallWidgetProvider::class.java to WidgetFormat.SQUARE,
        WideWidgetProvider::class.java to WidgetFormat.WIDE,
    )

    fun updateWidget(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        val cls = mgr.getAppWidgetInfo(widgetId)?.provider?.className ?: return
        val format = providers.firstOrNull { it.first.name == cls }?.second ?: return
        mgr.updateAppWidget(widgetId, WidgetRenderer.render(context, widgetId, format))
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        providers.forEach { (cls, format) ->
            mgr.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                runCatching { mgr.updateAppWidget(id, WidgetRenderer.render(context, id, format)) }
            }
        }
        schedule(context)
    }

    private fun allWidgetIds(context: Context): List<Int> {
        val mgr = AppWidgetManager.getInstance(context)
        return providers.flatMap { (cls, _) -> mgr.getAppWidgetIds(ComponentName(context, cls)).toList() }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, TickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Plant genau den Moment, in dem sich die nächste sichtbare Zahl ändert. Sekunden zählen hier
     * nicht – die tickt der Launcher selbst. Nicht weckender Alarm (RTC): bei ausgeschaltetem
     * Bildschirm wird nichts gerechnet, beim Einschalten sofort nachgeholt.
     */
    fun schedule(context: Context) {
        EventRepository.init(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val now = System.currentTimeMillis()
        val next = allWidgetIds(context)
            .mapNotNull { EventRepository.widgetEvent(it) }
            .minOfOrNull { e ->
                val finest = e.widgetUnits.lastOrNull { it != CountdownUnit.SECONDS } ?: CountdownUnit.MINUTES
                Countdown.nextChangeMillis(e.targetMillis, now, finest)
            }
        if (next == null) {
            alarm.cancel(pi)
            return
        }
        val at = maxOf(next + 40, now + 500)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (exact) alarm.setExact(AlarmManager.RTC, at, pi) else alarm.setWindow(AlarmManager.RTC, at, 5_000, pi)
    }
}
