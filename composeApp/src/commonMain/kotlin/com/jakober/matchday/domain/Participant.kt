package com.jakober.matchday.domain

/**
 * Jemand, der zu einem Spiel geantwortet hat.
 *
 * Solange kein Backend angebunden ist, ist das nur der Nutzer selbst. Die
 * Oberflaeche arbeitet aber schon mit einer Liste, damit sie sich beim
 * Anschluss der Gruppe nicht mehr aendern muss.
 */
data class Participant(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val status: RsvpStatus,
    /** Begruendung, meist bei einer Absage. */
    val comment: String? = null,
    /** Zeigt an, dass dieser Eintrag der Nutzer selbst ist. */
    val isMe: Boolean = false,
) {
    val initials: String
        get() = name.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/**
 * Gesamtbild der Antworten zu einem Spiel - steuert die farbige Umrandung in
 * Liste und Kalender.
 */
enum class MatchMood {
    /** Zu wenige Antworten, um etwas auszusagen. */
    OPEN,

    /** Genug Zusagen - daraus wird etwas. */
    ENOUGH_IN,

    /** Abgesagt, und nicht genug Zusagen, die das auffangen. */
    DECLINED,
}

/** Ab so vielen Zusagen gilt ein Spiel als gesetzt. */
const val MIN_FOR_GREEN = 2

/**
 * Genug Zusagen schlaegt eine Absage: Wenn zwei mitkommen, findet es statt,
 * auch wenn ein Dritter passen muss.
 */
fun moodOf(participants: List<Participant>): MatchMood = when {
    participants.count { it.status == RsvpStatus.IN } >= MIN_FOR_GREEN -> MatchMood.ENOUGH_IN
    participants.any { it.status == RsvpStatus.OUT } -> MatchMood.DECLINED
    else -> MatchMood.OPEN
}

/** Liefert die Antworten zu einem Spiel. */
fun interface ParticipantsSource {
    operator fun invoke(matchId: String): List<Participant>
}
