package app.countdown.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import app.countdown.data.EventRepository

abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        EventRepository.init(context)
        WidgetUpdater.updateAll(context)
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
