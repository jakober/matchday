package com.jakober.matchday

import com.jakober.matchday.data.MatchdayStore
import com.jakober.matchday.domain.RsvpStatus
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `legt beim ersten Start beide Mannschaften an`() {
        val store = MatchdayStore(MapSettings())

        val ids = store.subscriptions.value.map { it.id }
        assertTrue("fcbayern" in ids)
        assertTrue("dfb" in ids)
    }
}
