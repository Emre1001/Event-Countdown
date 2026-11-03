package app.countdown.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.countdown.data.EventRepository

abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        EventRepository.init(context)
        WidgetUpdater.updateAll(context)
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

class SmallWidgetProvider : BaseWidgetProvider()

class WideWidgetProvider : BaseWidgetProvider()
