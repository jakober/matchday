package com.jakober.matchday

import com.jakober.matchday.data.remote.GroupMembership
import com.jakober.matchday.data.remote.GroupSnapshot
import com.jakober.matchday.data.remote.MemberDto
import com.jakober.matchday.data.remote.RsvpDto
import com.jakober.matchday.data.remote.participantsOfMatch
import com.jakober.matchday.data.remote.splitMatchId
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantsTest {

    private val matchId = "fcbayern#68600e4a1ac72@2.calovo"
    private val calendarId = "cal-fcb"

    private val membership = GroupMembership(
        groupId = "g1",
        memberId = "m-max",
        inviteCode = "ABC123",
        groupName = "Stammtisch",
        calendarIds = mapOf("fcbayern" to calendarId, "dfb" to "cal-dfb"),
    )

    private val snapshot = GroupSnapshot(
        members = listOf(
            MemberDto(id = "m-max", displayName = "Max", color = 1),
            MemberDto(id = "m-anna", displayName = "Anna", color = 2),
        ),
        rsvps = listOf(
            RsvpDto("g1", "m-anna", calendarId, "68600e4a1ac72@2.calovo", "IN"),
            RsvpDto("g1", "m-max", calendarId, "68600e4a1ac72@2.calovo", "IN"),
        ),
    )

    @Test
    fun `zerlegt die Spiel-Id in Mannschaft und Termin`() {
        val parts = splitMatchId(matchId)
        assertEquals("fcbayern", parts?.first)
        assertEquals("68600e4a1ac72@2.calovo", parts?.second)
    }

    @Test
    fun `Termin-UIDs mit Sonderzeichen bleiben unversehrt`() {
        // Die calovo-UIDs enthalten ein @; nur am ersten # wird getrennt.
        assertEquals("a#b@c", splitMatchId("dfb#a#b@c")?.second)
    }

    @Test
    fun `ohne Gruppe zaehlt nur die eigene Antwort`() {
        val result = participantsOfMatch(
            matchId = matchId,
            membership = null,
            snapshot = GroupSnapshot(),
            localRsvps = mapOf(matchId to Rsvp(RsvpStatus.IN)),
            ownName = "Max",
            ownColor = 1,
        )

        assertEquals(1, result.size)
        assertTrue(result.single().isMe)
    }

    @Test
    fun `in der Gruppe erscheinen alle Antworten`() {
        val result = participantsOfMatch(
            matchId = matchId,
            membership = membership,
            snapshot = snapshot,
            localRsvps = mapOf(matchId to Rsvp(RsvpStatus.IN)),
            ownName = "Max",
            ownColor = 1,
        )

        assertEquals(2, result.size)
        assertEquals(setOf("Max", "Anna"), result.map { it.name }.toSet())
        assertEquals(1, result.count { it.isMe })
    }

    @Test
    fun `die lokale Antwort schlaegt den Stand aus der Datenbank`() {
        // Gerade auf "nicht dabei" gewechselt, noch nicht hochgeschoben:
        // die Anzeige muss sofort die neue Antwort zeigen.
        val result = participantsOfMatch(
            matchId = matchId,
            membership = membership,
            snapshot = snapshot,
            localRsvps = mapOf(matchId to Rsvp(RsvpStatus.OUT, "keine Zeit")),
            ownName = "Max",
            ownColor = 1,
        )

        val me = result.single { it.isMe }
        assertEquals(RsvpStatus.OUT, me.status)
        assertEquals("keine Zeit", me.comment)
        // Der Name kommt weiter aus der Gruppe, nicht aus dem lokalen Profil.
        assertEquals("Max", me.name)
    }

    @Test
    fun `eine zurueckgenommene eigene Antwort verschwindet`() {
        val result = participantsOfMatch(
            matchId = matchId,
            membership = membership,
            snapshot = snapshot,
            localRsvps = emptyMap(),
            ownName = "Max",
            ownColor = 1,
        )

        assertNull(result.firstOrNull { it.isMe })
        assertEquals(listOf("Anna"), result.map { it.name })
    }

    @Test
    fun `Antworten anderer Spiele werden nicht vermischt`() {
        val result = participantsOfMatch(
            matchId = "dfb#anderes-spiel",
            membership = membership,
            snapshot = snapshot,
            localRsvps = emptyMap(),
            ownName = "Max",
            ownColor = 1,
        )

        assertEquals(emptyList(), result)
    }
}
