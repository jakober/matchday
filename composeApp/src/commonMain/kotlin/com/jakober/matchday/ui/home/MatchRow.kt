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
import com.jakober.matchday.data.TeamCatalog
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.MatchMood
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus
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
    status: RsvpStatus,
    participants: List<Participant>,
    isImportant: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateTime = match.start.local()
    val date = dateTime.date
    val team = TeamCatalog.byId(match.subscriptionId)

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

    Box(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
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
        // Spiel es geht.
        TeamBadge(
            team = team,
            size = 38.dp,
            modifier = Modifier.padding(start = 14.dp),
        )

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

    // Eckabzeichen statt Vorsatz vor dem Titel: So bleibt die Begegnung an
    // ihrem Platz, egal ob ein Spiel hervorgehoben ist oder nicht.
    if (isImportant) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Waagerecht auf die Mitte des Wappens gesetzt: Das beginnt
                // bei 14 dp und ist 38 dp breit, seine Mitte liegt also bei
                // 33 dp - abzueglich des halben Abzeichens.
                .padding(start = 20.dp, top = 4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Wichtiges Spiel",
                tint = StatusOpen,
                modifier = Modifier.size(22.dp),
            )
        }
    }
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
@Composable
private fun StatusDot(status: RsvpStatus) {
    val color = statusColor(status)
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        when (status) {
            RsvpStatus.IN -> Box(Modifier.size(14.dp).clip(CircleShape).background(color))
            RsvpStatus.OUT -> Box(
                Modifier.size(14.dp).clip(CircleShape).border(3.dp, color, CircleShape),
            )
            RsvpStatus.UNDECIDED -> Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .border(2.dp, color.copy(alpha = 0.7f), CircleShape),
            )
        }
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
