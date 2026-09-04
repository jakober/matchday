package com.jakober.matchday.ui.subs

import com.jakober.matchday.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jakober.matchday.data.CatalogFeed
import com.jakober.matchday.data.FeedCatalog
import com.jakober.matchday.data.FeedPreview
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.theme.ChipCorner
import com.jakober.matchday.ui.components.AvatarColors
import com.jakober.matchday.ui.onboarding.ColorPicker

/**
 * Kalender hinzufuegen: Adresse eingeben, pruefen, benennen, uebernehmen.
 *
 * Die Erklaerung steht bewusst ueber dem Feld. Wer hier landet, weiss in der
 * Regel nicht, was eine ICS-Adresse ist - ohne den Text wuerde er die Adresse
 * der Vereinsseite eintippen und eine unverstaendliche Fehlermeldung bekommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    busy: Boolean,
    preview: FeedPreview?,
    error: String?,
    onPreview: (String) -> Unit,
    onAdd: (name: String, url: String, colorArgb: Long) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var colorArgb by remember { mutableStateOf(AvatarColors.first()) }
    var helpOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Name aus der eingebauten Liste, wenn von dort gewaehlt - der ist
    // kuerzer als das, was der Kalender selbst mitbringt.
    var pickedName by remember { mutableStateOf<String?>(null) }

    // Den Namensvorschlag uebernehmen, sobald die Vorschau da ist - aber nur
    // dann, sonst wuerde jede Eingabe des Nutzers wieder ueberschrieben.
    LaunchedEffect(preview) {
        preview?.let { name = pickedName ?: it.suggestedName }
    }

    val suggestions = remember(query) {
        if (query.isBlank()) FeedCatalog.featured
        else FeedCatalog.search(query).take(10)
    }

    val canCheck = url.isNotBlank() && !busy
    val canAdd = preview != null && name.trim().length >= 2 && !busy

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(S.addCalendar) },
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = S.searchLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(S.searchHint) },
                singleLine = true,
                shape = RoundedCornerShape(ChipCorner),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (suggestions.isEmpty()) {
                Text(
                    text = S.nothingFound,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            for (feed in suggestions) {
                SuggestionRow(
                    feed = feed,
                    selected = url == feed.url,
                    onClick = {
                        url = feed.url
                        pickedName = feed.name
                        name = feed.name
                        onPreview(feed.url)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = S.orEnterAddress,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = S.importIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = { helpOpen = !helpOpen }) {
                Text(if (helpOpen) S.hideHelp else S.whereAddress)
            }
            if (helpOpen) {
                HelpCard()
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    // Von Hand geaendert: Der Name aus der Liste passt nicht mehr.
                    pickedName = null
                },
                label = { Text(S.addressLabel) },
                placeholder = { Text("https://…/spielplan.ics") },
                singleLine = true,
                shape = RoundedCornerShape(ChipCorner),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { if (canCheck) onPreview(url) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onPreview(url) },
                enabled = canCheck,
                shape = RoundedCornerShape(ChipCorner),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (busy && preview == null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(S.check)
                }
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (preview != null) {
                Spacer(Modifier.height(28.dp))
                PreviewCard(preview)

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(S.nameInApp) },
                    singleLine = true,
                    shape = RoundedCornerShape(ChipCorner),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = S.color,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ColorPicker(selected = colorArgb, onSelect = { colorArgb = it })

                Spacer(Modifier.height(20.dp))
                Text(
                    text = S.groupWide,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAdd(name.trim(), preview.normalizedUrl, colorArgb) },
                    enabled = canAdd,
                    shape = RoundedCornerShape(ChipCorner),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(S.add)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SuggestionRow(feed: CatalogFeed, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ChipCorner))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(ChipCorner))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = feed.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = feed.league,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewCard(preview: FeedPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CardCorner))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardCorner))
            .padding(16.dp),
    ) {
        Text(
            text = preview.suggestedName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (preview.matchCount) {
                0 -> S.previewNone
                1 -> S.previewOne
                else -> S.previewN(preview.matchCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview.matchCount == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = S.recurringWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preview.normalizedUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HelpCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(CardCorner))
            .padding(16.dp),
    ) {
        HelpParagraph(
            S.help1
        )
        Spacer(Modifier.height(10.dp))
        HelpParagraph(
            S.help2
        )
        Spacer(Modifier.height(10.dp))
        HelpParagraph(
            S.help3
        )
    }
}

@Composable
private fun HelpParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
