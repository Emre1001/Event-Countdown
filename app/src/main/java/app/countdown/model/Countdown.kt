package app.countdown.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class UnitValue(val unit: CountdownUnit, val value: Long)

data class Remaining(val values: List<UnitValue>, val isPast: Boolean)

object Countdown {

    /**
     * Zerlegt die Restzeit kaskadierend auf die gewählten Einheiten.
     * Nur Stunden gewählt -> Gesamtstunden; Tage + Minuten -> Tage und der Rest in Minuten.
     * Liegt das Event in der Vergangenheit, wird die vergangene Zeit gezählt.
     */
    fun remaining(targetMillis: Long, nowMillis: Long, ordered: List<CountdownUnit>): Remaining {
        val diff = targetMillis - nowMillis
        val past = diff < 0
        var secs = if (past) (-diff) / 1000 else (diff + 999) / 1000
        val values = ordered.map { unit ->
            val v = secs / unit.seconds
            secs %= unit.seconds
            UnitValue(unit, v)
        }
        return Remaining(values, past)
    }

    /** Zeitpunkt, an dem sich die kleinste angezeigte Einheit das nächste Mal ändert. */
    fun nextChangeMillis(targetMillis: Long, nowMillis: Long, finest: CountdownUnit): Long {
        val u = finest.seconds
        val diff = targetMillis - nowMillis
        return if (diff >= 0) {
            val rSec = (diff + 999) / 1000
            val k = rSec / u
            if (k <= 0) targetMillis + 1 else targetMillis - (k * u - 1) * 1000
        } else {
            val e = (-diff) / 1000
            targetMillis + (e / u + 1) * u * 1000
        }
    }

    fun formatDate(targetMillis: Long, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(targetMillis).atZone(ZoneId.systemDefault()))
}
