package com.jakober.matchday.ui.components

import com.jakober.matchday.i18n.S
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Deutsche Datumsangaben von Hand.
 *
 * kotlinx-datetime bringt in gemeinsamem Code keine lokalisierte Formatierung
 * mit - die Alternative waeren zwei Plattformimplementierungen, was fuer eine
 * Handvoll Namen den Aufwand nicht lohnt.
 */
/**
 * Wandelt einen Zeitpunkt in die lokale Zeit des Geraets.
 *
 * Bewusst als Top-Level-Funktion: Als Member eines `object` waere es eine
 * Member-Extension und liesse sich nicht importieren.
 */
fun Instant.local(): LocalDateTime = toLocalDateTime(TimeZone.currentSystemDefault())

object DateText {

    private val WEEKDAYS_SHORT: List<String> get() = S.weekdaysShort
    private val WEEKDAYS_LONG: List<String> get() = S.weekdaysLong
    private val MONTHS: List<String> get() = S.months

    val weekdayHeaders: List<String> get() = WEEKDAYS_SHORT

    fun zone(): TimeZone = TimeZone.currentSystemDefault()

    /** "Sa" */
    fun weekdayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.ordinal]

    /** "Samstag" */
    fun weekdayLong(date: LocalDate): String = WEEKDAYS_LONG[date.dayOfWeek.ordinal]

    /** "August 2026" */
    fun monthYear(date: LocalDate): String = "${MONTHS[date.monthNumber - 1]} ${date.year}"

    /** "August" */
    fun monthName(monthNumber: Int): String = MONTHS[monthNumber - 1]

    /** "15. August 2026" */
    fun fullDate(date: LocalDate): String =
        "${date.dayOfMonth}. ${MONTHS[date.monthNumber - 1]} ${date.year}"

    /** "20:30" */
    fun time(dateTime: LocalDateTime): String =
        "${dateTime.hour.pad()}:${dateTime.minute.pad()}"

    /**
     * Umgangssprachliche Angabe fuer die Liste: "Heute", "Morgen",
     * "In 3 Tagen", danach das Datum.
     */
    fun relativeDay(date: LocalDate, today: LocalDate = todayDate()): String {
        val days = today.daysUntil(date)
        return when {
            days == 0 -> S.today
            days == 1 -> S.tomorrow
            days == 2 -> S.dayAfterTomorrow
            days in 3..6 -> S.inDays(days)
            days < 0 -> S.past
            else -> "${weekdayShort(date)}, ${date.dayOfMonth}. ${MONTHS[date.monthNumber - 1]}"
        }
    }

    fun todayDate(): LocalDate = Clock.System.now().toLocalDateTime(zone()).date

    /**
     * Tage eines Monatsrasters, beginnend am Montag der Woche, in die der
     * Monatserste faellt - immer 42 Felder, damit das Raster nicht springt,
     * wenn man zwischen Monaten blaettert.
     */
    fun monthGrid(year: Int, month: Int): List<LocalDate> {
        val first = LocalDate(year, month, 1)
        // dayOfWeek.ordinal ist 0 fuer Montag, genau was wir brauchen.
        val start = first.minusDays(first.dayOfWeek.ordinal)
        return (0 until 42).map { start.plus(it, DateTimeUnit.DAY) }
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)

    private fun Int.pad(): String = if (this < 10) "0$this" else toString()
}
