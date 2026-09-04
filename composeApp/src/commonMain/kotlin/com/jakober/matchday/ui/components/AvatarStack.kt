package com.jakober.matchday.ui.components

import com.jakober.matchday.i18n.S
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.theme.StatusIn

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
    avatarSize: Dp = 30.dp,
) {
    val attending = participants.filter { it.status == RsvpStatus.IN }
    val declined = participants.filter { it.status == RsvpStatus.OUT }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (attending.isEmpty()) {
            Text(
                text = when {
                    declined.isEmpty() -> S.noAcceptYet
                    declined.size == 1 && declined.first().isMe -> S.youDeclined
                    declined.size == 1 -> S.oneDecline
                    else -> S.nDeclines(declined.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }

        AvatarStack(participants = attending, size = avatarSize)
        Spacer(Modifier.width(10.dp))
        // Die Zusagen sind der Zweck der App - sie bekommen deshalb die
        // Schriftgroesse eines Titels, nicht die einer Fussnote. Zusage und
        // Absagen-Zusatz sind EIN Text: Als zwei Texte nebeneinander bekam
        // der zweite bei langen Namen keinen Platz mehr und brach Buchstabe
        // fuer Buchstabe um.
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = StatusIn, fontWeight = FontWeight.SemiBold)) {
                    append(attendanceText(attending))
                }
                if (declined.isNotEmpty()) {
                    // Absagen nur als Zahl; die Gruende stehen in der Detailansicht.
                    withStyle(SpanStyle(color = muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)) {
                        append(S.declinedSuffix(declined.size))
                    }
                }
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** "Du bist dabei" liest sich besser als "1 dabei". */
private fun attendanceText(attending: List<Participant>): String = when {
    attending.size == 1 && attending.first().isMe -> S.youAreIn
    attending.size == 1 -> S.xIsIn(attending.first().name)
    attending.any { it.isMe } -> S.youAndN(attending.size - 1)
    else -> S.nIn(attending.size)
}
