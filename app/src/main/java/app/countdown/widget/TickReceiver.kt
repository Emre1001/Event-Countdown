package app.countdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.countdown.data.EventRepository
import app.countdown.data.Reminders

/** Alarm (Minutenwechsel) sowie Boot, App-Update, Zeit-, Zeitzonen- und Sprachwechsel. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRepository.init(context)
        val pending = goAsync()
        val isTick = intent.action == "app.countdown.TICK"
        WidgetUpdater.refreshInBackground(context, pending) {
            // Nach Neustart, Update oder Zeitänderung auch die Erinnerungen neu planen.
            if (!isTick) Reminders.schedule(context)
        }
    }
}
