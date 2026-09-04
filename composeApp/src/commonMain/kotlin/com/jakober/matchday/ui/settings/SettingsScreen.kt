package com.jakober.matchday.ui.settings

import com.jakober.matchday.i18n.S
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jakober.matchday.PushState
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.notify.NotificationDiagnostics
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.theme.StatusIn
import com.jakober.matchday.theme.StatusOut
import com.jakober.matchday.ui.components.Avatar
import com.jakober.matchday.ui.onboarding.ColorPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: Profile,
    reminders: ReminderSettings,
    subscriptionCount: Int,
    groupName: String?,
    memberCount: Int,
    diagnostics: NotificationDiagnostics?,
    /** Anmeldekennung des Geraets - aendert sie sich, geht die Gruppe verloren. */
    deviceId: String?,
    pushState: PushState,
    onSendTest: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onProfileChange: (Profile) -> Unit,
    onRemindersChange: (ReminderSettings) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenGroup: () -> Unit,
    /** Adresse des Kontos; null, solange die Sitzung nicht geladen ist. */
    email: String?,
    accountNotice: String?,
    /** Neues Passwort; die Sitzung besteht, also braucht es das alte nicht. */
    onChangePassword: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(S.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.back)
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // -- Profil ------------------------------------------------------
            SectionLabel(S.profile)

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
                    label = { Text(S.name) },
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

            // -- Konto -------------------------------------------------------
            SectionLabel(S.account)
            AccountSection(
                email = email,
                notice = accountNotice,
                onChangePassword = onChangePassword,
                onSignOut = onSignOut,
            )

            // -- Gruppe ------------------------------------------------------
            SectionLabel(S.group)

            LinkRow(
                title = groupName ?: S.noGroup,
                subtitle = when {
                    groupName == null -> S.groupSubtitleNone
                    memberCount <= 1 -> S.groupSubtitleInvite
                    else -> S.membersN(memberCount)
                },
                onClick = onOpenGroup,
            )

            // -- Erinnerungen ------------------------------------------------
            SectionLabel(S.reminderSection)

            SwitchRow(
                label = S.remindBefore,
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

            SectionLabel(S.delivery)

            NotificationStatus(
                diagnostics = diagnostics,
                pushState = pushState,
                onSendTest = onSendTest,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            )

            SectionLabel(S.openRsvps)

            SwitchRow(
                label = S.askWeekBefore,
                description = S.askWeekBeforeDesc,
                checked = reminders.undecidedReminderEnabled,
                onChange = { onRemindersChange(reminders.copy(undecidedReminderEnabled = it)) },
            )

            // -- Kalender ----------------------------------------------------
            SectionLabel(S.calendarSection)

            LinkRow(
                title = S.calendarsN(subscriptionCount),
                subtitle = null,
                onClick = onOpenSubscriptions,
            )

            // Bewusst sichtbar: Aendert sich diese Kennung zwischen zwei
            // Starts, ist die Anmeldesitzung verloren gegangen - und damit die
            // Gruppenzugehoerigkeit. Ohne Anzeige liesse sich das nur in der
            // Datenbank nachsehen.
            Spacer(Modifier.height(28.dp))
            Text(
                text = S.deviceId(deviceId?.take(8)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Zeigt, ob Erinnerungen ueberhaupt ankommen koennen, und laesst es
 * nachpruefen. Ohne diese Auskunft ist nicht unterscheidbar, ob keine
 * Erinnerung kam, weil nichts anstand, oder weil eine Erlaubnis fehlt.
 */
@Composable
private fun NotificationStatus(
    diagnostics: NotificationDiagnostics?,
    pushState: PushState,
    onSendTest: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    var testSent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(16.dp),
    ) {
        if (diagnostics == null) {
            Text(
                text = S.checking,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        StatusLine(
            ok = diagnostics.permissionGranted,
            okText = S.notifAllowed,
            failText = S.notifDenied,
        )

        if (diagnostics.exactAlarmsRelevant) {
            Spacer(Modifier.height(8.dp))
            StatusLine(
                ok = diagnostics.exactAlarmsAllowed,
                okText = S.exactOk,
                failText = S.exactFail,
            )
            if (!diagnostics.exactAlarmsAllowed) {
                TextButton(onClick = onOpenExactAlarmSettings) {
                    Text(S.allowExact)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = when (diagnostics.pendingCount) {
                0 -> S.pendingNone
                1 -> S.pendingOne
                else -> S.pendingN(diagnostics.pendingCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Ohne hinterlegte Kennung erreichen einen die Zusagen der anderen
        // nicht - das ist sonst nicht erkennbar, weil die eigenen
        // Erinnerungen davon unberuehrt weiterlaufen.
        Spacer(Modifier.height(8.dp))
        when (pushState) {
            PushState.REGISTERED -> StatusLine(
                ok = true,
                okText = S.pushOk,
                failText = "",
            )
            PushState.NO_GROUP -> Text(
                text = S.pushNoGroup,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PushState.NO_TOKEN, PushState.UPLOAD_FAILED -> StatusLine(
                ok = false,
                okText = "",
                failText = S.pushFail,
            )
            PushState.UNKNOWN -> Unit
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                onSendTest()
                testSent = true
            },
            shape = RoundedCornerShape(ChipCorner),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(S.sendTest)
        }
        if (testSent) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = S.testHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, failText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (ok) StatusIn else StatusOut),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = if (ok) okText else failText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/**
 * Konto: Adresse, Passwort aendern, Abmelden. Das Passwort laesst sich mit
 * bestehender Sitzung direkt setzen; das alte ist dafuer nicht noetig.
 */
@Composable
private fun AccountSection(
    email: String?,
    notice: String?,
    onChangePassword: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }

    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = { passwordDialog = false },
            title = { Text(S.changePassword) },
            text = {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(S.newPasswordMin) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(ChipCorner),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChangePassword(newPassword)
                        newPassword = ""
                        passwordDialog = false
                    },
                    enabled = newPassword.length >= 8,
                ) { Text(S.save) }
            },
            dismissButton = {
                TextButton(onClick = { passwordDialog = false; newPassword = "" }) { Text(S.cancel) }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(16.dp),
    ) {
        Text(
            text = email ?: S.notSignedIn,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        notice?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = { passwordDialog = true }, enabled = email != null) {
                Text(S.changePassword)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmSignOut = true }) {
                Text(S.signOut, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(S.signOutQuestion) },
            text = {
                Text(
                    S.signOutText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) {
                    Text(S.signOut, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(S.cancel) }
            },
        )
    }
}

/** Anklickbare Zeile mit Pfeil, fuer die Spruenge in Untermenues. */
@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        minutes % (24 * 60) == 0 -> S.daysShort(minutes / (24 * 60))
        minutes % 60 == 0 -> S.hoursShort(minutes / 60)
        else -> S.minutesShort(minutes)
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
