package com.jakober.matchday.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.ui.components.Avatar
import com.jakober.matchday.ui.onboarding.ColorPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: Profile,
    reminders: ReminderSettings,
    subscriptionCount: Int,
    onProfileChange: (Profile) -> Unit,
    onRemindersChange: (ReminderSettings) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // -- Profil ------------------------------------------------------
            SectionLabel("PROFIL")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    initials = profile.initials,
                    colorArgb = profile.colorArgb,
                    size = 64.dp,
                )
                Spacer(Modifier.padding(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        // Erst ab zwei Zeichen speichern, sonst steht beim
                        // Leeren des Feldes kurzzeitig ein leerer Name drin.
                        if (it.trim().length >= 2) {
                            onProfileChange(profile.copy(name = it.trim()))
                        }
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(ChipCorner),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            ColorPicker(
                selected = profile.colorArgb,
                onSelect = { onProfileChange(profile.copy(colorArgb = it)) },
            )

            // -- Erinnerungen ------------------------------------------------
            SectionLabel("ERINNERUNG VOR ANPFIFF")

            SwitchRow(
                label = "Vor dem Spiel erinnern",
                checked = reminders.kickoffReminderEnabled,
                onChange = { onRemindersChange(reminders.copy(kickoffReminderEnabled = it)) },
            )

            if (reminders.kickoffReminderEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in ReminderSettings.CHOICES) {
                        MinuteChip(
                            minutes = choice,
                            selected = reminders.minutesBefore == choice,
                            onClick = { onRemindersChange(reminders.copy(minutesBefore = choice)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionLabel("OFFENE ZUSAGEN")

            SwitchRow(
                label = "Eine Woche vorher nachfragen",
                description = "Erinnert dich, wenn ein Spiel in sieben Tagen ansteht und du noch nicht geantwortet hast.",
                checked = reminders.undecidedReminderEnabled,
                onChange = { onRemindersChange(reminders.copy(undecidedReminderEnabled = it)) },
            )

            // -- Kalender ----------------------------------------------------
            SectionLabel("KALENDER")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardCorner))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
                    .clickable(onClick = onOpenSubscriptions)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (subscriptionCount == 1) {
                        "1 Kalender abonniert"
                    } else {
                        "$subscriptionCount Kalender abonniert"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(28.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "1 Std", "30 Min", "1 Tag" - je nach Groesse. */
@Composable
private fun MinuteChip(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} Tag"
        minutes % 60 == 0 -> "${minutes / 60} Std"
        else -> "$minutes Min"
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(ChipCorner))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(ChipCorner),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
