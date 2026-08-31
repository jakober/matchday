package com.jakober.matchday.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Akzent der App: kraeftiges Rasengruen, funktioniert hell wie dunkel. */
val Pitch = Color(0xFF37E27A)
val PitchDim = Color(0xFF1E9E52)

/** Zusagestatus - bewusst nicht Rot/Gruen allein, damit es auch bei
 *  Farbfehlsichtigkeit unterscheidbar bleibt (Form und Text tragen mit). */
val StatusIn = Color(0xFF37E27A)
val StatusOut = Color(0xFFFF6B6B)
val StatusOpen = Color(0xFFFFC24B)

private val DarkColors = darkColorScheme(
    primary = Pitch,
    onPrimary = Color(0xFF04150A),
    primaryContainer = Color(0xFF12351F),
    onPrimaryContainer = Pitch,
    secondary = Color(0xFF8FA3B8),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF11161D),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF1A212A),
    onSurfaceVariant = Color(0xFF9AA7B5),
    outline = Color(0xFF2A333F),
    outlineVariant = Color(0xFF1E262F),
    error = StatusOut,
)

private val LightColors = lightColorScheme(
    primary = PitchDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5E2),
    onPrimaryContainer = Color(0xFF0A3D22),
    secondary = Color(0xFF4A5A6B),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF0B0F14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0F14),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5A6775),
    outline = Color(0xFFD5DDE5),
    outlineVariant = Color(0xFFE6ECF2),
    error = Color(0xFFD03A3A),
)

/**
 * Typografie mit deutlicher Groessenspreizung: grosse Zahlen fuer Datum und
 * Uhrzeit, ruhige Grundschrift fuer alles andere.
 */
private val MatchdayTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        ),
    )
}

/** Kennzahlen-Stil fuer Uhrzeiten und Tageszahlen im Kalender. */
val NumberStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    letterSpacing = (-0.2).sp,
    textAlign = TextAlign.Center,
)

/** Einheitliche Eckenrundung fuer Karten und Flaechen. */
val CardCorner = 20.dp
val ChipCorner = 12.dp

@Composable
fun MatchdayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MatchdayTypography,
        content = content,
    )
}
