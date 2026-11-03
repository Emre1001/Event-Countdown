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
    val units: Set<CountdownUnit> = CountdownUnit.entries.toSet(),
    val background: Int = 0xFF0B0B0F.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF0A84FF.toInt(),
    val backgroundAlpha: Float = 1f,
) {
    /** Ausgewählte Einheiten, immer von groß nach klein sortiert. */
    val orderedUnits: List<CountdownUnit>
        get() = CountdownUnit.entries.filter { it in units }.ifEmpty { listOf(CountdownUnit.DAYS) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("target", targetMillis)
        put("units", JSONArray(orderedUnits.map { it.name }))
        put("bg", background)
        put("text", textColor)
        put("accent", accent)
        put("alpha", backgroundAlpha.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): CountdownEvent {
            val unitsJson = o.optJSONArray("units")
            val units = buildSet {
                if (unitsJson != null) for (i in 0 until unitsJson.length()) {
                    runCatching { CountdownUnit.valueOf(unitsJson.getString(i)) }.getOrNull()?.let(::add)
                }
            }.ifEmpty { CountdownUnit.entries.toSet() }
            return CountdownEvent(
                id = o.getString("id"),
                title = o.optString("title", ""),
                targetMillis = o.getLong("target"),
                units = units,
                background = o.optInt("bg", 0xFF0B0B0F.toInt()),
                textColor = o.optInt("text", 0xFFFFFFFF.toInt()),
                accent = o.optInt("accent", 0xFF0A84FF.toInt()),
                backgroundAlpha = o.optDouble("alpha", 1.0).toFloat(),
            )
        }
    }
}
