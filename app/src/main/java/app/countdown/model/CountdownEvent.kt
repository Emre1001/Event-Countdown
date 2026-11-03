package app.countdown.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class CountdownUnit(val seconds: Long) {
    DAYS(86_400),
    HOURS(3_600),
    MINUTES(60),
    SECONDS(1),
}

data class CountdownEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val targetMillis: Long,
    val units: Set<CountdownUnit> = setOf(CountdownUnit.DAYS, CountdownUnit.HOURS, CountdownUnit.MINUTES),
    val style: EventStyle = EventStyle(),
) {
    /** Ausgewählte Einheiten, immer von groß nach klein sortiert. */
    val orderedUnits: List<CountdownUnit>
        get() = CountdownUnit.entries.filter { it in units }.ifEmpty { listOf(CountdownUnit.DAYS) }

    /**
     * Einheiten im Widget. Sekunden laufen dort über eine tickende Uhr und zeigen immer den Rest
     * innerhalb der Minute – deshalb kommen Minuten automatisch dazu, wenn Sekunden gewählt sind.
     */
    val widgetUnits: List<CountdownUnit>
        get() {
            val u = orderedUnits.toMutableSet()
            if (CountdownUnit.SECONDS in u) u += CountdownUnit.MINUTES
            return CountdownUnit.entries.filter { it in u }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("target", targetMillis)
        put("units", JSONArray(orderedUnits.map { it.name }))
        put("style", style.toJson())
    }

    companion object {
        /** Events liegen immer auf einer vollen Minute (Voraussetzung für die Widget-Sekunden). */
        fun normalize(millis: Long): Long = millis - Math.floorMod(millis, 60_000L)

        fun fromJson(o: JSONObject): CountdownEvent {
            val unitsJson = o.optJSONArray("units")
            val units = buildSet {
                if (unitsJson != null) for (i in 0 until unitsJson.length()) {
                    runCatching { CountdownUnit.valueOf(unitsJson.getString(i)) }.getOrNull()?.let(::add)
                }
            }.ifEmpty { CountdownUnit.entries.toSet() }
            val style = o.optJSONObject("style")?.let { EventStyle.fromJson(it) } ?: legacyStyle(o)
            return CountdownEvent(
                id = o.getString("id"),
                title = o.optString("title", ""),
                targetMillis = normalize(o.getLong("target")),
                units = units,
                style = style,
            )
        }

        /** Übernahme der Farben aus Version 1 (bg / text / accent / alpha). */
        private fun legacyStyle(o: JSONObject): EventStyle {
            val d = EventStyle()
            return d.copy(
                background = Fill.solid(o.optInt("bg", d.background.first)),
                numbers = Fill.solid(o.optInt("text", d.numbers.first)),
                accent = Fill.solid(o.optInt("accent", d.accent.first)),
                backgroundAlpha = o.optDouble("alpha", 1.0).toFloat(),
            )
        }
    }
}
