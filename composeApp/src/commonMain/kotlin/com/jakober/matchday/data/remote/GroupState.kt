package com.jakober.matchday.data.remote

import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus

/**
 * Was die App gerade ueber die Gruppe weiss: die Mitglieder und deren
 * Antworten, so wie sie beim letzten Abgleich in der Datenbank standen.
 */
data class GroupSnapshot(
    val members: List<MemberDto> = emptyList(),
    val rsvps: List<RsvpDto> = emptyList(),
)

/**
 * Setzt die Teilnehmerliste eines Spiels zusammen.
 *
 * Die eigene Antwort kommt aus der lokalen Ablage, nicht aus der Datenbank:
 * Sie ist dort sofort da, auch ohne Netz, und wird im Hintergrund
 * hochgeschoben. Ohne diesen Vorrang wuerde eine gerade gesetzte Zusage
 * kurz wieder verschwinden.
 */
fun participantsOfMatch(
    matchId: String,
    membership: GroupMembership?,
    snapshot: GroupSnapshot,
    localRsvps: Map<String, Rsvp>,
    ownName: String,
    ownColor: Long,
): List<Participant> {
    val ownLocal = localRsvps[matchId]

    // Ohne Gruppe kennt die App nur die eigene Antwort.
    if (membership == null) {
        return ownLocal?.let {
            listOf(
                Participant(
                    id = "me",
                    name = ownName,
                    colorArgb = ownColor,
                    status = it.status,
                    comment = it.comment,
                    isMe = true,
                )
            )
        }.orEmpty()
    }

    val (teamId, matchUid) = splitMatchId(matchId) ?: return emptyList()
    val calendarId = membership.calendarIds[teamId]

    val byMember = snapshot.members.associateBy { it.id }
    val remote = snapshot.rsvps
        .filter { it.calendarId == calendarId && it.matchUid == matchUid }
        .mapNotNull { dto ->
            val member = byMember[dto.memberId] ?: return@mapNotNull null
            val status = runCatching { RsvpStatus.valueOf(dto.status) }.getOrNull()
                ?: return@mapNotNull null
            Participant(
                id = member.id,
                name = member.displayName,
                colorArgb = member.color,
                status = status,
                comment = dto.comment,
                isMe = member.id == membership.memberId,
            )
        }

    // Eigenen Eintrag durch den lokalen Stand ersetzen.
    val others = remote.filterNot { it.id == membership.memberId }
    val own = ownLocal?.let {
        val me = byMember[membership.memberId]
        Participant(
            id = membership.memberId,
            name = me?.displayName ?: ownName,
            colorArgb = me?.color ?: ownColor,
            status = it.status,
            comment = it.comment,
            isMe = true,
        )
    }

    return if (own == null) others else others + own
}

/** Zerlegt "fcbayern#68600e4a1ac72@2.calovo" in Mannschaft und Termin-UID. */
fun splitMatchId(matchId: String): Pair<String, String>? {
    val index = matchId.indexOf('#')
    if (index <= 0 || index == matchId.lastIndex) return null
    return matchId.substring(0, index) to matchId.substring(index + 1)
}
