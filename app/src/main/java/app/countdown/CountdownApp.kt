package app.countdown

import android.app.Application
import app.countdown.data.AppSettings
import app.countdown.data.EventRepository
import app.countdown.update.UpdateController

class CountdownApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        EventRepository.init(this)
        UpdateController.init(this) // räumt außerdem liegengebliebene Installer-Dateien weg
    }
}
