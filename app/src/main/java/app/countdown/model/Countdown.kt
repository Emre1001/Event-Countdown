package app.countdown.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class UnitValue(val unit: CountdownUnit, val value: Long)

data class Remaining(val values: List<UnitValue>, val isPast: Boolean)

object Countdown {

    /**
     * Zerlegt die Zeitspanne kaskadierend auf die gewählten Einheiten. Jahre und Monate werden
     * kalendergenau gezählt, der Rest in festen Längen. Nur "Stunden" gewählt → Gesamtstunden.
     * Liegt das Ziel in der Vergangenheit, wird die vergangene Zeit gezählt.
     */
    fun remaining(targetMillis: Long, nowMillis: Long, ordered: List<CountdownUnit>): Remaining {
        val past = targetMillis < nowMillis
        // Countdown rundet auf (bei 0,3 s Rest steht noch 1 s), "seit" rundet ab.
        val fromMs = if (past) targetMillis else nowMillis - 999
        val toMs = if (past) nowMillis else targetMillis
        val zone = ZoneId.systemDefault()
        var from = Instant.ofEpochMilli(fromMs).atZone(zone)
        val to = Instant.ofEpochMilli(toMs).atZone(zone)
        val values = ArrayList<UnitValue>(ordered.size)
        var restSec = -1L
        for (unit in ordered) {
            when (unit) {
                CountdownUnit.YEARS -> {
                    val y = maxOf(0L, ChronoUnit.YEARS.between(from, to))
                    from = from.plusYears(y)
                    values += UnitValue(unit, y)
                }
                CountdownUnit.MONTHS -> {
                    val m = maxOf(0L, ChronoUnit.MONTHS.between(from, to))
                    from = from.plusMonths(m)
                    values += UnitValue(unit, m)
                }
                else -> {
                    if (restSec < 0) restSec = maxOf(0L, (to.toInstant().toEpochMilli() - from.toInstant().toEpochMilli()) / 1000)
                    val v = restSec / unit.seconds
                    restSec %= unit.seconds
                    values += UnitValue(unit, v)
                }
            }
        }
        return Remaining(values, past)
    }

    /** Zeitpunkt, an dem sich die kleinste angezeigte Einheit das nächste Mal ändert (Jahre/Monate/Wochen: täglich prüfen). */
    fun nextChangeMillis(targetMillis: Long, nowMillis: Long, finest: CountdownUnit): Long {
        val u = if (finest.seconds == 0L || finest == CountdownUnit.WEEKS) CountdownUnit.DAYS.seconds else finest.seconds
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

    /** Fortschritt von [start] bis [target] (0..1). */
    fun progress(start: Long, target: Long, now: Long): Float {
        if (target <= start) return 1f
        return ((now - start).toDouble() / (target - start)).toFloat().coerceIn(0f, 1f)
    }

    fun formatDate(targetMillis: Long, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(targetMillis).atZone(ZoneId.systemDefault()))

    fun formatDay(targetMillis: Long, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(targetMillis).atZone(ZoneId.systemDefault()))
}
