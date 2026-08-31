package com.jakober.matchday.ui.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jakober.matchday.data.TeamCatalog
import com.jakober.matchday.data.TeamFeed
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.TeamBadge
import com.jakober.matchday.ui.components.local

/**
 * Auswahl der Mannschaften. Die App deckt bewusst nur Bayern und die
 * Nationalmannschaft ab, deshalb eine feste Liste statt einer Suche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    subscriptions: List<Subscription>,
    matchCountOf: (String) -> Int,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mannschaften") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (team in TeamCatalog.ALL) {
                val subscription = subscriptions.firstOrNull { it.id == team.id }
                TeamRow(
                    team = team,
                    enabled = subscription?.enabled ?: false,
                    matchCount = matchCountOf(team.id),
                    lastSynced = subscription?.lastSyncedAt?.let {
                        "${DateText.fullDate(it.local().date)}, ${DateText.time(it.local())} Uhr"
                    },
                    onToggle = { onToggle(team.id, it) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Die Spielpläne werden automatisch im Hintergrund aktualisiert - auch verlegte Anstoßzeiten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamRow(
    team: TeamFeed,
    enabled: Boolean,
    matchCount: Int,
    lastSynced: String?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CardCorner))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(team = team, size = 44.dp)
        Spacer(Modifier.size(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    !enabled -> "Nicht abonniert"
                    matchCount == 0 -> "Noch keine Spiele geladen"
                    lastSynced == null -> "$matchCount Spiele"
                    else -> "$matchCount Spiele · Stand $lastSynced"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
