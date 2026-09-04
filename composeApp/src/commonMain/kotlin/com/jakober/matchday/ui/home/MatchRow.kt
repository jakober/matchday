package com.jakober.matchday.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.MatchMood
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.domain.moodOf
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.StatusIn
import com.jakober.matchday.theme.StatusOpen
import com.jakober.matchday.theme.StatusOut
import com.jakober.matchday.ui.components.AttendanceLine
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.TeamBadge
import com.jakober.matchday.ui.components.local

/** Farbe und Kurztext zu einem Zusagestatus. */
fun statusColor(status: RsvpStatus): Color = when (status) {
    RsvpStatus.IN -> StatusIn
    RsvpStatus.OUT -> StatusOut
    RsvpStatus.UNDECIDED -> StatusOpen
}

fun statusLabel(status: RsvpStatus): String = when (status) {
    RsvpStatus.IN -> "Dabei"
    RsvpStatus.OUT -> "Nicht dabei"
    RsvpStatus.UNDECIDED -> "Offen"
}

/**
 * Eine Zeile in der Spielliste: Wappen, Datum, Begegnung, wer mitkommt und
 * die eigene Antwort.
 */
@Composable
fun MatchRow(
    match: Match,
    /** Der Kalender, aus dem das Spiel stammt - fuer das Abzeichen. */
    subscription: Subscription?,
    status: RsvpStatus,
    participants: List<Participant>,
    isImportant: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateTime = match.start.local()
    val date = dateTime.date

    // Umrandung zeigt das Gesamtbild: gruen ab zwei Zusagen, rot bei einer
    // Absage. Kraeftiger als der normale Rahmen, damit man es beim
    // Durchscrollen sieht.
    val mood = moodOf(participants)
    val borderColor = when (mood) {
        MatchMood.ENOUGH_IN -> StatusIn
        MatchMood.DECLINED -> StatusOut
        MatchMood.OPEN -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (mood == MatchMood.OPEN) 1.dp else 2.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(CardCorner),
            )
            .clickable(onClick = onClick)
            .padding(top = 14.dp, bottom = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Wappen statt Farbbalken - auf einen Blick erkennbar, um wessen
        // Spiel es geht. Bei hervorgehobenen Spielen steht der Stern darueber
        // und schiebt das Wappen nach unten; die Begegnung bleibt davon
        // unberuehrt, weil beides in derselben Spalte liegt.
        Column(
            modifier = Modifier.padding(start = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isImportant) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Wichtiges Spiel",
                    tint = StatusOpen,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            TeamBadge(subscription = subscription, size = 38.dp)
        }

        Column(
            modifier = Modifier
                .width(58.dp)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = DateText.weekdayShort(date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitleOf(match),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            AttendanceLine(participants = participants)
        }

        Spacer(Modifier.width(8.dp))

        StatusDot(status)
    }
}

/** Uhrzeit, dahinter Wettbewerb oder Ort, sofern vorhanden. */
private fun subtitleOf(match: Match): String {
    val time = if (match.isAllDay) "Ganztägig" else DateText.time(match.start.local())
    val extra = match.competition?.takeIf { it.isNotBlank() }
        ?: match.location?.takeIf { it.isNotBlank() }
    return if (extra == null) time else "$time · $extra"
}

/**
 * Eigene Antwort. Bewusst mit Fuellung und Ring statt nur Farbe, damit sich
 * die drei Zustaende auch ohne Farbunterscheidung lesen lassen.
 */
/**
 * Eigene Antwort, gross und mit Symbol. Vorher war es ein Punkt von vierzehn
 * Punkten Groesse - fuer die zentrale Angabe der App zu wenig. Haken, Kreuz
 * und Fragezeichen unterscheiden sich zudem auch ohne Farbe.
 */
@Composable
private fun StatusDot(status: RsvpStatus) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (status) {
                RsvpStatus.IN -> Icons.Filled.Check
                RsvpStatus.OUT -> Icons.Filled.Close
                RsvpStatus.UNDECIDED -> Icons.Filled.QuestionMark
            },
            contentDescription = statusLabel(status),
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Kleines Etikett mit Farbe und Text, fuer die Detailansicht. */
@Composable
fun StatusPill(status: RsvpStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
