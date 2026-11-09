package app.countdown.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.countdown.data.EventRepository

/**
 * Wichtig: Klassennamen nie ändern – sonst entfernt Android bestehende Widgets beim Update.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {
    /** Kommt auch alle 30 Minuten vom System (updatePeriodMillis) – das Sicherheitsnetz, falls ein Alarm verloren ging. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        EventRepository.init(context)
        WidgetUpdater.refreshInBackground(context, goAsync())
    }

    /** Größe geändert: neu zeichnen, damit nichts verzerrt und die Sekunden exakt sitzen. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        EventRepository.init(context)
        WidgetUpdater.updateWidget(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        EventRepository.init(context)
        appWidgetIds.forEach { EventRepository.unbindWidget(it) }
        WidgetUpdater.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.schedule(context)
    }
}

/** 2×2 */
class SmallWidgetProvider : BaseWidgetProvider()

/** 4×2 */
class WideWidgetProvider : BaseWidgetProvider()

/** 4×1 Leiste */
class BarWidgetProvider : BaseWidgetProvider()

/** 4×4 groß */
class LargeWidgetProvider : BaseWidgetProvider()

/** Übersicht über alle Events (ohne Auswahl). */
class OverviewWidgetProvider : BaseWidgetProvider()
