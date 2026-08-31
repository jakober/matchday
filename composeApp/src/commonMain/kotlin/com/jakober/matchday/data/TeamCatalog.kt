package com.jakober.matchday.data

import com.jakober.matchday.domain.Subscription

/**
 * Eine fest hinterlegte Mannschaft samt Kalenderquelle.
 *
 * Die Id ist bewusst ein fester Text und keine Zufalls-Id: Aus ihr wird die
 * Spiel-Id gebildet, und die muss auf allen Geraeten gleich sein, damit sich
 * Zusagen ueberhaupt zuordnen lassen.
 */
data class TeamFeed(
    val id: String,
    val name: String,
    val shortName: String,
    val url: String,
    val colorArgb: Long,
    /** Wappen als PNG. Fehlt es, zeichnet [com.jakober.matchday.ui.components.TeamBadge] ein Ersatzabzeichen. */
    val logoUrl: String? = null,
)

/**
 * Die App deckt bewusst nur zwei Mannschaften ab. Beide Kalender stammen von
 * calovo, das die offiziellen Spielplaene als ICS veroeffentlicht.
 *
 * Zur Adresse: Der Zahlenblock am Ende ist bei calovo eine Abo-Kennung. Der
 * Dienst wertet sie nicht aus - der Platzhalter aus dem Seitenquelltext
 * liefert denselben Kalender. Sollte calovo das eines Tages pruefen, muessen
 * hier echte Kennungen hinterlegt werden.
 */
object TeamCatalog {

    val FC_BAYERN = TeamFeed(
        id = "fcbayern",
        name = "FC Bayern München",
        shortName = "Bayern",
        url = "https://i.cal.to/ical/2/fcbayern/spielplan/12345.12345-54321.ics",
        colorArgb = 0xFFDC052D,
        logoUrl = "https://crests.football-data.org/5.png",
    )

    val NATIONALMANNSCHAFT = TeamFeed(
        id = "dfb",
        name = "Nationalmannschaft",
        shortName = "DFB",
        url = "https://i.cal.to/ical/23/dfb/nationalmannschaft/12345.12345-54321.ics",
        colorArgb = 0xFF2B2B2B,
        // Fuer die Nationalmannschaft liefert der Wappendienst nur eine leere
        // Datei, deshalb das gezeichnete Flaggenabzeichen.
        logoUrl = null,
    )

    val ALL = listOf(FC_BAYERN, NATIONALMANNSCHAFT)

    fun byId(id: String): TeamFeed? = ALL.firstOrNull { it.id == id }

    /** Ausgangszustand beim ersten Start: beide Mannschaften aktiv. */
    fun defaultSubscriptions(): List<Subscription> = ALL.map { team ->
        Subscription(
            id = team.id,
            name = team.name,
            url = team.url,
            colorArgb = team.colorArgb,
            enabled = true,
        )
    }
}
