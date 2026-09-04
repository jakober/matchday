package com.jakober.matchday

import com.jakober.matchday.data.MatchdayStore
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import kotlinx.datetime.Instant
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchdayStoreTest {

    @Test
    fun `liest Zusagen im alten Format ohne Kommentar`() {
        // Bis Version 0.3 stand je Spiel nur der Statusname. Diese Daten
        // liegen auf Geraeten, die schon eine aeltere Fassung hatten.
        val settings = MapSettings("rsvps" to """{"fcbayern#a":"IN","dfb#b":"OUT"}""")

        val store = MatchdayStore(settings)

        assertEquals(RsvpStatus.IN, store.rsvps.value["fcbayern#a"]?.status)
        assertEquals(RsvpStatus.OUT, store.rsvps.value["dfb#b"]?.status)
        assertNull(store.rsvps.value["fcbayern#a"]?.comment)
    }

    @Test
    fun `liest Zusagen im neuen Format mit Kommentar`() {
        val settings = MapSettings(
            "rsvps" to """{"fcbayern#a":{"status":"OUT","comment":"bin im Urlaub"}}"""
        )

        val store = MatchdayStore(settings)

        assertEquals(RsvpStatus.OUT, store.rsvps.value["fcbayern#a"]?.status)
        assertEquals("bin im Urlaub", store.rsvps.value["fcbayern#a"]?.comment)
    }

    @Test
    fun `speichert Absage mit Kommentar und liest sie wieder`() {
        val settings = MapSettings()

        MatchdayStore(settings).setRsvp("m1", RsvpStatus.OUT, "keine Zeit")

        // Zweite Instanz auf denselben Daten - so wie nach einem Neustart.
        val reloaded = MatchdayStore(settings)
        assertEquals(RsvpStatus.OUT, reloaded.rsvps.value["m1"]?.status)
        assertEquals("keine Zeit", reloaded.rsvps.value["m1"]?.comment)
    }

    @Test
    fun `leerer Kommentar wird nicht gespeichert`() {
        val settings = MapSettings()
        val store = MatchdayStore(settings)

        store.setRsvp("m1", RsvpStatus.OUT, "   ")

        assertNull(store.rsvps.value["m1"]?.comment)
    }

    @Test
    fun `Ruecknahme entfernt den Eintrag samt Kommentar`() {
        val settings = MapSettings()
        val store = MatchdayStore(settings)
        store.setRsvp("m1", RsvpStatus.OUT, "keine Zeit")

        store.setRsvp("m1", RsvpStatus.UNDECIDED)

        // Wichtig fuer die Wochen-Erinnerung: Nur ein fehlender Eintrag
        // gilt als unbeantwortet.
        assertNull(store.rsvps.value["m1"])
    }

    @Test
    fun `uebernimmt Kalender der Gruppe und behaelt den lokalen Schalter`() {
        val store = MatchdayStore(MapSettings())
        store.mergeServerSubscriptions(listOf(sub("c1", "Bundesliga")))
        store.setSubscriptionEnabled("c1", false)

        // Der Server nennt den Kalender um; der Schalter darf dabei nicht
        // zurueckspringen.
        store.mergeServerSubscriptions(listOf(sub("c1", "1. Bundesliga"), sub("c2", "DFB")))

        val byId = store.subscriptions.value.associateBy { it.id }
        assertEquals("1. Bundesliga", byId["c1"]?.name)
        assertEquals(false, byId["c1"]?.enabled)
        assertEquals(true, byId["c2"]?.enabled)
    }

    @Test
    fun `entfernt Kalender, die es in der Gruppe nicht mehr gibt, samt Spielen und Zusagen`() {
        val store = MatchdayStore(MapSettings())
        store.mergeServerSubscriptions(listOf(sub("c1", "Bundesliga"), sub("c2", "DFB")))
        store.replaceMatchesOf("c1", listOf(match("c1#a", "c1")))
        store.replaceMatchesOf("c2", listOf(match("c2#b", "c2")))
        store.setRsvp("c1#a", RsvpStatus.IN)
        store.setRsvp("c2#b", RsvpStatus.IN)

        store.mergeServerSubscriptions(listOf(sub("c2", "DFB")))

        assertEquals(listOf("c2"), store.subscriptions.value.map { it.id })
        assertEquals(listOf("c2#b"), store.matches.value.map { it.id })
        assertNull(store.rsvps.value["c1#a"])
        assertEquals(RsvpStatus.IN, store.rsvps.value["c2#b"]?.status)
    }

    private fun sub(id: String, name: String) =
        Subscription(id = id, name = name, url = "https://example.org/$id.ics", colorArgb = 1)

    private fun match(id: String, subscriptionId: String) = Match(
        id = id,
        subscriptionId = subscriptionId,
        start = Instant.parse("2026-10-01T18:30:00Z"),
        title = "Spiel",
    )
}
