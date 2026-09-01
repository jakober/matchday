package com.jakober.matchday.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local

/**
 * Chronologische Liste, nach Monaten gruppiert. Die Monatsueberschrift bleibt
 * beim Scrollen oben stehen, damit man in einem langen Spielplan die
 * Orientierung behaelt.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchListView(
    matches: List<Match>,
    rsvps: Map<String, Rsvp>,
    participantsOf: ParticipantsSource,
    importantIds: Set<String>,
    onSelect: (Match) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = matches.groupBy {
        val date = it.start.local().date
        date.year * 100 + date.monthNumber
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((key, group) in grouped) {
            stickyHeader(key = "header-$key") {
                MonthHeader(
                    text = "${DateText.monthName(key % 100)} ${key / 100}",
                )
            }

            items(group, key = { it.id }) { match ->
                MatchRow(
                    match = match,
                    status = rsvps[match.id]?.status ?: RsvpStatus.UNDECIDED,
                    participants = participantsOf(match.id),
                    isImportant = match.id in importantIds,
                    onClick = { onSelect(match) },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // Deckender Hintergrund, sonst scheinen die Karten beim Scrollen
            // unter der Ueberschrift durch.
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp, bottom = 8.dp),
    )
}
