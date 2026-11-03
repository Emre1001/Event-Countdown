package app.countdown.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Kalender-Einheiten (Jahre, Monate) haben keine feste Länge; [seconds] ist dann 0. */
enum class CountdownUnit(val seconds: Long) {
    YEARS(0),
    MONTHS(0),
    WEEKS(604_800),
    DAYS(86_400),
    HOURS(3_600),
    MINUTES(60),
    SECONDS(1),
}

/** Countdown bis zu einem Zeitpunkt oder "Seit": wie lange etwas schon her ist (z. B. Scooter seit …). */
enum class EventMode { COUNTDOWN, SINCE }

/** Wiederholung: Geburtstage, Jahrestage, Monatliches … – der Countdown springt danach auf den nächsten Termin. */
enum class Repeat { NONE, YEARLY, MONTHLY, WEEKLY }

data class CountdownEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val note: String = "",
    val targetMillis: Long,
    val mode: EventMode = EventMode.COUNTDOWN,
    val repeat: Repeat = Repeat.NONE,
    val startMillis: Long = System.currentTimeMillis(),
    val reminder: Boolean = false,
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

    /** Der Zeitpunkt, auf den gerade gezählt wird (bei Wiederholung der nächste anstehende Termin). */
    fun effectiveTarget(now: Long): Long {
        if (mode == EventMode.SINCE || repeat == Repeat.NONE || targetMillis >= now) return targetMillis
        val zone = ZoneId.systemDefault()
        val base = Instant.ofEpochMilli(targetMillis).atZone(zone)
        var n = 0L
        var t = base
        // Sprung in großen Schritten, dann fein – auch für sehr alte Startdaten schnell.
        while (t.toInstant().toEpochMilli() < now) {
            n++
            t = when (repeat) {
                Repeat.YEARLY -> base.plusYears(n)
                Repeat.MONTHLY -> base.plusMonths(n)
                Repeat.WEEKLY -> base.plusWeeks(n)
                Repeat.NONE -> return targetMillis
            }
            if (n > 100_000) break
        }
        return t.toInstant().toEpochMilli()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("note", note)
        put("target", targetMillis)
        put("mode", mode.name)
        put("repeat", repeat.name)
        put("start", startMillis)
        put("reminder", reminder)
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
            }.ifEmpty { setOf(CountdownUnit.DAYS, CountdownUnit.HOURS, CountdownUnit.MINUTES) }
            val style = o.optJSONObject("style")?.let { EventStyle.fromJson(it) } ?: legacyStyle(o)
            val target = normalize(o.getLong("target"))
            return CountdownEvent(
                id = o.getString("id"),
                title = o.optString("title", ""),
                note = o.optString("note", ""),
                targetMillis = target,
                mode = EventStyle.enumOr(o.optString("mode"), EventMode.COUNTDOWN),
                repeat = EventStyle.enumOr(o.optString("repeat"), Repeat.NONE),
                startMillis = o.optLong("start", minOf(target, System.currentTimeMillis())),
                reminder = o.optBoolean("reminder", false),
                units = units,
                style = style,
            )
        }

        /** Übernahme der Farben aus der ersten Version (bg / text / accent / alpha). */
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
