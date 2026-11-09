package app.countdown

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.countdown.data.AppSettings
import app.countdown.data.EventRepository
import app.countdown.update.UpdateController
import app.countdown.widget.WidgetUpdater

class CountdownApp : Application() {

    /**
     * Zusätzliches Sicherheitsnetz, solange der Prozess lebt: Minutentakt des Systems und
     * "Bildschirm an". Ist eine Zahl fällig (oder ging ein Alarm verloren), wird sofort neu gezeichnet.
     */
    private val live = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val due = System.currentTimeMillis() >= WidgetUpdater.nextDueAt
            if (intent.action != Intent.ACTION_TIME_TICK || due) WidgetUpdater.refreshInBackground(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        EventRepository.init(this)
        UpdateController.init(this) // räumt außerdem liegengebliebene Installer-Dateien weg
        // Bei jedem Prozessstart den Alarm prüfen – eine abgerissene Kette heilt sich damit selbst.
        runCatching { WidgetUpdater.schedule(this) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching { ContextCompat.registerReceiver(this, live, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }
}
