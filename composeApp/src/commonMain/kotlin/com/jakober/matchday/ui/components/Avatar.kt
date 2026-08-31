package com.jakober.matchday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.jakober.matchday.domain.Profile

/** Auswahl fuer den Monogramm-Avatar - kraeftig genug fuer weisse Schrift. */
val AvatarColors = listOf(
    0xFF37E27AL, 0xFF3FA9F5L, 0xFFB06BFFL, 0xFFFF6B6BL,
    0xFFFFA23EL, 0xFF17C3B2L, 0xFFEE5D9CL, 0xFF8A94A6L,
)

@Composable
fun Avatar(
    profile: Profile,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Avatar(
        initials = profile.initials,
        colorArgb = profile.colorArgb,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun Avatar(
    initials: String,
    colorArgb: Long,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorArgb)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color(0xFF0B0F14),
            fontWeight = FontWeight.Bold,
            // Schriftgroesse mitwachsen lassen, damit der Avatar in jeder
            // Groesse gleich aussieht.
            fontSize = (size.value * 0.38f).sp,
        )
    }
}
