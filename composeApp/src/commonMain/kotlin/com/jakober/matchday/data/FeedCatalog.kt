package com.jakober.matchday.data

/**
 * Eingebaute Kalenderadressen fuer die Suche beim Hinzufuegen.
 *
 * Reine Vorschlaege: Die Identitaet eines Abos kommt weiterhin vom Server
 * (calendars.id), hier stehen nur Name und Adresse zum Vorbelegen. Alle
 * Adressen stammen von calovo und wurden beim Erstellen einzeln abgerufen;
 * der Zahlenblock am Ende ist ein Platzhalter, den der Dienst nicht prueft.
 *
 * Stand: September 2026. Auf- und Absteiger muessen hier nachgezogen werden.
 */
data class CatalogFeed(val name: String, val league: String, val url: String)

object FeedCatalog {

    val ALL: List<CatalogFeed> = listOf(
        CatalogFeed("Bundesliga - alle Spiele", "1. Bundesliga", "https://i.cal.to/ical/2699/bundesliga/bundesliga-gesamtspielplan/12345.12345-54321.ics"),
        CatalogFeed("FC Augsburg", "1. Bundesliga", "https://i.cal.to/ical/2712/bundesliga/fc-augsburg/12345.12345-54321.ics"),
        CatalogFeed("1. FC Union Berlin", "1. Bundesliga", "https://i.cal.to/ical/2726/bundesliga/1-fc-union-berlin/12345.12345-54321.ics"),
        CatalogFeed("SV Werder Bremen", "1. Bundesliga", "https://i.cal.to/ical/2711/bundesliga/sv-werder-bremen/12345.12345-54321.ics"),
        CatalogFeed("Borussia Dortmund", "1. Bundesliga", "https://i.cal.to/ical/2704/bundesliga/borussia-dortmund/12345.12345-54321.ics"),
        CatalogFeed("SV Elversberg", "1. Bundesliga", "https://i.cal.to/ical/7023/bundesliga/sv-elversberg/12345.12345-54321.ics"),
        CatalogFeed("Eintracht Frankfurt", "1. Bundesliga", "https://i.cal.to/ical/2708/bundesliga/eintracht-frankfurt/12345.12345-54321.ics"),
        CatalogFeed("Sport-Club Freiburg", "1. Bundesliga", "https://i.cal.to/ical/2715/bundesliga/sport-club-freiburg/12345.12345-54321.ics"),
        CatalogFeed("Hamburger SV", "1. Bundesliga", "https://i.cal.to/ical/2719/bundesliga/hamburger-sv/12345.12345-54321.ics"),
        CatalogFeed("TSG Hoffenheim", "1. Bundesliga", "https://i.cal.to/ical/2703/bundesliga/tsg-1899-hoffenheim/12345.12345-54321.ics"),
        CatalogFeed("1. FC Köln", "1. Bundesliga", "https://i.cal.to/ical/2720/bundesliga/1-fc-koeln/12345.12345-54321.ics"),
        CatalogFeed("RB Leipzig", "1. Bundesliga", "https://i.cal.to/ical/2706/bundesliga/rb-leipzig/12345.12345-54321.ics"),
        CatalogFeed("Bayer 04 Leverkusen", "1. Bundesliga", "https://i.cal.to/ical/2705/bundesliga/bayer-04-leverkusen/12345.12345-54321.ics"),
        CatalogFeed("1. FSV Mainz 05", "1. Bundesliga", "https://i.cal.to/ical/2714/bundesliga/1-fsv-mainz-05/12345.12345-54321.ics"),
        CatalogFeed("Borussia Mönchengladbach", "1. Bundesliga", "https://i.cal.to/ical/2709/bundesliga/borussia-moenchengladbach/12345.12345-54321.ics"),
        CatalogFeed("FC Bayern München", "1. Bundesliga", "https://i.cal.to/ical/2701/bundesliga/fc-bayern-muenchen/12345.12345-54321.ics"),
        CatalogFeed("SC Paderborn 07", "1. Bundesliga", "https://i.cal.to/ical/2736/bundesliga/sc-paderborn-07/12345.12345-54321.ics"),
        CatalogFeed("FC Schalke 04", "1. Bundesliga", "https://i.cal.to/ical/2702/bundesliga/fc-schalke-04/12345.12345-54321.ics"),
        CatalogFeed("VfB Stuttgart", "1. Bundesliga", "https://i.cal.to/ical/2707/bundesliga/vfb-stuttgart/12345.12345-54321.ics"),
        CatalogFeed("2. Bundesliga - alle Spiele", "2. Bundesliga", "https://i.cal.to/ical/2700/bundesliga/2-bundesliga-gesamtspielplan/12345.12345-54321.ics"),
        CatalogFeed("Hertha BSC", "2. Bundesliga", "https://i.cal.to/ical/2710/bundesliga/hertha-bsc/12345.12345-54321.ics"),
        CatalogFeed("DSC Arminia Bielefeld", "2. Bundesliga", "https://i.cal.to/ical/2722/bundesliga/dsc-arminia-bielefeld/12345.12345-54321.ics"),
        CatalogFeed("VfL Bochum 1848", "2. Bundesliga", "https://i.cal.to/ical/2724/bundesliga/vfl-bochum-1848/12345.12345-54321.ics"),
        CatalogFeed("Eintracht Braunschweig", "2. Bundesliga", "https://i.cal.to/ical/5840/bundesliga/eintracht-braunschweig/12345.12345-54321.ics"),
        CatalogFeed("FC Energie Cottbus", "2. Bundesliga", "https://i.cal.to/ical/8688/bundesliga/fc-energie-cottbus/12345.12345-54321.ics"),
        CatalogFeed("SV Darmstadt 98", "2. Bundesliga", "https://i.cal.to/ical/2728/bundesliga/sv-darmstadt-98/12345.12345-54321.ics"),
        CatalogFeed("SG Dynamo Dresden", "2. Bundesliga", "https://i.cal.to/ical/2732/bundesliga/sg-dynamo-dresden/12345.12345-54321.ics"),
        CatalogFeed("SpVgg Greuther Fürth", "2. Bundesliga", "https://i.cal.to/ical/2733/bundesliga/spvgg-greuther-fuerth/12345.12345-54321.ics"),
        CatalogFeed("Hannover 96", "2. Bundesliga", "https://i.cal.to/ical/2713/bundesliga/hannover-96/12345.12345-54321.ics"),
        CatalogFeed("1. FC Heidenheim 1846", "2. Bundesliga", "https://i.cal.to/ical/2731/bundesliga/1-fc-heidenheim-1846/12345.12345-54321.ics"),
        CatalogFeed("1. FC Kaiserslautern", "2. Bundesliga", "https://i.cal.to/ical/6592/bundesliga/1-fc-kaiserslautern/12345.12345-54321.ics"),
        CatalogFeed("Karlsruher SC", "2. Bundesliga", "https://i.cal.to/ical/4144/bundesliga/karlsruher-sc/12345.12345-54321.ics"),
        CatalogFeed("Holstein Kiel", "2. Bundesliga", "https://i.cal.to/ical/2721/bundesliga/holstein-kiel/12345.12345-54321.ics"),
        CatalogFeed("1. FC Magdeburg", "2. Bundesliga", "https://i.cal.to/ical/2735/bundesliga/1-fc-magdeburg/12345.12345-54321.ics"),
        CatalogFeed("1. FC Nürnberg", "2. Bundesliga", "https://i.cal.to/ical/2718/bundesliga/1-fc-nuernberg/12345.12345-54321.ics"),
        CatalogFeed("VfL Osnabrück", "2. Bundesliga", "https://i.cal.to/ical/4142/bundesliga/vfl-osnabrueck/12345.12345-54321.ics"),
        CatalogFeed("FC St. Pauli", "2. Bundesliga", "https://i.cal.to/ical/2730/bundesliga/fc-st-pauli/12345.12345-54321.ics"),
        CatalogFeed("VfL Wolfsburg", "2. Bundesliga", "https://i.cal.to/ical/2716/bundesliga/vfl-wolfsburg/12345.12345-54321.ics"),
        CatalogFeed("Deutsche Nationalmannschaft", "DFB", "https://i.cal.to/ical/23/dfb/nationalmannschaft/12345.12345-54321.ics"),
    )

    /**
     * Sucht in Name und Liga, unempfindlich gegen Gross-/Kleinschreibung und
     * Umlaute: "koln" findet "1. FC Koeln".
     */
    fun search(query: String): List<CatalogFeed> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return ALL.filter { q in fold(it.name) || q in fold(it.league) }
    }

    private fun fold(s: String): String = s.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace("ae", "a").replace("oe", "o").replace("ue", "u")
        .replace(Regex("[^a-z0-9]"), "")
}
