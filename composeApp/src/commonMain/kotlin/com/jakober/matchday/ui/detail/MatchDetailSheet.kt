package com.jakober.matchday.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.theme.StatusIn
import com.jakober.matchday.theme.StatusOpen
import com.jakober.matchday.theme.StatusOut
import com.jakober.matchday.ui.components.AttendanceLine
import com.jakober.matchday.ui.components.Avatar
import com.jakober.matchday.ui.components.DateText
import com.jakober.matchday.ui.components.local

/** Laenge des Absagegrunds - genug fuer einen Satz, zu wenig fuer einen Aufsatz. */
private const val MAX_COMMENT = 140

/**
 * Detailansicht als Bottom Sheet. Hier wird die Teilnahme gesetzt, begruendet
 * und wieder zurueckgenommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailSheet(
    match: Match,
    status: RsvpStatus,
    comment: String?,
    participants: List<Participant>,
    accent: Color,
    isImportant: Boolean,
    /** Nur der Admin darf die Hervorhebung setzen. */
    canEditImportant: Boolean,
    onToggleImportant: () -> Unit,
    onSetStatus: (RsvpStatus, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateTime = match.start.local()

    var draft by remember(match.id) { mutableStateOf(comment.orEmpty()) }
    // Das Kommentarfeld erscheint erst bei einer Absage - bei einer Zusage
    // will niemand etwas begruenden.
    var showComment by remember(match.id) { mutableStateOf(status == RsvpStatus.OUT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Die Einrueckungen traegt der Inhalt selbst - sonst rechnet das Sheet
        // die Tastatur nicht ein und das Kommentarfeld verschwindet darunter.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Ohne Scrollbarkeit ist bei offener Tastatur weder das Feld
                // noch der Speichern-Knopf erreichbar.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isImportant) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Wichtiges Spiel",
                        tint = StatusOpen,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            if (canEditImportant) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ChipCorner))
                        .background(
                            if (isImportant) StatusOpen.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (isImportant) StatusOpen else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(ChipCorner),
                        )
                        .clickable(onClick = onToggleImportant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (isImportant) StatusOpen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (isImportant) "Hervorhebung aufheben" else "Als wichtig hervorheben",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isImportant) StatusOpen else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

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

            Spacer(Modifier.height(24.dp))

            Text(
                text = "WER KOMMT MIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            AttendanceLine(participants = participants, avatarSize = 28.dp)

            DeclineNotes(participants)

            Spacer(Modifier.height(24.dp))

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
                    onClick = {
                        onSetStatus(RsvpStatus.IN, null)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                ChoiceButton(
                    label = "Nicht dabei",
                    icon = Icons.Filled.Close,
                    color = StatusOut,
                    selected = status == RsvpStatus.OUT,
                    onClick = {
                        // Absage sofort speichern, damit sie auch dann steht,
                        // wenn das Sheet ohne Speichern weggewischt wird.
                        onSetStatus(RsvpStatus.OUT, draft.ifBlank { null })
                        showComment = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (showComment && status == RsvpStatus.OUT) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_COMMENT) draft = it },
                    label = { Text("Warum nicht? (optional)") },
                    placeholder = { Text("z.B. bin im Urlaub") },
                    supportingText = { Text("${draft.length}/$MAX_COMMENT") },
                    shape = RoundedCornerShape(ChipCorner),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSetStatus(RsvpStatus.OUT, draft.ifBlank { null })
                        onDismiss()
                    },
                    shape = RoundedCornerShape(ChipCorner),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (draft.isBlank()) "Ohne Grund absagen" else "Speichern")
                }
            }

            // Zuruecknehmen setzt auf "offen" - damit greift auch die
            // Wochen-Erinnerung wieder.
            if (status != RsvpStatus.UNDECIDED) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        onSetStatus(RsvpStatus.UNDECIDED, null)
                        onDismiss()
                    },
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

/** Absagen mit Begruendung, damit man den Grund sieht und nicht nur die Zahl. */
@Composable
private fun DeclineNotes(participants: List<Participant>) {
    val declines = participants.filter {
        it.status == RsvpStatus.OUT && !it.comment.isNullOrBlank()
    }
    if (declines.isEmpty()) return

    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (person in declines) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                Avatar(
                    initials = person.initials,
                    colorArgb = person.colorArgb,
                    size = 26.dp,
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = if (person.isMe) "Du" else person.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusOut,
                    )
                    Text(
                        text = person.comment.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
