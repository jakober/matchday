package com.jakober.matchday.data.ics

import com.jakober.matchday.domain.Match
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/**
 * Genuegsamer iCalendar-Leser (RFC 5545), beschraenkt auf das, was Spielplaene
 * tatsaechlich enthalten: VEVENT mit Start, Ende, Titel und Ort.
 *
 * Bewusst selbst geschrieben statt per Bibliothek - die verbreiteten
 * ICS-Parser sind JVM-only und waeren auf iOS nicht verwendbar.
 *
 * Nicht unterstuetzt: RRULE. Wiederkehrende Termine erscheinen nur mit ihrem
 * ersten Vorkommen. Fuer Spielplaene ist das unerheblich, jedes Spiel ist ein
 * eigener Eintrag.
 */
object IcsParser {

    fun parse(ics: String, subscriptionId: String, fallbackZone: TimeZone): List<Match> {
        val events = mutableListOf<Match>()
        var current: MutableMap<String, Property>? = null

        for (line in unfold(ics)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableMapOf()

                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { fields -> toMatch(fields, subscriptionId, fallbackZone)?.let(events::add) }
                    current = null
                }

                current != null -> {
                    val property = parseLine(line)
                    // Bei doppelten Feldern gewinnt das erste - manche Feeds
                    // haengen uebersetzte Zweitfassungen hinten an.
                    if (property != null && !current.containsKey(property.name)) {
                        current[property.name] = property
                    }
                }
            }
        }
        return events.sortedBy { it.start }
    }

    /**
     * Name des Kalenders, sofern der Feed einen mitliefert. Die meisten tun
     * das ueber X-WR-CALNAME, manche nur ueber NAME.
     */
    fun calendarName(ics: String): String? {
        for (line in unfold(ics)) {
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) break
            val property = parseLine(line) ?: continue
            if (property.name == "X-WR-CALNAME" || property.name == "NAME") {
                val value = unescape(property.value).trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    /**
     * Faltet fortgesetzte Zeilen zusammen. Im ICS-Format wird nach 75 Zeichen
     * umgebrochen; Folgezeilen beginnen mit Leerzeichen oder Tab und gehoeren
     * ohne Trennzeichen an die vorherige Zeile.
     */
    private fun unfold(ics: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in ics.split("\r\n", "\n", "\r")) {
            if (raw.isEmpty()) continue
            if ((raw[0] == ' ' || raw[0] == '\t') && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    private data class Property(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    )

    /** Zerlegt `DTSTART;TZID=Europe/Berlin:20260815T203000`. */
    private fun parseLine(line: String): Property? {
        // Der Doppelpunkt, der Wert und Name trennt, darf nicht in
        // Anfuehrungszeichen stehen (etwa bei TZID="GMT+1").
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) {
                colon = i
                break
            }
        }
        if (colon <= 0) return null

        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(';')
        val name = parts[0].uppercase()

        val params = buildMap {
            for (p in parts.drop(1)) {
                val eq = p.indexOf('=')
                if (eq > 0) {
                    put(p.substring(0, eq).uppercase(), p.substring(eq + 1).trim('"'))
                }
            }
        }
        return Property(name, params, value)
    }

    private fun toMatch(
        fields: Map<String, Property>,
        subscriptionId: String,
        fallbackZone: TimeZone,
    ): Match? {
        val dtStart = fields["DTSTART"] ?: return null
        val start = parseDateTime(dtStart, fallbackZone) ?: return null
        val end = fields["DTEND"]?.let { parseDateTime(it, fallbackZone) }

        val summary = fields["SUMMARY"]?.value?.let(::unescape)?.trim().orEmpty()
        if (summary.isEmpty()) return null

        // Fehlt die UID, bilden wir eine aus Start und Titel. Damit bleibt die
        // Id ueber Sync-Laeufe stabil und Zusagen gehen nicht verloren.
        val uid = fields["UID"]?.value?.trim()
            ?: "${start.toEpochMilliseconds()}-${summary.hashCode()}"

        // Die Ligakalender von calovo haengen Wettbewerb und Spieltag an den
        // Titel: "FC Bayern München - Hamburger SV | Bundesliga | 3. Spieltag".
        // Ohne diese Trennung hiesse die Gastmannschaft "Hamburger SV |
        // Bundesliga | 3. Spieltag" - kein Wappen, unsinniges Kuerzel.
        val segments = summary.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
        // fixtur.es haengt Ergebnis und Wettbewerbskuerzel an: "Arsenal -
        // Chelsea [CL] (2-1)". Beides gehoert nicht zum Mannschaftsnamen.
        val cleaned = stripScoreAndTag(segments.firstOrNull() ?: summary)
        val title = cleaned.first
        val suffix = (segments.drop(1) + listOfNotNull(cleaned.second))
            .joinToString(" · ").ifEmpty { null }

        val teams = splitTeams(title)

        return Match(
            id = "$subscriptionId#$uid",
            subscriptionId = subscriptionId,
            start = start,
            end = end,
            isAllDay = dtStart.params["VALUE"].equals("DATE", ignoreCase = true),
            title = title,
            homeTeam = teams.first,
            awayTeam = teams.second,
            location = fields["LOCATION"]?.value?.let(::unescape)?.trim()?.ifEmpty { null },
            competition = fields["CATEGORIES"]?.value?.let(::unescape)?.trim()?.ifEmpty { null }
                ?: competitionFromDescription(fields["DESCRIPTION"]?.value?.let(::unescape))
                ?: suffix,
        )
    }

    /**
     * Deckt die drei in freier Wildbahn vorkommenden Formen ab:
     * `20260815T183000Z` (UTC), `20260815T203000` mit TZID-Parameter
     * und `20260815` als ganztaegiger Termin.
     */
    private fun parseDateTime(property: Property, fallbackZone: TimeZone): Instant? {
        val v = property.value.trim()

        // Reines Datum, ganztaegig.
        if (v.length == 8 && !v.contains('T')) {
            val date = runCatching {
                LocalDate(
                    v.substring(0, 4).toInt(),
                    v.substring(4, 6).toInt(),
                    v.substring(6, 8).toInt(),
                )
            }.getOrNull() ?: return null
            return date.atStartOfDayIn(zoneOf(property, fallbackZone))
        }

        if (v.length < 15 || v[8] != 'T') return null
        val local = runCatching {
            LocalDateTime(
                year = v.substring(0, 4).toInt(),
                monthNumber = v.substring(4, 6).toInt(),
                dayOfMonth = v.substring(6, 8).toInt(),
                hour = v.substring(9, 11).toInt(),
                minute = v.substring(11, 13).toInt(),
                second = v.substring(13, 15).toInt(),
            )
        }.getOrNull() ?: return null

        // Endendes Z heisst UTC und schlaegt einen etwaigen TZID-Parameter.
        val zone = if (v.endsWith("Z")) TimeZone.UTC else zoneOf(property, fallbackZone)
        return local.toInstant(zone)
    }

    /**
     * Nicht jeder Feed fuellt CATEGORIES. Die Spielplaene von calovo schreiben
     * den Wettbewerb stattdessen als erste Zeile der Beschreibung, in der Form
     * "Wettbewerb: Bundesliga 3. Spieltag".
     */
    private fun competitionFromDescription(description: String?): String? {
        if (description == null) return null
        for (line in description.split('\n')) {
            val trimmed = line.trim()
            for (prefix in listOf("Wettbewerb:", "Competition:")) {
                if (trimmed.startsWith(prefix, ignoreCase = true)) {
                    // substring statt removePrefix - der Vergleich ignoriert
                    // Gross- und Kleinschreibung, removePrefix nicht.
                    return trimmed.substring(prefix.length).trim().ifEmpty { null }
                }
            }
        }
        return null
    }

    /** Unbekannte Zeitzonen-Kennungen fallen auf die Geraetezone zurueck. */
    private fun zoneOf(property: Property, fallback: TimeZone): TimeZone {
        val tzid = property.params["TZID"] ?: return fallback
        return runCatching { TimeZone.of(tzid) }.getOrDefault(fallback)
    }

    /** Hebt die TEXT-Maskierung nach RFC 5545 auf. */
    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                if (next == 'n' || next == 'N') sb.append('\n') else sb.append(next)
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private val SCORE = Regex("""\s*\(\d+\s*[-:]\s*\d+(?:\s*[a-zA-Z.]+)?\)\s*$""")
    private val TAG = Regex("""\s*\[([A-Za-z0-9 .-]{1,12})\]""")
    private val SPACES = Regex("""\s{2,}""")

    /** Entfernt "(2-1)" am Ende und "[CL]" mittendrin; das Kuerzel kommt als Wettbewerb zurueck. */
    private fun stripScoreAndTag(title: String): Pair<String, String?> {
        var tag: String? = null
        val withoutTag = TAG.replace(title) { m ->
            tag = m.groupValues[1]
            ""
        }
        val withoutScore = SCORE.replace(withoutTag, "")
        return withoutScore.replace(SPACES, " ").trim() to tag
    }

    /**
     * Trennt "Bayern - Dortmund" in Heim und Gast. Die Trennerliste deckt die
     * gaengigen Schreibweisen ab; passt keine, bleibt der Titel ungeteilt.
     *
     * An der Zerlegung haengen die Wappen - sie ist keine Kosmetik mehr.
     * Deshalb fehlen " : " und " v " bewusst: Das eine trifft "Achtung :
     * Spiel verlegt", das andere jedes Wort mit einem v am Rand.
     *
     * Bei " at " und " @ " (US-Schreibweise, "Packers at Bears") steht die
     * Heimmannschaft hinten.
     */
    private fun splitTeams(summary: String): Pair<String?, String?> {
        val separators = listOf(" - ", " – ", " — ", " vs. ", " vs ", " v. ", " gegen ")
        val swapped = listOf(" at ", " @ ")
        for (sep in separators + swapped) {
            val index = summary.indexOf(sep, ignoreCase = true)
            if (index > 0) {
                val before = summary.substring(0, index).trim()
                val after = summary.substring(index + sep.length).trim()
                if (before.isEmpty() || after.isEmpty()) continue
                return if (sep in swapped) after to before else before to after
            }
        }
        return null to null
    }
}
