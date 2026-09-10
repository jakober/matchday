package com.jakober.matchday

import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.notify.ReminderPlanner
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ReminderPlannerTest {

    private val now = Instant.parse("2026-09-01T12:00:00Z")

    private fun match(id: String, inDays: Int) = Match(
        id = id,
        subscriptionId = "fcbayern",
        start = now + inDays.days,
        title = "Bayern - Gegner",
        homeTeam = "Bayern",
        awayTeam = "Gegner",
    )

    @Test
    fun `fragt eine Woche, drei Tage und einen Tag vorher nach`() {
        val plan = ReminderPlanner.plan(
            matches = listOf(match("m1", inDays = 30)),
            rsvps = emptyMap(),
            settings = ReminderSettings(),
            now = now,
        )

        val nags = plan.filter { it.id.startsWith("undecided:") }
        assertEquals(3, nags.size)
        assertEquals(
            listOf(7, 3, 1).map { now + (30 - it).days }.sorted(),
            nags.map { it.at }.sorted(),
        )
    }

    @Test
    fun `keine Nachfrage nach einer Antwort`() {
        val plan = ReminderPlanner.plan(
            matches = listOf(match("m1", inDays = 30)),
            rsvps = mapOf("m1" to Rsvp(RsvpStatus.IN)),
            settings = ReminderSettings(),
            now = now,
        )

        assertTrue(plan.none { it.id.startsWith("undecided:") })
        // Die Erinnerung vor Anpfiff bleibt davon unberuehrt.
        assertEquals(1, plan.count { it.id.startsWith("kickoff:") })
    }

    @Test
    fun `bereits verstrichene Nachfragen entfallen`() {
        // Spiel in drei Tagen: Die Wochen-Nachfrage liegt in der Vergangenheit,
        // die fuer drei Tage vorher faellt auf den jetzigen Zeitpunkt und
        // zaehlt damit auch nicht mehr - es bleibt die vom Vortag.
        val plan = ReminderPlanner.plan(
            matches = listOf(match("m1", inDays = 3)),
            rsvps = emptyMap(),
            settings = ReminderSettings(),
            now = now,
        )

        val nags = plan.filter { it.id.startsWith("undecided:") }
        assertEquals(1, nags.size)
        assertTrue(nags.all { it.at > now })
    }

    @Test
    fun `abgeschaltete Nachfrage erzeugt nichts`() {
        val plan = ReminderPlanner.plan(
            matches = listOf(match("m1", inDays = 30)),
            rsvps = emptyMap(),
            settings = ReminderSettings(undecidedReminderEnabled = false),
            now = now,
        )

        assertTrue(plan.none { it.id.startsWith("undecided:") })
    }

    @Test
    fun `haelt die Obergrenze ein und nimmt die naechstliegenden`() {
        // 40 Spiele erzeugen weit mehr als 60 Vormerkungen.
        val matches = (1..40).map { match("m$it", inDays = it + 8) }

        val plan = ReminderPlanner.plan(
            matches = matches,
            rsvps = emptyMap(),
            settings = ReminderSettings(),
            now = now,
        )

        assertEquals(ReminderPlanner.MAX_PENDING, plan.size)
        // Nach Zeitpunkt sortiert, damit die naechstliegenden gewinnen.
        assertEquals(plan.map { it.at }.sorted(), plan.map { it.at })
    }

    @Test
    fun `Erinnerung vor Anpfiff richtet sich nach der Einstellung`() {
        val plan = ReminderPlanner.plan(
            matches = listOf(match("m1", inDays = 30)),
            rsvps = mapOf("m1" to Rsvp(RsvpStatus.IN)),
            settings = ReminderSettings(minutesBefore = 120),
            now = now,
        )

        val kickoff = plan.single { it.id.startsWith("kickoff:") }
        assertEquals(now + 30.days - 2.hours, kickoff.at)
    }
}
