package com.jakober.matchday

import com.jakober.matchday.data.FeedCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedCatalogTest {

    @Test
    fun `findet Vereine unabhaengig von Umlauten und Schreibweise`() {
        assertTrue(FeedCatalog.search("koln").any { it.name == "1. FC Köln" })
        assertTrue(FeedCatalog.search("Köln").any { it.name == "1. FC Köln" })
        assertTrue(FeedCatalog.search("BAYERN").any { it.name == "FC Bayern München" })
        assertTrue(FeedCatalog.search("gladbach").any { it.name == "Borussia Mönchengladbach" })
    }

    @Test
    fun `findet ueber die Liga`() {
        assertTrue(FeedCatalog.search("2. bundesliga").size > 10)
        assertTrue(FeedCatalog.search("dfb").any { it.name == "Deutsche Nationalmannschaft" })
    }

    @Test
    fun `leere Suche liefert nichts, unbekanntes auch nicht`() {
        assertEquals(0, FeedCatalog.search("  ").size)
        assertEquals(0, FeedCatalog.search("xyzzy").size)
    }

    @Test
    fun `jede Adresse ist eindeutig und zeigt auf calovo`() {
        val urls = FeedCatalog.ALL.map { it.url }
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(urls.all { it.startsWith("https://i.cal.to/ical/") })
    }
}
