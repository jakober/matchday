package com.jakober.matchday.ui.components

import androidx.compose.foundation.Canvas
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
import com.jakober.matchday.data.TeamCatalog
import com.jakober.matchday.data.TeamFeed

/**
 * Abzeichen einer Mannschaft. Gibt es ein Wappen als Bild, wird es geladen;
 * sonst zeichnen wir ein Ersatzabzeichen. Beides ist rund und gleich gross,
 * damit die Liste ruhig bleibt.
 */
@Composable
fun TeamBadge(
    team: TeamFeed?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val shell = modifier.size(size).clip(CircleShape)

    when {
        team == null -> Box(shell.background(MaterialTheme.colorScheme.surfaceVariant))

        team.logoUrl != null -> SubcomposeAsyncImage(
            model = team.logoUrl,
            contentDescription = team.name,
            contentScale = ContentScale.Fit,
            // Solange das Wappen laedt - und falls es gar nicht kommt - steht
            // das Ersatzabzeichen an seiner Stelle. Ohne das blinkt die Liste
            // beim Scrollen mit leeren Kreisen.
            loading = { FallbackBadge(team, size) },
            error = { FallbackBadge(team, size) },
            modifier = shell,
        )

        else -> Box(shell) { FallbackBadge(team, size) }
    }
}

@Composable
private fun FallbackBadge(team: TeamFeed, size: Dp) {
    if (team.id == TeamCatalog.NATIONALMANNSCHAFT.id) {
        GermanFlagBadge(size)
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(Color(team.colorArgb)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = team.shortName.take(3).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.3f).sp,
            )
        }
    }
}

/** Schwarz-Rot-Gold als liegende Streifen im Kreis. */
@Composable
private fun GermanFlagBadge(size: Dp) {
    Canvas(modifier = Modifier.size(size).clip(CircleShape)) {
        val stripe = this.size.height / 3f
        drawRect(
            color = Color(0xFF1A1A1A),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(this.size.width, stripe),
        )
        drawRect(
            color = Color(0xFFDD0000),
            topLeft = androidx.compose.ui.geometry.Offset(0f, stripe),
            size = androidx.compose.ui.geometry.Size(this.size.width, stripe),
        )
        drawRect(
            color = Color(0xFFFFCE00),
            topLeft = androidx.compose.ui.geometry.Offset(0f, stripe * 2f),
            size = androidx.compose.ui.geometry.Size(this.size.width, stripe),
        )
    }
}
