package com.jakober.matchday.notify

import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.domain.Rsvp
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** Eine vorgemerkte Benachrichtigung. */
data class ScheduledReminder(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String,
)

/**
 * Plant lokale Benachrichtigungen ein. Bewusst lokal und nicht per Push-Server:
 * Beide Anlaesse stehen zum Planungszeitpunkt fest, damit braucht es weder
 * Firebase noch APNs-Zertifikate.
 */
interface ReminderScheduler {
    /** Fragt die Benachrichtigungserlaubnis ab. Liefert true, wenn erteilt. */
    suspend fun ensurePermission(): Boolean

    /** Verwirft alle bisherigen Vormerkungen und setzt die uebergebenen neu. */
    fun replaceAll(reminders: List<ScheduledReminder>)
}

expect fun createReminderScheduler(): ReminderScheduler

/**
 * Berechnet aus Spielplan, Zusagen und Einstellungen die faelligen
 * Benachrichtigungen.
 *
 * Die Obergrenze ist keine Willkuer: iOS erlaubt hoechstens 64 vorgemerkte
 * lokale Benachrichtigungen pro App und verwirft alles darueber stillschweigend.
 * Android kennt eine aehnliche Grenze bei den Alarmen. Deshalb planen wir nur
 * das zeitlich naechste Fenster und schreiben es bei jedem App-Start fort.
 */
object ReminderPlanner {

    const val MAX_PENDING = 60

    fun plan(
        matches: List<Match>,
        rsvps: Map<String, Rsvp>,
        settings: ReminderSettings,
        now: Instant,
    ): List<ScheduledReminder> {
        val upcoming = matches.filter { it.start > now }.sortedBy { it.start }
        val out = mutableListOf<ScheduledReminder>()

        for (match in upcoming) {
            if (settings.kickoffReminderEnabled) {
                val at = match.start - settings.minutesBefore.minutes
                // Liegt der Erinnerungszeitpunkt schon hinter uns, faellt er aus.
                if (at > now) {
                    out += ScheduledReminder(
                        id = "kickoff:${match.id}",
                        at = at,
                        title = match.displayTitle,
                        body = kickoffBody(settings.minutesBefore, match.location),
                    )
                }
            }

            if (settings.undecidedReminderEnabled && rsvps[match.id] == null) {
                val at = match.start - 7.days
                if (at > now) {
                    out += ScheduledReminder(
                        id = "undecided:${match.id}",
                        at = at,
                        title = "Kommst du mit?",
                        body = "${match.displayTitle} ist in einer Woche - du hast noch nicht geantwortet.",
                    )
                }
            }
        }

        return out.sortedBy { it.at }.take(MAX_PENDING)
    }

    private fun kickoffBody(minutesBefore: Int, location: String?): String {
        val whenText = when {
            minutesBefore % (24 * 60) == 0 -> {
                val days = minutesBefore / (24 * 60)
                if (days == 1) "Morgen um diese Zeit ist Anpfiff" else "In $days Tagen ist Anpfiff"
            }
            minutesBefore % 60 == 0 -> {
                val hours = minutesBefore / 60
                if (hours == 1) "Anpfiff in einer Stunde" else "Anpfiff in $hours Stunden"
            }
            else -> "Anpfiff in $minutesBefore Minuten"
        }
        return if (location.isNullOrBlank()) whenText else "$whenText - $location"
    }
}
