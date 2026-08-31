package com.jakober.matchday.ui.detail

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.StatusIn
import com.jakober.matchday.theme.StatusOut
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local

/**
 * Detailansicht als Bottom Sheet. Hier wird die Teilnahme gesetzt und wieder
 * zurueckgenommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailSheet(
    match: Match,
    status: RsvpStatus,
    accent: Color,
    onSetStatus: (RsvpStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateTime = match.start.local()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            // Wettbewerb als kleines farbiges Etikett ueber dem Titel.
            match.competition?.takeIf { it.isNotBlank() }?.let { competition ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = competition.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = match.displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            InfoLine(
                icon = Icons.Filled.Schedule,
                text = buildString {
                    append(DateText.weekdayLong(dateTime.date))
                    append(", ")
                    append(DateText.fullDate(dateTime.date))
                    if (!match.isAllDay) {
                        append(" · ")
                        append(DateText.time(dateTime))
                        append(" Uhr")
                    }
                },
            )

            match.location?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                InfoLine(icon = Icons.Filled.LocationOn, text = it)
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "BIST DU DABEI?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceButton(
                    label = "Dabei",
                    icon = Icons.Filled.Check,
                    color = StatusIn,
                    selected = status == RsvpStatus.IN,
                    onClick = { onSetStatus(RsvpStatus.IN) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceButton(
                    label = "Nicht dabei",
                    icon = Icons.Filled.Close,
                    color = StatusOut,
                    selected = status == RsvpStatus.OUT,
                    onClick = { onSetStatus(RsvpStatus.OUT) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Zuruecknehmen setzt auf "offen" - damit greift auch die
            // Wochen-Erinnerung wieder.
            if (status != RsvpStatus.UNDECIDED) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { onSetStatus(RsvpStatus.UNDECIDED) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Antwort zurücknehmen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Auswahlflaeche mit Rahmen. Der gewaehlte Zustand ist an Fuellung, Rahmen und
 * Symbol erkennbar, nicht nur an der Farbe.
 */
@Composable
private fun ChoiceButton(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(CardCorner))
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(CardCorner),
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
        )
    }
}
