package com.jakober.matchday.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Ein abonnierter Kalender, z.B. der Spielplan eines Vereins oder einer Liga.
 *
 * Die Id ist die Kennung des Kalenders in der Gruppe (calendars.id). Aus ihr
 * wird die Spiel-Id gebildet, und die muss auf allen Geraeten gleich sein,
 * damit sich Zusagen ueberhaupt zuordnen lassen. Deshalb vergibt sie der
 * Server, nicht das Geraet.
 */
@Serializable
data class Subscription(
    val id: String,
    val name: String,
    /** ICS-Adresse. webcal:// wird beim Abruf auf https:// gemappt. */
    val url: String,
    /** Farbe als ARGB-Wert, faerbt die Markierung in Liste und Kalender. */
    val colorArgb: Long,
    /** Abzeichen des Kalenders, sofern der Admin eines hinterlegt hat. */
    val logoUrl: String? = null,
    /** Nur auf diesem Geraet: ob die Spiele angezeigt werden. */
    val lastSyncedAt: Instant? = null,
    val enabled: Boolean = true,
) {
    /** Bis zu drei Buchstaben fuer das Ersatzabzeichen: "FC Bayern" -> "FB", "Bundesliga" -> "BUN". */
    val badgeLabel: String
        get() {
            val words = name.trim().split(" ").filter { it.isNotEmpty() }
            val label = if (words.size >= 2) {
                words.take(3).joinToString("") { it.first().toString() }
            } else {
                name.trim().take(3)
            }
            return label.uppercase().ifEmpty { "?" }
        }
}

/** Ein Spiel, wie es aus einem ICS-Kalender gelesen wurde. */
@Serializable
data class Match(
    /** Stabil ueber Sync-Laeufe hinweg: Abo-Id plus UID aus dem Kalender. */
    val id: String,
    val subscriptionId: String,
    val start: Instant,
    val end: Instant? = null,
    val isAllDay: Boolean = false,
    /** Unveraenderte SUMMARY-Zeile, Rueckfallebene fuer die Anzeige. */
    val title: String,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val location: String? = null,
    val competition: String? = null,
) {
    /** Anzeigename: "Bayern - Dortmund", sonst die rohe Zeile. */
    val displayTitle: String
        get() = if (homeTeam != null && awayTeam != null) "$homeTeam - $awayTeam" else title
}

/**
 * Eine Antwort auf ein Spiel: Status und - bei einer Absage - optional ein
 * kurzer Grund fuer die anderen.
 */
@Serializable
data class Rsvp(
    val status: RsvpStatus,
    val comment: String? = null,
)

/** Zusage zu einem Spiel. */
enum class RsvpStatus {
    /** Noch nicht beantwortet - loest die Wochen-Erinnerung aus. */
    UNDECIDED,
    IN,
    OUT,
}

/** Eigenes Profil, spaeter auch das der anderen aus der Gruppe. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** Farbe des Monogramm-Avatars. */
    val colorArgb: Long,
    /** Bildadresse, sobald der Foto-Upload steht. Vorerst immer null. */
    val avatarUrl: String? = null,
) {
    /** Ein bis zwei Buchstaben fuer den Avatar. */
    val initials: String
        get() = name.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/** Wann vor Anpfiff erinnert wird. */
@Serializable
data class ReminderSettings(
    /** Minuten vor Anpfiff. 60 = eine Stunde, der Standard. */
    val minutesBefore: Int = 60,
    val kickoffReminderEnabled: Boolean = true,
    /** Erinnerung, wenn ein Spiel in einer Woche ansteht und die Zusage fehlt. */
    val undecidedReminderEnabled: Boolean = true,
) {
    companion object {
        /** Auswahl im Einstellungsdialog. */
        val CHOICES = listOf(15, 30, 60, 120, 180, 24 * 60)
    }
}
