package com.jakober.matchday.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.newId
import com.jakober.matchday.theme.CardCorner
import com.jakober.matchday.ui.components.Avatar
import com.jakober.matchday.ui.components.AvatarColors

/**
 * Erster Start: Name und Farbe. Beides laesst sich spaeter in den
 * Einstellungen aendern.
 */
@Composable
fun OnboardingScreen(onDone: (Profile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorArgb by remember { mutableStateOf(AvatarColors.first()) }

    val trimmed = name.trim()
    val canContinue = trimmed.length >= 2
    val initials = trimmed
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Text(
                text = "Matchday",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Alle Spiele an einem Ort - und deine Truppe weiß, wer dabei ist.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(48.dp))

            // Vorschau des eigenen Avatars, aktualisiert sich beim Tippen.
            Avatar(initials = initials, colorArgb = colorArgb, size = 96.dp)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Wie heißt du?") },
                singleLine = true,
                shape = RoundedCornerShape(CardCorner),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "FARBE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            ColorPicker(
                selected = colorArgb,
                onSelect = { colorArgb = it },
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    onDone(Profile(id = newId(), name = trimmed, colorArgb = colorArgb))
                },
                enabled = canContinue,
                shape = RoundedCornerShape(CardCorner),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Los geht's", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Reihe von Farbkreisen; die gewaehlte bekommt einen Ring. */
@Composable
fun ColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (option in AvatarColors) {
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(option))
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(option) },
            )
        }
    }
}
