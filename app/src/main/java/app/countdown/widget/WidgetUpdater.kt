package app.countdown.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.countdown.R
import app.countdown.data.EventRepository
import app.countdown.model.Countdown

object WidgetUpdater {
    private const val ACTION_TICK = "app.countdown.TICK"

    private val providers = listOf(
        SmallWidgetProvider::class.java to R.layout.widget_small,
        WideWidgetProvider::class.java to R.layout.widget_wide,
    )

    fun updateWidget(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        val cls = mgr.getAppWidgetInfo(widgetId)?.provider?.className ?: return
        val layout = providers.firstOrNull { it.first.name == cls }?.second ?: return
        mgr.updateAppWidget(widgetId, WidgetRenderer.render(context, widgetId, layout))
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        providers.forEach { (cls, layout) ->
            mgr.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                mgr.updateAppWidget(id, WidgetRenderer.render(context, id, layout))
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
     * Plant genau den nächsten Zeitpunkt, an dem sich eine sichtbare Zahl ändert
     * (kein fester Minutentakt, solange nur Tage/Stunden angezeigt werden).
     */
    fun schedule(context: Context) {
        EventRepository.init(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val now = System.currentTimeMillis()
        val next = allWidgetIds(context)
            .mapNotNull { EventRepository.widgetEvent(it) }
            .minOfOrNull { e ->
                Countdown.nextChangeMillis(e.targetMillis, now, WidgetRenderer.widgetUnits(e).last())
            }
        if (next == null) {
            alarm.cancel(pi)
            return
        }
        // Mindestens 1 s in der Zukunft; kleine Verzögerung, damit die Sekunde sicher gewechselt hat.
        val at = maxOf(next + 300, now + 1000)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context))
    }
}
