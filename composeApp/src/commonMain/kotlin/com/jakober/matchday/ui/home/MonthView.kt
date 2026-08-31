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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local
import kotlinx.datetime.LocalDate

/**
 * Monatsraster mit Punkten an Spieltagen. Ein Tippen auf einen Tag zeigt die
 * Spiele dieses Tages darunter.
 */
@Composable
fun MonthView(
    matches: List<Match>,
    rsvps: Map<String, RsvpStatus>,
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
    val daysOfMonth = remember(grid, month) { grid.filter { it.monthNumber == month } }
    val selected = selectedDay?.takeIf { it.monthNumber == month && it.year == year }
    val dayMatches = selected?.let { byDay[it] }.orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {

        // -- Kopfzeile mit Monatsnavigation --------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${DateText.monthName(month)} $year",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                if (month == 1) { month = 12; year-- } else month--
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Monat")
            }
            IconButton(onClick = {
                if (month == 12) { month = 1; year++ } else month++
            }) {
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

        Spacer(Modifier.height(4.dp))

        // -- Raster ---------------------------------------------------------
        // Feste 6 Wochen, damit das Layout beim Blaettern nicht springt.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for (week in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (weekday in 0 until 7) {
                        val date = grid[week * 7 + weekday]
                        DayCell(
                            date = date,
                            inCurrentMonth = date.monthNumber == month,
                            isToday = date == today,
                            isSelected = date == selected,
                            dots = byDay[date].orEmpty().map(accentOf),
                            onClick = {
                                selectedDay = if (selectedDay == date) null else date
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // -- Spiele des gewaehlten Tages ------------------------------------
        if (selected != null) {
            Text(
                text = "${DateText.weekdayLong(selected)}, ${DateText.fullDate(selected)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (dayMatches.isEmpty()) {
                Text(
                    text = "Kein Spiel an diesem Tag.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                if (selected != null) dayMatches else emptyList(),
                key = { it.id },
            ) { match ->
                MatchRow(
                    match = match,
                    status = rsvps[match.id] ?: RsvpStatus.UNDECIDED,
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
    isSelected: Boolean,
    dots: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = if (isToday && !isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = inCurrentMonth, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        Spacer(Modifier.height(3.dp))

        // Hoechstens drei Punkte, sonst wird die Zelle unruhig.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (color in dots.take(3)) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (inCurrentMonth) color else color.copy(alpha = 0.3f)),
                )
            }
        }
    }
}
