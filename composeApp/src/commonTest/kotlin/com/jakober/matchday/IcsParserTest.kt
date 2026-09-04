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
    fun `liest den Wettbewerb aus der Beschreibung`() {
        // Die calovo-Spielplaene fuellen CATEGORIES nicht, sondern schreiben
        // den Wettbewerb in die erste Zeile der Beschreibung.
        val ics = calendar(
            """
            BEGIN:VEVENT
            UID:spiel-6
            DTSTART;TZID=Europe/Berlin:20250913T183000
            SUMMARY:FC Bayern München - Hamburger SV
            LOCATION:Allianz Arena\, München
            DESCRIPTION:Wettbewerb: Bundesliga 3. Spieltag\n\n FC Bayern live im TV: Sky
            END:VEVENT
            """
        )

        val match = IcsParser.parse(ics, "fcbayern", berlin).single()

        assertEquals("Bundesliga 3. Spieltag", match.competition)
        assertEquals("Allianz Arena, München", match.location)
        assertEquals("FC Bayern München", match.homeTeam)
        assertEquals("Hamburger SV", match.awayTeam)
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

    private fun icsWith(summary: String) = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:x1
        DTSTART:20261003T133000Z
        SUMMARY:$summary
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `US-Schreibweise stellt die Heimmannschaft nach hinten`() {
        val match = IcsParser.parse(icsWith("Green Bay Packers at Chicago Bears"), "s", berlin).single()
        assertEquals("Chicago Bears", match.homeTeam)
        assertEquals("Green Bay Packers", match.awayTeam)
    }

    @Test
    fun `Doppelpunkt und einzelnes v trennen nicht mehr`() {
        // Beides traf harmlose Titel und machte daraus Begegnungen.
        assertNull(IcsParser.parse(icsWith("Achtung : Spiel verlegt"), "s", berlin).single().homeTeam)
        assertNull(IcsParser.parse(icsWith("Abfahrt v Bus"), "s", berlin).single().homeTeam)
    }

    @Test
    fun `v mit Punkt trennt weiterhin`() {
        val match = IcsParser.parse(icsWith("Arsenal v. Chelsea"), "s", berlin).single()
        assertEquals("Arsenal", match.homeTeam)
        assertEquals("Chelsea", match.awayTeam)
    }

    @Test
    fun `trennt Wettbewerb und Spieltag vom Titel der Ligakalender`() {
        val match = IcsParser.parse(icsWith("FC Bayern München - Hamburger SV | Bundesliga | 3. Spieltag"), "s", berlin).single()
        assertEquals("FC Bayern München", match.homeTeam)
        assertEquals("Hamburger SV", match.awayTeam)
        assertEquals("FC Bayern München - Hamburger SV", match.title)
        assertEquals("Bundesliga · 3. Spieltag", match.competition)
    }
}
