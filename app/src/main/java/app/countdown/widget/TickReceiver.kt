package app.countdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.countdown.data.EventRepository
import app.countdown.data.Reminders

/** Alarm (Minutenwechsel) sowie Boot, Zeit-, Zeitzonen- und Sprachwechsel. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRepository.init(context)
        WidgetUpdater.updateAll(context)
        // Nach Neustart, Update oder Zeitänderung auch die Erinnerungen neu planen.
        if (intent.action != "app.countdown.TICK") Reminders.schedule(context)
    }
}
