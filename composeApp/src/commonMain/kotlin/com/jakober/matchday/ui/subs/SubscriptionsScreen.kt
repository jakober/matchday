package com.jakober.matchday.ui.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jakober.matchday.Container
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.domain.newId
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.ui.components.AvatarColors
import kotlinx.coroutines.launch

/**
 * Verwaltung der Kalender-Abos. Jede ICS- oder webcal-Adresse laesst sich
 * abonnieren; die Vereinsseiten und die gaengigen Spielplan-Dienste liefern
 * solche Adressen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    onBack: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Kalender") },
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
        ) {
            Button(
                onClick = { showAdd = true },
                shape = RoundedCornerShape(CardCorner),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Kalender hinzufügen")
            }

            Spacer(Modifier.height(16.dp))

            if (subscriptions.isEmpty()) {
                Text(
                    text = "Noch nichts abonniert. Du brauchst die ICS- oder webcal-Adresse des Spielplans - die meisten Vereins- und Ligaseiten bieten sie unter \"Kalender abonnieren\" an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(subscriptions, key = { it.id }) { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        onRemove = { Container.store.removeSubscription(subscription.id) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddSubscriptionDialog(
            onDismiss = { showAdd = false },
            onAdded = { showAdd = false },
        )
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(subscription.colorArgb)),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = subscription.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subscription.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Entfernen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Adresse eingeben, pruefen, uebernehmen. Der Feed wird vor dem Speichern
 * geladen - so faellt eine falsche Adresse sofort auf und nicht erst, wenn die
 * Liste leer bleibt.
 */
@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var foundCount by remember { mutableStateOf<Int?>(null) }
    var normalizedUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kalender abonnieren") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        foundCount = null
                        error = null
                    },
                    label = { Text("ICS- oder webcal-Adresse") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (foundCount != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${foundCount} Termine gefunden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (foundCount == null) {
                TextButton(
                    enabled = url.isNotBlank() && !checking,
                    onClick = {
                        checking = true
                        error = null
                        scope.launch {
                            Container.repository.preview(url)
                                .onSuccess { preview ->
                                    foundCount = preview.matchCount
                                    name = preview.suggestedName
                                    normalizedUrl = preview.normalizedUrl
                                }
                                .onFailure {
                                    error = it.message ?: "Die Adresse konnte nicht geladen werden."
                                }
                            checking = false
                        }
                    },
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Prüfen")
                    }
                }
            } else {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val subscription = Subscription(
                            id = newId(),
                            name = name.trim(),
                            url = normalizedUrl ?: url.trim(),
                            // Farbe reihum aus der Palette, damit sich mehrere
                            // Abos in Liste und Kalender unterscheiden.
                            colorArgb = AvatarColors[
                                Container.store.subscriptions.value.size % AvatarColors.size
                            ],
                        )
                        Container.store.addSubscription(subscription)
                        Container.syncAll()
                        onAdded()
                    },
                ) {
                    Text("Abonnieren")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
