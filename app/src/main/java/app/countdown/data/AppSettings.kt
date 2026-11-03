package app.countdown.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import app.countdown.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemePref { SYSTEM, LIGHT, DARK }

/** App-weite Einstellungen: Theme (System/Hell/Dunkel), Sprache, automatische Update-Prüfung. */
object AppSettings {
    val languages = listOf("de" to "Deutsch", "en" to "English", "tr" to "Türkçe")

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _theme = MutableStateFlow(ThemePref.SYSTEM)
    val theme: StateFlow<ThemePref> = _theme.asStateFlow()

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    /** Mittelpunkt (Fenster-Koordinaten) des zuletzt gedrückten Theme-Schalters für den Kreis-Reveal. */
    private var revealOrigin: Offset? = null

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemePref.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemePref.SYSTEM)
        _language.value = prefs.getString("language", "system")!!
        _autoUpdate.value = prefs.getBoolean("auto_update", true)
    }

    fun setTheme(pref: ThemePref, origin: Offset?) {
        revealOrigin = origin
        prefs.edit().putString("theme", pref.name).apply()
        _theme.value = pref
    }

    fun takeRevealOrigin(): Offset? = revealOrigin.also { revealOrigin = null }

    fun setLanguage(code: String) {
        prefs.edit().putString("language", code).apply()
        _language.value = code
        WidgetUpdater.updateAll(appContext) // Widgets zeigen ebenfalls die gewählte Sprache
    }

    fun setAutoUpdate(value: Boolean) {
        prefs.edit().putBoolean("auto_update", value).apply()
        _autoUpdate.value = value
    }

    fun locale(lang: String = _language.value): Locale =
        if (lang == "system") Locale.getDefault() else Locale.forLanguageTag(lang)

    /** Context mit der gewählten Sprache (bei "Systemsprache" unverändert). */
    fun wrap(base: Context, lang: String = _language.value): Context {
        if (lang == "system") return base
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return base.createConfigurationContext(cfg)
    }

    // --- Update-Zustand (Überspringen / Später) -----------------------------------------------
    var skippedVersion: String
        get() = prefs.getString("skipped_version", "") ?: ""
        set(v) { prefs.edit().putString("skipped_version", v).apply() }

    var remindAfter: Long
        get() = prefs.getLong("remind_after", 0L)
        set(v) { prefs.edit().putLong("remind_after", v).apply() }
}
