package com.jakober.matchday.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jakober.matchday.data.remote.GroupMembership
import com.jakober.matchday.data.remote.MemberDto
import com.jakober.matchday.data.remote.SCOPE_ALL
import com.jakober.matchday.data.remote.SCOPE_IMPORTANT
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.ui.components.Avatar

/**
 * Gruppe anlegen, beitreten und verwalten. Ohne Gruppe bleibt die Zusage
 * privat; mit Gruppe sehen alle, wer mitkommt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    membership: GroupMembership?,
    members: List<MemberDto>,
    busy: Boolean,
    error: String?,
    /** Zuletzt erzeugte Einladung. */
    invite: InviteResult?,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    /** Sichtbarkeit und - optional - die Adresse, an die der Code gehen soll. */
    onCreateInvite: (scope: String, email: String?) -> Unit,
    onRemoveMember: (MemberDto) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    /**
     * Gesetzt, wenn der Bildschirm als Tor dient - ohne Gruppe geht es
     * nicht weiter, und statt "zurueck" gibt es nur "abmelden".
     */
    onSignOut: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (onSignOut != null) "Deine Gruppe" else "Gruppe") },
                navigationIcon = {
                    if (onSignOut == null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (onSignOut != null) {
                        TextButton(onClick = onSignOut) { Text("Abmelden") }
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
            if (membership == null) {
                NoGroup(busy = busy, error = error, onCreate = onCreate, onJoin = onJoin)
            } else {
                InGroup(
                    membership = membership,
                    members = members,
                    busy = busy,
                    error = error,
                    invite = invite,
                    onCreateInvite = onCreateInvite,
                    onRemoveMember = onRemoveMember,
                    onLeave = onLeave,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NoGroup(
    busy: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "Matchday lebt von der Gruppe: Ihr teilt die Kalender und seht, wer zu einem Spiel mitkommt. " +
            "Lege eine Gruppe an oder tritt mit einem Einladungscode bei.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(28.dp))
    Label("NEUE GRUPPE")
    OutlinedTextField(
        value = groupName,
        onValueChange = { groupName = it },
        label = { Text("Name der Gruppe") },
        placeholder = { Text("z.B. Stammtisch") },
        singleLine = true,
        shape = RoundedCornerShape(ChipCorner),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Wer die Gruppe anlegt, verwaltet sie: einladen und Spiele hervorheben.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { onCreate(groupName.trim()) },
        enabled = !busy && groupName.trim().length >= 2,
        shape = RoundedCornerShape(ChipCorner),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Gruppe erstellen")
    }

    Spacer(Modifier.height(32.dp))
    Label("ODER BEITRETEN")
    OutlinedTextField(
        value = code,
        // Der Code besteht aus Grossbuchstaben und Ziffern; die Umwandlung
        // erspart Tippfehler durch Autokorrektur.
        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
        label = { Text("Einladungscode") },
        placeholder = { Text("6 Zeichen") },
        singleLine = true,
        shape = RoundedCornerShape(ChipCorner),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = { onJoin(code) },
        enabled = !busy && code.length == 6,
        shape = RoundedCornerShape(ChipCorner),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        Text("Beitreten")
    }

    ErrorLine(error)
}

@Composable
private fun InGroup(
    membership: GroupMembership,
    members: List<MemberDto>,
    busy: Boolean,
    error: String?,
    invite: InviteResult?,
    onCreateInvite: (scope: String, email: String?) -> Unit,
    onRemoveMember: (MemberDto) -> Unit,
    onLeave: () -> Unit,
) {
    // Vor dem Entfernen nachfragen: Die Zusagen des Mitglieds verschwinden mit.
    var zuEntfernen by remember { mutableStateOf<MemberDto?>(null) }
    var inviteEmail by remember { mutableStateOf("") }

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = membership.groupName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (membership.isAdmin) {
            Text(
                text = "ADMIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (membership.seesOnlyImportant) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(ChipCorner))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Du siehst nur die hervorgehobenen Spiele.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (membership.isAdmin) {
        Spacer(Modifier.height(28.dp))
        Label("EINLADEN")
        Text(
            text = "Jede Einladung gilt einmal. Du legst dabei fest, was der Eingeladene zu sehen bekommt. " +
                "Mit Adresse geht der Code direkt per E-Mail raus, ohne siehst du ihn hier zum Weitergeben.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = inviteEmail,
            onValueChange = { inviteEmail = it },
            label = { Text("E-Mail-Adresse (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(ChipCorner),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        val email = inviteEmail.trim().ifEmpty { null }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onCreateInvite(SCOPE_ALL, email) },
                enabled = !busy,
                shape = RoundedCornerShape(ChipCorner),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("Alle Spiele")
            }
            OutlinedButton(
                onClick = { onCreateInvite(SCOPE_IMPORTANT, email) },
                enabled = !busy,
                shape = RoundedCornerShape(ChipCorner),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("Nur wichtige")
            }
        }

        invite?.let {
            Spacer(Modifier.height(16.dp))
            InviteCode(code = it.code, scope = it.scope, sentTo = it.sentTo, warning = it.warning)
        }
    }

    ErrorLine(error)

    Spacer(Modifier.height(28.dp))
    Label(if (members.size == 1) "1 MITGLIED" else "${members.size} MITGLIEDER")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (member in members) {
            MemberRow(
                member = member,
                isMe = member.id == membership.memberId,
                showScope = membership.isAdmin,
                // Der Admin kann alle entfernen ausser sich selbst - sonst
                // bliebe die Gruppe ohne Verantwortlichen zurueck.
                canRemove = membership.isAdmin && member.id != membership.memberId,
                onRemove = { zuEntfernen = member },
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text("Gruppe verlassen", color = MaterialTheme.colorScheme.error)
    }

    zuEntfernen?.let { member ->
        AlertDialog(
            onDismissRequest = { zuEntfernen = null },
            title = { Text("${member.displayName} entfernen?") },
            text = {
                Text(
                    "Alle Zu- und Absagen dieser Person verschwinden aus der Gruppe. " +
                        "Für eine Rückkehr braucht sie eine neue Einladung."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveMember(member)
                    zuEntfernen = null
                }) {
                    Text("Entfernen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zuEntfernen = null }) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Der Code bleibt sichtbar, auch wenn er per Mail ging: Die Mail ist die
 * Bequemlichkeit, der angezeigte Code die Rueckfallebene. Sonst verschwaende
 * eine Einladung an eine vertippte Adresse spurlos.
 */
@Composable
private fun InviteCode(code: String, scope: String, sentTo: String?, warning: String?) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CardCorner))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = code,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            // Weiter Zeichenabstand: So verwechselt niemand 0 und O beim
            // Abtippen oder Vorlesen am Telefon.
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (scope == SCOPE_IMPORTANT) "sieht nur wichtige Spiele" else "sieht alle Spiele",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        sentTo?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Per E-Mail an $it geschickt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        warning?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$it - gib den Code stattdessen so weiter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
            Text("Kopieren")
        }
    }
}

/** Zuletzt erzeugte Einladung, wie die Oberflaeche sie zeigt. */
data class InviteResult(
    val code: String,
    val scope: String,
    val sentTo: String? = null,
    val warning: String? = null,
)

@Composable
private fun MemberRow(
    member: MemberDto,
    isMe: Boolean,
    showScope: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CardCorner))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initials = member.displayName.trim().split(" ")
                .filter { it.isNotEmpty() }.take(2)
                .joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
            colorArgb = member.color,
            size = 34.dp,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showScope && member.scope == SCOPE_IMPORTANT) {
                Text(
                    text = "nur wichtige Spiele",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isMe) {
            Text(
                text = "Du",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.PersonRemove,
                    contentDescription = "${member.displayName} entfernen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let {
        Spacer(Modifier.height(14.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
