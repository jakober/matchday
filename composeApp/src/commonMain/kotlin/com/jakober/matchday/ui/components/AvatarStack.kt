package com.jakober.matchday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus

/**
 * Ueberlappende Avatare derer, die zugesagt haben, dahinter die Anzahl.
 *
 * Zeigt bewusst nur die Zusagen: Wer absagt, ist fuer die Frage "wer kommt
 * mit" uninteressant, und eine Reihe aus Zu- und Absagen waere nicht mehr auf
 * einen Blick lesbar.
 */
@Composable
fun AvatarStack(
    participants: List<Participant>,
    size: Dp = 24.dp,
    maxVisible: Int = 4,
    modifier: Modifier = Modifier,
) {
    val attending = participants.filter { it.status == RsvpStatus.IN }
    if (attending.isEmpty()) return

    val visible = attending.take(maxVisible)
    val overflow = attending.size - visible.size

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        // Negativer Abstand laesst die Kreise ueberlappen.
        horizontalArrangement = Arrangement.spacedBy(-(size / 3)),
    ) {
        for (person in visible) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color(person.colorArgb))
                    // Der Ring in Hintergrundfarbe trennt die Kreise optisch.
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = person.initials,
                    color = Color(0xFF0B0F14),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
        }

        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.34f).sp,
                )
            }
        }
    }
}

/** Avatarreihe mit nachgestelltem Text, etwa "3 dabei". */
@Composable
fun AttendanceLine(
    participants: List<Participant>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 22.dp,
) {
    val attending = participants.filter { it.status == RsvpStatus.IN }
    val declined = participants.filter { it.status == RsvpStatus.OUT }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (attending.isEmpty()) {
            Text(
                text = when {
                    declined.isEmpty() -> "Noch keine Zusage"
                    declined.size == 1 && declined.first().isMe -> "Du hast abgesagt"
                    declined.size == 1 -> "1 Absage"
                    else -> "${declined.size} Absagen"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }

        AvatarStack(participants = attending, size = avatarSize)
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildString {
                append(attendanceText(attending))
                // Absagen nur als Zahl anhaengen; die Gruende stehen in der
                // Detailansicht, in eine Listenzeile passen sie nicht.
                if (declined.isNotEmpty()) append(" · ${declined.size} abgesagt")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Du bist dabei" liest sich besser als "1 dabei". */
private fun attendanceText(attending: List<Participant>): String = when {
    attending.size == 1 && attending.first().isMe -> "Du bist dabei"
    attending.size == 1 -> "${attending.first().name} ist dabei"
    attending.any { it.isMe } -> "Du und ${attending.size - 1} weitere"
    else -> "${attending.size} dabei"
}
