package com.jakober.matchday.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.jakober.matchday.data.TeamCatalog
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.MatchMood
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.moodOf
import com.jakober.matchday.theme.StatusIn
import com.jakober.matchday.theme.StatusOpen
import com.jakober.matchday.theme.StatusOut
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Rund zwanzig Jahre in beide Richtungen - mehr braucht kein Spielplan. */
private const val PAGE_COUNT = 480
private const val CENTER_PAGE = PAGE_COUNT / 2

/**
 * Monatsraster. Spieltage sind am Ball erkennbar und zusaetzlich hinterlegt;
 * zwischen den Monaten wird gewischt.
 *
 * Die Spiele des angetippten Tages erscheinen als Overlay ueber dem Raster.
 * Unterhalb waren sie auf kleineren Geraeten ausserhalb des sichtbaren
 * Bereichs, weil das Raster sechs Wochen hoch ist.
 */
@Composable
fun MonthView(
    matches: List<Match>,
    rsvps: Map<String, Rsvp>,
    participantsOf: ParticipantsSource,
    importantIds: Set<String>,
    accentOf: (Match) -> Color,
    onSelect: (Match) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { DateText.todayDate() }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val scope = rememberCoroutineScope()

    // Spiele nach Tag vorsortieren - im Raster wird 42-mal nachgeschlagen.
    val byDay = remember(matches) { matches.groupBy { it.start.local().date } }

    val pagerState = rememberPagerState(initialPage = CENTER_PAGE) { PAGE_COUNT }
    val shown = remember(pagerState.currentPage) { monthOfPage(today, pagerState.currentPage) }

    Column(modifier = modifier.fillMaxSize()) {

        // -- Kopfzeile mit Monatsnavigation --------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${DateText.monthName(shown.monthNumber)} ${shown.year}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Monat")
            }
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
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

        Spacer(Modifier.height(6.dp))

        // -- Raster, wischbar ------------------------------------------------
        // Das Raster nimmt sich den verbleibenden Platz. Keine feste Hoehe:
        // auf kurzen Bildschirmen - aufgeklapptes Foldable, Querformat - waere
        // sonst die untere Zeile abgeschnitten und nicht erreichbar.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val month = monthOfPage(today, page)
            MonthGrid(
                year = month.year,
                month = month.monthNumber,
                today = today,
                byDay = byDay,
                accentOf = accentOf,
                participantsOf = participantsOf,
                importantIds = importantIds,
                onDayClick = { date ->
                    // Tag aus dem Nachbarmonat: erst dorthin blaettern, sonst
                    // faellt die Auswahl sofort wieder aus dem Raster.
                    if (date.monthNumber != month.monthNumber || date.year != month.year) {
                        scope.launch { pagerState.animateScrollToPage(pageOfMonth(today, date)) }
                    }
                    selectedDay = date
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Wische für den nächsten Monat. Tippe einen Spieltag an.",
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
            importantIds = importantIds,
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

/**
 * Gesamtbild eines Tages. Liegen mehrere Spiele an, gewinnt das
 * aussagekraeftigste: gruen vor rot vor offen.
 */
private fun dayMood(matches: List<Match>, participantsOf: ParticipantsSource): MatchMood {
    val moods = matches.map { moodOf(participantsOf(it.id)) }
    return when {
        MatchMood.ENOUGH_IN in moods -> MatchMood.ENOUGH_IN
        MatchMood.DECLINED in moods -> MatchMood.DECLINED
        else -> MatchMood.OPEN
    }
}

/** Monat, den eine Pager-Seite zeigt. */
private fun monthOfPage(today: LocalDate, page: Int): LocalDate {
    val total = today.year * 12 + (today.monthNumber - 1) + (page - CENTER_PAGE)
    return LocalDate(total / 12, total % 12 + 1, 1)
}

/** Umkehrung: die Seite, auf der ein Datum liegt. */
private fun pageOfMonth(today: LocalDate, date: LocalDate): Int {
    val total = date.year * 12 + (date.monthNumber - 1)
    val base = today.year * 12 + (today.monthNumber - 1)
    return CENTER_PAGE + (total - base)
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    today: LocalDate,
    byDay: Map<LocalDate, List<Match>>,
    accentOf: (Match) -> Color,
    participantsOf: ParticipantsSource,
    importantIds: Set<String>,
    onDayClick: (LocalDate) -> Unit,
) {
    val grid = remember(year, month) { DateText.monthGrid(year, month) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        for (week in 0 until 6) {
            // Sechs gleich hohe Wochen - zusammen genau die verfuegbare Hoehe.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (weekday in 0 until 7) {
                    val date = grid[week * 7 + weekday]
                    val dayMatches = byDay[date].orEmpty()
                    DayCell(
                        date = date,
                        inCurrentMonth = date.monthNumber == month,
                        isToday = date == today,
                        isPast = date < today,
                        matchCount = dayMatches.size,
                        // Die groesste Zusagenzahl des Tages - bei zwei
                        // Spielen zaehlt das besser besuchte.
                        attendeeCount = dayMatches.maxOfOrNull { match ->
                            participantsOf(match.id).count { it.status == RsvpStatus.IN }
                        } ?: 0,
                        ballColor = dayMatches.firstOrNull()?.let(accentOf),
                        ballTeamId = dayMatches.firstOrNull()?.subscriptionId,
                        mood = dayMood(dayMatches, participantsOf),
                        isImportant = dayMatches.any { it.id in importantIds },
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    matches: List<Match>,
    rsvps: Map<String, Rsvp>,
    participantsOf: ParticipantsSource,
    importantIds: Set<String>,
    onSelect: (Match) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Einrueckungen uebernimmt der Inhalt selbst, damit imePadding greift.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
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
                    isImportant = match.id in importantIds,
                    onClick = { onSelect(match) },
                )
            }
        }
    }
}

/**
 * Zeichen fuer einen Spieltag: Stern bei hervorgehobenen Spielen, sonst ein
 * Ball. Bei Laenderspielen bekommt der Ball einen Verlauf in Schwarz-Rot-Gold.
 *
 * Der Verlauf entsteht, indem ueber das gezeichnete Symbol ein Rechteck mit
 * dem Farbverlauf gelegt wird, das nur dort wirkt, wo das Symbol Deckung hat.
 * Eine einfache Einfaerbung kann immer nur eine Farbe.
 */
@Composable
private fun MatchMark(
    isImportant: Boolean,
    isNational: Boolean,
    color: Color,
    dimmed: Boolean,
    description: String,
) {
    val alpha = if (dimmed) 0.45f else 1f

    if (isImportant) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = description,
            tint = StatusOpen.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        return
    }

    if (!isNational) {
        Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = description,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
        return
    }

    val flagge = Brush.verticalGradient(
        listOf(Color(0xFF1A1A1A), Color(0xFFDD0000), Color(0xFFFFCE00)),
    )
    Icon(
        imageVector = Icons.Filled.SportsSoccer,
        contentDescription = description,
        tint = Color.Unspecified,
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer {
                this.alpha = alpha
                // Ohne eigene Ebene wirkt der Mischmodus auf den ganzen
                // Hintergrund statt nur auf das Symbol.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = flagge, blendMode = BlendMode.SrcIn)
            },
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isPast: Boolean,
    attendeeCount: Int,
    matchCount: Int,
    ballColor: Color?,
    ballTeamId: String?,
    mood: MatchMood,
    isImportant: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMatch = matchCount > 0
    val dimmed = !inCurrentMonth || isPast
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(shape)
            .background(
                when {
                    !hasMatch -> Color.Transparent
                    // Hervorgehobene Tage bekommen zusaetzlich einen kraeftigeren
                    // Grund, damit die Groesse allein nicht die einzige
                    // Unterscheidung ist.
                    isImportant && !dimmed -> StatusOpen.copy(alpha = 0.18f)
                    // Nachbarmonat und Vergangenheit blasser, aber sichtbar.
                    dimmed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            // Gruen ab zwei Zusagen, rot bei Absage - sonst der heutige Tag.
            // Das Gesamtbild schlaegt die Heute-Markierung; welcher Tag heute
            // ist, sagt ohnehin schon die fette Zahl.
            .border(
                width = when {
                    mood != MatchMood.OPEN -> 2.dp
                    isToday -> 1.5.dp
                    else -> 0.dp
                },
                color = when (mood) {
                    MatchMood.ENOUGH_IN -> StatusIn
                    MatchMood.DECLINED -> StatusOut
                    MatchMood.OPEN ->
                        if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                },
                shape = shape,
            )
            .clickable(enabled = hasMatch, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = if (isImportant) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (isToday || hasMatch) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                isToday -> MaterialTheme.colorScheme.primary
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        if (hasMatch) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MatchMark(
                    isImportant = isImportant,
                    // Laenderspiele bekommen einen Ball in Schwarz-Rot-Gold.
                    isNational = ballTeamId == TeamCatalog.NATIONALMANNSCHAFT.id,
                    color = ballColor ?: MaterialTheme.colorScheme.primary,
                    dimmed = dimmed,
                    description = when {
                        isImportant -> "Wichtiges Spiel"
                        matchCount == 1 -> "Spieltag"
                        else -> "$matchCount Spiele"
                    },
                )
                if (matchCount > 1) {
                    Text(
                        text = matchCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Zusagen als Zahl statt als Punkt: Wie viele mitkommen, ist die
            // Frage, um die es in der App geht - das gehoert lesbar ins Raster.
            if (attendeeCount > 0 && !dimmed) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(StatusIn),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = attendeeCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B0F14),
                    )
                }
            }
        }
    }
}
