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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jakober.matchday.data.remote.GroupMembership
import com.jakober.matchday.data.remote.MemberDto
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.ui.components.Avatar

/**
 * Gruppe anlegen, beitreten und ansehen. Ohne Gruppe bleibt die Zusage privat;
 * mit Gruppe sehen alle, wer mitkommt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    membership: GroupMembership?,
    members: List<MemberDto>,
    busy: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gruppe") },
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
        text = "Ohne Gruppe bleibt deine Zusage nur auf diesem Gerät. In einer Gruppe sehen alle, wer zu einem Spiel mitkommt.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(28.dp))
    Text(
        text = "NEUE GRUPPE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
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
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { onCreate(groupName.trim()) },
        enabled = !busy && groupName.trim().length >= 2,
        shape = RoundedCornerShape(ChipCorner),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Gruppe erstellen")
        }
    }

    Spacer(Modifier.height(32.dp))
    Text(
        text = "ODER BEITRETEN",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
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

    error?.let {
        Spacer(Modifier.height(14.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun InGroup(
    membership: GroupMembership,
    members: List<MemberDto>,
    onLeave: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Spacer(Modifier.height(8.dp))
    Text(
        text = membership.groupName,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(Modifier.height(24.dp))
    Text(
        text = "EINLADUNGSCODE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CardCorner))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = membership.inviteCode,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            // Weiter Zeichenabstand: So verwechselt niemand 0 und O beim
            // Abtippen oder Vorlesen am Telefon.
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { clipboard.setText(AnnotatedString(membership.inviteCode)) }) {
            Text("Kopieren")
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = "Wer diesen Code eingibt, ist in der Gruppe und sieht die Zusagen aller.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(28.dp))
    Text(
        text = if (members.size == 1) "1 MITGLIED" else "${members.size} MITGLIEDER",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (member in members) {
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
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (member.id == membership.memberId) {
                    Text(
                        text = "Du",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
    TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text("Gruppe verlassen", color = MaterialTheme.colorScheme.error)
    }
}
