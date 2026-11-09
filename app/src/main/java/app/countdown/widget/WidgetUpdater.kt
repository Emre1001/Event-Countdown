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

    /** Zeichnen läuft nacheinander auf einem eigenen Thread – nie auf dem Hauptthread eines Receivers. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "widget-render") }

    /** Wann die nächste sichtbare Zahl wechselt (für die Sicherheitsnetze, solange die App läuft). */
    @Volatile
    var nextDueAt: Long = Long.MAX_VALUE
        private set

    /**
     * Robuster Ablauf für jeden Auslöser (Alarm, System-Update, Bildschirm an, App geöffnet):
     * 1. den nächsten Alarm SOFORT neu stellen – so reißt die Kette nie ab, selbst wenn das Zeichnen
     *    abbricht oder Android den Prozess danach einfriert,
     * 2. im Hintergrund zeichnen (ein Receiver auf dem Hauptthread würde bei langsamer Arbeit beendet),
     * 3. danach erneut planen und den Broadcast sauber abschließen.
     */
    fun refreshInBackground(context: Context, pending: android.content.BroadcastReceiver.PendingResult? = null, then: (() -> Unit)? = null) {
        val app = context.applicationContext
        runCatching { schedule(app) }
        worker.execute {
            try {
                updateAll(app)
                then?.invoke()
            } catch (_: Throwable) {
                // Eine fehlgeschlagene Zeichnung darf die nächste Aktualisierung nie verhindern.
            } finally {
                runCatching { schedule(app) }
                pending?.finish()
            }
        }
    }

    fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        return (providers.map { it.first } + OverviewWidgetProvider::class.java).any {
            mgr.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    /** Countdown-Widgets mit ihrem Standardformat (die Größe bestimmt das tatsächliche Layout). */
    private val providers = listOf(
        SmallWidgetProvider::class.java to WidgetFormat.SQUARE,
        WideWidgetProvider::class.java to WidgetFormat.WIDE,
        BarWidgetProvider::class.java to WidgetFormat.BAR,
        LargeWidgetProvider::class.java to WidgetFormat.SQUARE,
    )

    @Synchronized
    fun updateWidget(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        val cls = mgr.getAppWidgetInfo(widgetId)?.provider?.className ?: return
        if (cls == OverviewWidgetProvider::class.java.name) {
            mgr.updateAppWidget(widgetId, WidgetRenderer.renderOverview(context, widgetId))
            return
        }
        val format = providers.firstOrNull { it.first.name == cls }?.second ?: return
        mgr.updateAppWidget(widgetId, WidgetRenderer.render(context, widgetId, format))
    }

    @Synchronized
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        providers.forEach { (cls, format) ->
            mgr.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                runCatching { mgr.updateAppWidget(id, WidgetRenderer.render(context, id, format)) }
            }
        }
        mgr.getAppWidgetIds(ComponentName(context, OverviewWidgetProvider::class.java)).forEach { id ->
            runCatching { mgr.updateAppWidget(id, WidgetRenderer.renderOverview(context, id)) }
        }
        schedule(context)
    }

    private fun eventWidgetIds(context: Context): List<Int> {
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
        val candidates = eventWidgetIds(context).mapNotNull { EventRepository.widgetEvent(it) }.map { e ->
            val finest = e.widgetUnits.lastOrNull { it != CountdownUnit.SECONDS } ?: CountdownUnit.MINUTES
            Countdown.nextChangeMillis(e.effectiveTarget(now), now, finest)
        }.toMutableList()
        val mgr = AppWidgetManager.getInstance(context)
        if (mgr.getAppWidgetIds(ComponentName(context, OverviewWidgetProvider::class.java)).isNotEmpty()) {
            EventRepository.state.value.forEach { e ->
                candidates += Countdown.nextChangeMillis(e.effectiveTarget(now), now, CountdownUnit.HOURS)
            }
        }
        val next = candidates.minOrNull()
        if (next == null) {
            nextDueAt = Long.MAX_VALUE
            alarm.cancel(pi)
            return
        }
        val at = maxOf(next + 40, now + 500)
        nextDueAt = at
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (exact) alarm.setExact(AlarmManager.RTC, at, pi) else alarm.setWindow(AlarmManager.RTC, at, 5_000, pi)
    }
}
