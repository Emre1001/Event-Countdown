package app.countdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.countdown.data.EventRepository

/** Alarm (Minutenwechsel) sowie Boot, Zeit-, Zeitzonen- und Sprachwechsel. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRepository.init(context)
        WidgetUpdater.updateAll(context)
    }
}
