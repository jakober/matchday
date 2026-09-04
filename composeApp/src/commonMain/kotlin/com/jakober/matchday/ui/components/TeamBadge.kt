package com.jakober.matchday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.jakober.matchday.domain.Subscription

/**
 * Rundes Abzeichen. Gibt es ein Wappen als Bild, wird es geladen; sonst
 * zeichnen wir ein Ersatzabzeichen aus Farbe und Kuerzel. Beides ist gleich
 * gross, damit die Liste ruhig bleibt.
 *
 * Bewusst ohne Wissen darueber, was es zeigt - Mannschaft oder Kalender
 * entscheidet der Aufrufer. Ein leeres Kuerzel ergibt einen grauen Kreis,
 * den der Monatskalender fuer Tage ohne Spiel braucht.
 */
@Composable
fun TeamBadge(
    logoUrl: String?,
    fallbackLabel: String,
    fallbackColor: Long,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val shell = modifier.size(size).clip(CircleShape)

    when {
        fallbackLabel.isEmpty() && logoUrl == null ->
            Box(shell.background(MaterialTheme.colorScheme.surfaceVariant))

        logoUrl != null -> SubcomposeAsyncImage(
            model = logoUrl,
            contentDescription = fallbackLabel,
            contentScale = ContentScale.Fit,
            // Solange das Wappen laedt - und falls es gar nicht kommt - steht
            // das Ersatzabzeichen an seiner Stelle. Ohne das blinkt die Liste
            // beim Scrollen mit leeren Kreisen.
            loading = { FallbackBadge(fallbackLabel, fallbackColor, size) },
            error = { FallbackBadge(fallbackLabel, fallbackColor, size) },
            modifier = shell,
        )

        else -> Box(shell) { FallbackBadge(fallbackLabel, fallbackColor, size) }
    }
}

/** Abzeichen eines Kalender-Abos: dessen Bild, sonst Farbe und Kuerzel. */
@Composable
fun TeamBadge(
    subscription: Subscription?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    TeamBadge(
        logoUrl = subscription?.logoUrl,
        fallbackLabel = subscription?.badgeLabel ?: "",
        fallbackColor = subscription?.colorArgb ?: 0xFF888888,
        size = size,
        modifier = modifier,
    )
}

@Composable
private fun FallbackBadge(label: String, colorArgb: Long, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(colorArgb)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(3),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.3f).sp,
        )
    }
}
