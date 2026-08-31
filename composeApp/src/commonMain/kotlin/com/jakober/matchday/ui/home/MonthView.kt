package com.jakober.matchday.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local
import kotlinx.datetime.LocalDate

/**
 * Monatsraster. Spieltage sind am Ball erkennbar und zusaetzlich hinterlegt.
 *
 * Die Spiele des angetippten Tages erscheinen als Overlay ueber dem Raster.
 * Unterhalb waren sie auf kleineren Geraeten ausserhalb des sichtbaren
 * Bereichs, weil das Raster sechs Wochen hoch ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthView(
    matches: List<Match>,
    rsvps: Map<String, Rsvp>,
    participantsOf: ParticipantsSource,
    accentOf: (Match) -> Color,
    onSelect: (Match) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { DateText.todayDate() }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthNumber) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    // Spiele nach Tag vorsortieren - im Raster wird 42-mal nachgeschlagen.
    val byDay = remember(matches) { matches.groupBy { it.start.local().date } }
    val grid = remember(year, month) { DateText.monthGrid(year, month) }

    Column(modifier = modifier.fillMaxWidth()) {

        // -- Kopfzeile mit Monatsnavigation --------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${DateText.monthName(month)} $year",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Monat")
            }
            IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
            }
        }

        // -- Wochentagsleiste ----------------------------------------------
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for (label in DateText.weekdayHeaders) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // -- Raster, feste 6 Wochen, damit nichts springt --------------------
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for (week in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (weekday in 0 until 7) {
                        val date = grid[week * 7 + weekday]
                        val dayMatches = byDay[date].orEmpty()
                        DayCell(
                            date = date,
                            inCurrentMonth = date.monthNumber == month,
                            isToday = date == today,
                            isPast = date < today,
                            matchCount = dayMatches.size,
                            ballColor = dayMatches.firstOrNull()?.let(accentOf),
                            attending = dayMatches.any { match ->
                                participantsOf(match.id).any { it.status == RsvpStatus.IN }
                            },
                            onClick = {
                                // Tag aus dem Nachbarmonat: erst umblaettern,
                                // damit die Auswahl nicht sofort wieder aus dem
                                // Raster faellt.
                                if (date.monthNumber != month) {
                                    year = date.year
                                    month = date.monthNumber
                                }
                                selectedDay = date
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tippe einen Spieltag an. Tage aus dem Nachbarmonat blättern weiter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    // -- Tagesansicht als Overlay ------------------------------------------
    selectedDay?.let { day ->
        DaySheet(
            date = day,
            matches = byDay[day].orEmpty(),
            rsvps = rsvps,
            participantsOf = participantsOf,
            onSelect = { match ->
                // Erst schliessen, dann die Detailansicht oeffnen - zwei
                // uebereinanderliegende Sheets vertragen sich nicht.
                selectedDay = null
                onSelect(match)
            },
            onDismiss = { selectedDay = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    matches: List<Match>,
    rsvps: Map<String, Rsvp>,
    participantsOf: ParticipantsSource,
    onSelect: (Match) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${DateText.weekdayLong(date)}, ${DateText.fullDate(date)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (matches.isEmpty()) {
                Text(
                    text = "Kein Spiel an diesem Tag.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            for (match in matches) {
                MatchRow(
                    match = match,
                    status = rsvps[match.id]?.status ?: RsvpStatus.UNDECIDED,
                    participants = participantsOf(match.id),
                    onClick = { onSelect(match) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isPast: Boolean,
    matchCount: Int,
    ballColor: Color?,
    attending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMatch = matchCount > 0
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .aspectRatio(0.8f)
            .padding(2.dp)
            .clip(shape)
            // Spieltage zusaetzlich hinterlegen - der Ball allein geht im
            // Raster unter, sobald man das Handy schraeg haelt.
            .background(
                when {
                    !hasMatch -> Color.Transparent
                    // Nachbarmonat und Vergangenheit blasser, aber sichtbar.
                    inCurrentMonth && !isPast -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            )
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = hasMatch, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || hasMatch) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                isToday -> MaterialTheme.colorScheme.primary
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        if (hasMatch) {
            val dimmed = !inCurrentMonth || isPast
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsSoccer,
                    contentDescription = if (matchCount == 1) "Spieltag" else "$matchCount Spiele",
                    tint = (ballColor ?: MaterialTheme.colorScheme.primary)
                        .copy(alpha = if (dimmed) 0.45f else 1f),
                    modifier = Modifier.size(14.dp),
                )
                if (matchCount > 1) {
                    Text(
                        text = matchCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Kleiner Punkt, wenn jemand zugesagt hat - im Raster ist kein
            // Platz fuer Avatare, die stehen in der Tagesansicht.
            if (attending) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
