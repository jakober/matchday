package com.jakober.matchday.data

import com.jakober.matchday.data.remote.GroupMembership
import com.jakober.matchday.data.remote.GroupSnapshot
import com.jakober.matchday.data.remote.MemberDto
import com.jakober.matchday.data.remote.RsvpDto
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.i18n.En
import com.jakober.matchday.i18n.S
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Erfundene, aber glaubwuerdige Daten fuer Store-Screenshots.
 *
 * Nur ueber den Demo-Modus erreichbar (Startparameter), nie im normalen
 * Betrieb. Bewusst ohne Wappen: Vereinslogos in Store-Bildern sind
 * Markenzeichen, und Apple wie Google lehnen Eintraege deswegen ab. Die
 * Mannschaftsnamen selbst sind Tatsachen und erlaubt.
 */
object DemoData {
    const val GROUP_ID = "demo-group"
    const val ME = "m-max"

    val profile = Profile(id = "demo", name = "Max", colorArgb = 0xFF37E27AL)

    val membership = GroupMembership(
        groupId = GROUP_ID,
        memberId = ME,
        inviteCode = "DEMO12",
        groupName = "Stammtisch",
        isAdmin = true,
    )

    val subscriptions = listOf(
        Subscription(id = "cal-bl", name = "Bundesliga", url = "https://example.org/bl.ics", colorArgb = 0xFFDC052DL),
        Subscription(id = "cal-pl", name = "Premier League", url = "https://example.org/pl.ics", colorArgb = 0xFF3FA9F5L),
    )

    private val members = listOf(
        MemberDto(id = ME, groupId = GROUP_ID, displayName = "Max", color = 0xFF37E27AL),
        MemberDto(id = "m-anna", groupId = GROUP_ID, displayName = "Anna", color = 0xFFB06BFFL),
        MemberDto(id = "m-ben", groupId = GROUP_ID, displayName = "Ben", color = 0xFFFFA23EL),
        MemberDto(id = "m-lisa", groupId = GROUP_ID, displayName = "Lisa", color = 0xFFEE5D9CL),
    )

    /** Spiele der naechsten Wochen, an Samstagen und Sonntagen ab heute. */
    fun matches(): List<Match> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val fixtures = listOf(
            Triple("cal-bl", "FC Bayern München", "Borussia Dortmund"),
            Triple("cal-pl", "Arsenal", "Liverpool"),
            Triple("cal-bl", "RB Leipzig", "FC Bayern München"),
            Triple("cal-bl", "Eintracht Frankfurt", "Bayer 04 Leverkusen"),
            Triple("cal-pl", "Manchester City", "Chelsea"),
            Triple("cal-bl", "FC Bayern München", "VfB Stuttgart"),
            Triple("cal-pl", "Manchester United", "Tottenham Hotspur"),
            Triple("cal-bl", "Borussia Mönchengladbach", "1. FC Köln"),
            Triple("cal-bl", "Hamburger SV", "FC Bayern München"),
            Triple("cal-pl", "Newcastle United", "Aston Villa"),
        )
        var day = today.plus(2, DateTimeUnit.DAY)
        return fixtures.mapIndexed { i, (cal, home, away) ->
            // Abwechselnd Samstag 15:30 und Sonntag 17:30, alle drei bis vier Tage
            // ein Termin, damit Liste und Monatsraster gut gefuellt sind.
            val time = if (i % 2 == 0) LocalTime(15, 30) else LocalTime(17, 30)
            val start = day.atTime(time).toInstant(zone)
            day = day.plus(if (i % 2 == 0) 1 else 5, DateTimeUnit.DAY)
            val matchday = 5 + i / 2
            Match(
                id = "$cal#demo-$i",
                subscriptionId = cal,
                start = start,
                title = "$home - $away",
                homeTeam = home,
                awayTeam = away,
                competition = when {
                    cal == "cal-bl" && S === En -> "Bundesliga · Matchday $matchday"
                    cal == "cal-bl" -> "Bundesliga · $matchday. Spieltag"
                    else -> "Premier League · Matchday $matchday"
                },
            )
        }
    }

    /** Eigene Antworten - Zusage zu den ersten beiden, Absage zum vierten. */
    val ownRsvps = mapOf(
        "cal-bl#demo-0" to RsvpStatus.IN,
        "cal-pl#demo-1" to RsvpStatus.IN,
        "cal-bl#demo-3" to RsvpStatus.OUT,
    )

    fun snapshot(): GroupSnapshot {
        val rsvps = listOf(
            RsvpDto(GROUP_ID, "m-anna", "cal-bl", "demo-0", "IN"),
            RsvpDto(GROUP_ID, "m-ben", "cal-bl", "demo-0", "IN"),
            RsvpDto(GROUP_ID, "m-lisa", "cal-bl", "demo-0", "OUT", comment = "Bin im Urlaub"),
            RsvpDto(GROUP_ID, "m-anna", "cal-pl", "demo-1", "IN"),
            RsvpDto(GROUP_ID, "m-ben", "cal-bl", "demo-2", "IN"),
            RsvpDto(GROUP_ID, "m-lisa", "cal-bl", "demo-2", "IN"),
            RsvpDto(GROUP_ID, "m-anna", "cal-bl", "demo-3", "OUT"),
            RsvpDto(GROUP_ID, "m-ben", "cal-pl", "demo-4", "IN"),
            RsvpDto(GROUP_ID, "m-anna", "cal-bl", "demo-5", "IN"),
            RsvpDto(GROUP_ID, "m-lisa", "cal-bl", "demo-5", "IN"),
        )
        return GroupSnapshot(
            members = members,
            rsvps = rsvps,
            importantMatchIds = setOf("cal-bl#demo-0", "cal-bl#demo-5"),
        )
    }
}
