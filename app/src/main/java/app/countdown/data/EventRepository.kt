package app.countdown.data

import android.content.Context
import android.content.SharedPreferences
import app.countdown.model.CountdownEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Einfacher, abhängigkeitsfreier Speicher (SharedPreferences + JSON). */
object EventRepository {
    private lateinit var events: SharedPreferences
    private lateinit var widgets: SharedPreferences
    private val _state = MutableStateFlow<List<CountdownEvent>>(emptyList())
    val state: StateFlow<List<CountdownEvent>> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::events.isInitialized) return
        val app = context.applicationContext
        events = app.getSharedPreferences("events", Context.MODE_PRIVATE)
        widgets = app.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        _state.value = load()
    }

    private fun load(): List<CountdownEvent> {
        val raw = events.getString("list", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { CountdownEvent.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<CountdownEvent>) {
        _state.value = list
        events.edit().putString("list", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun get(id: String?): CountdownEvent? = id?.let { i -> _state.value.firstOrNull { it.id == i } }

    @Synchronized
    fun upsert(event: CountdownEvent) {
        val list = _state.value
        persist(if (list.any { it.id == event.id }) list.map { if (it.id == event.id) event else it } else list + event)
    }

    /** Für den Import: Event mit neuer ID einfügen. */
    @Synchronized
    fun add(event: CountdownEvent) {
        persist(_state.value + event)
    }

    @Synchronized
    fun delete(id: String) {
        persist(_state.value.filterNot { it.id == id })
    }

    // --- Widget-Zuordnung ---------------------------------------------------------------

    fun bindWidget(widgetId: Int, eventId: String) {
        widgets.edit().putString("event_$widgetId", eventId).apply()
    }

    fun widgetEvent(widgetId: Int): CountdownEvent? = get(widgets.getString("event_$widgetId", null))

    fun unbindWidget(widgetId: Int) {
        val e = widgets.edit().remove("event_$widgetId")
        widgets.all.keys.filter { it.startsWith("u_${widgetId}_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** Letzter angezeigter Text und sichtbares Bild (0/1) einer Zahlen-Ebene im Widget. */
    fun unitState(widgetId: Int, unit: String): Pair<String?, Int> {
        val raw = widgets.getString("u_${widgetId}_$unit", null) ?: return null to 0
        val i = raw.lastIndexOf('|')
        return raw.substring(0, i) to (raw.substring(i + 1).toIntOrNull() ?: 0)
    }

    fun saveUnitState(widgetId: Int, unit: String, text: String, parity: Int) {
        widgets.edit().putString("u_${widgetId}_$unit", "$text|$parity").apply()
    }
}
