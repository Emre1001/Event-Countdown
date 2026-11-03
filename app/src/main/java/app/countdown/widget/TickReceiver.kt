package app.countdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.countdown.data.EventRepository

/** Wird vom Alarm und bei Boot / Zeit- / Zeitzonenwechsel aufgerufen. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRepository.init(context)
        WidgetUpdater.updateAll(context)
    }
}
