package com.jakober.matchday.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.Rsvp
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.ui.components.Avatar

/** Die beiden Darstellungen des Spielplans. */
enum class HomeView { LIST, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: Profile,
    matches: List<Match>,
    /** Fuer den Kalender: einschliesslich bereits gespielter Partien. */
    calendarMatches: List<Match>,
    rsvps: Map<String, Rsvp>,
    view: HomeView,
    isSyncing: Boolean,
    hasSubscriptions: Boolean,
    participantsOf: ParticipantsSource,
    importantIds: Set<String>,
    accentOf: (Match) -> Color,
    subscriptionOf: (String) -> Subscription?,
    onViewChange: (HomeView) -> Unit,
    onSelect: (Match) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Matchday", style = MaterialTheme.typography.headlineSmall)
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                    IconButton(onClick = onOpenSubscriptions) {
                        Icon(Icons.Filled.Add, contentDescription = "Kalender")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Avatar(profile = profile, size = 32.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ViewToggle(
                selected = view,
                onSelect = onViewChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when {
                !hasSubscriptions -> EmptyState(
                    title = "Noch kein Kalender",
                    body = "Der Admin eurer Gruppe fügt Kalender hinzu - zum Beispiel den Spielplan der Bundesliga. Danach stehen alle Termine automatisch hier.",
                    actionLabel = "Kalender ansehen",
                    onAction = onOpenSubscriptions,
                )

                matches.isEmpty() -> EmptyState(
                    title = "Keine Spiele gefunden",
                    body = "Der Kalender ist abonniert, enthält aber keine anstehenden Termine.",
                    actionLabel = "Neu laden",
                    onAction = onRefresh,
                )

                else -> AnimatedContent(
                    targetState = view,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ansicht",
                    // Ohne weight bekaeme der Kalender keine begrenzte Hoehe
                    // und koennte sich nicht an den Bildschirm anpassen.
                    modifier = Modifier.weight(1f),
                ) { current ->
                    when (current) {
                        HomeView.LIST -> MatchListView(
                            matches = matches,
                            rsvps = rsvps,
                            participantsOf = participantsOf,
                            importantIds = importantIds,
                            subscriptionOf = subscriptionOf,
                            onSelect = onSelect,
                        )

                        HomeView.MONTH -> MonthView(
                            matches = calendarMatches,
                            rsvps = rsvps,
                            participantsOf = participantsOf,
                            importantIds = importantIds,
                            accentOf = accentOf,
                            subscriptionOf = subscriptionOf,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

/** Segmentumschalter zwischen Liste und Monat. */
@Composable
private fun ViewToggle(
    selected: HomeView,
    onSelect: (HomeView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ChipCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ToggleSegment("Liste", selected == HomeView.LIST, { onSelect(HomeView.LIST) }, Modifier.weight(1f))
        ToggleSegment("Monat", selected == HomeView.MONTH, { onSelect(HomeView.MONTH) }, Modifier.weight(1f))
    }
}

@Composable
private fun ToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ChipCorner - 3.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
