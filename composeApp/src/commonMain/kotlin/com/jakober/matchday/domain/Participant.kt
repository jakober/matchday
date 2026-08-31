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

/** Liefert die Antworten zu einem Spiel. */
fun interface ParticipantsSource {
    operator fun invoke(matchId: String): List<Participant>
}
