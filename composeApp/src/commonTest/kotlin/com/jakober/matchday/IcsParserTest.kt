package com.jakober.matchday

import com.jakober.matchday.data.ics.IcsParser
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsParserTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `liest Termin in UTC`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spiel-1
            DTSTART:20260815T183000Z
            DTEND:20260815T203000Z
            SUMMARY:FC Bayern München - Borussia Dortmund
            LOCATION:Allianz Arena
            END:VEVENT
            """
        )

        val matches = IcsParser.parse(ics, "abo", berlin)

        assertEquals(1, matches.size)
        val match = matches.first()
        assertEquals(Instant.parse("2026-08-15T18:30:00Z"), match.start)
        assertEquals("FC Bayern München", match.homeTeam)
        assertEquals("Borussia Dortmund", match.awayTeam)
        assertEquals("Allianz Arena", match.location)
        assertEquals("abo#spiel-1", match.id)
    }

    @Test
    fun `rechnet TZID in die richtige Zeit um`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spiel-2
            DTSTART;TZID=Europe/Berlin:20260815T203000
            SUMMARY:Team A - Team B
            END:VEVENT
            """
        )

        val match = IcsParser.parse(ics, "abo", TimeZone.UTC).single()

        // 20:30 Ortszeit im Sommer sind 18:30 UTC.
        assertEquals(Instant.parse("2026-08-15T18:30:00Z"), match.start)
    }

    @Test
    fun `erkennt ganztaegige Termine`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spiel-3
            DTSTART;VALUE=DATE:20260815
            SUMMARY:Auswärtsfahrt
            END:VEVENT
            """
        )

        val match = IcsParser.parse(ics, "abo", berlin).single()

        assertTrue(match.isAllDay)
        assertNull(match.homeTeam)
        assertEquals("Auswärtsfahrt", match.title)
    }

    @Test
    fun `fuegt umgebrochene Zeilen wieder zusammen`() {
        // Nach RFC 5545 wird nach 75 Zeichen umgebrochen; Folgezeilen
        // beginnen mit einem Leerzeichen.
        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:spiel-4")
            appendLine("DTSTART:20260815T183000Z")
            appendLine("SUMMARY:Borussia Mönchengladbach - Eintracht")
            appendLine("  Frankfurt")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }

        val match = IcsParser.parse(ics, "abo", berlin).single()

        assertEquals("Borussia Mönchengladbach", match.homeTeam)
        assertEquals("Eintracht Frankfurt", match.awayTeam)
    }

    @Test
    fun `loest maskierte Zeichen auf`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spiel-5
            DTSTART:20260815T183000Z
            SUMMARY:Team A - Team B
            LOCATION:Arena\, Tor 3
            END:VEVENT
            """
        )

        val match = IcsParser.parse(ics, "abo", berlin).single()

        assertEquals("Arena, Tor 3", match.location)
    }

    @Test
    fun `sortiert nach Anstosszeit`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spaet
            DTSTART:20260820T183000Z
            SUMMARY:Spät
            END:VEVENT
            BEGIN:VEVENT
            UID:frueh
            DTSTART:20260810T183000Z
            SUMMARY:Früh
            END:VEVENT
            """
        )

        val matches = IcsParser.parse(ics, "abo", berlin)

        assertEquals(listOf("Früh", "Spät"), matches.map { it.title })
    }

    @Test
    fun `ueberspringt Termine ohne Startzeit`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:kaputt
            SUMMARY:Ohne Datum
            END:VEVENT
            """
        )

        assertEquals(0, IcsParser.parse(ics, "abo", berlin).size)
    }

    @Test
    fun `liest den Kalendernamen`() {
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:x
            DTSTART:20260815T183000Z
            SUMMARY:A - B
            END:VEVENT
            """
        )

        assertEquals("Spielplan FC Bayern", IcsParser.calendarName(ics))
    }

    /** Rahmt Ereignisse in einen gueltigen Kalender ein. */
    private fun calendar(body: String): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("X-WR-CALNAME:Spielplan FC Bayern")
        body.trimIndent().lines().forEach { appendLine(it) }
        appendLine("END:VCALENDAR")
    }
}
