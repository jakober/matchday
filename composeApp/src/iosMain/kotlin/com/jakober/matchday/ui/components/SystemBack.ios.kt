package com.jakober.matchday.ui.components

import androidx.compose.runtime.Composable

/**
 * Auf iOS gibt es hier nichts abzufangen: Das Wischen vom linken Rand ist dort
 * keine systemweite Zurueck-Geste, sondern eine Eigenschaft der Navigation von
 * UIKit. Eine reine Compose-Oberflaeche bekommt sie nicht geliefert. Und eine
 * versehentlich beendete App gibt es auf iOS ohnehin nicht - das Wischen von
 * unten fuehrt zum Startbildschirm, ohne etwas zu verlieren.
 *
 * Der Pfeil oben links bleibt auf beiden Plattformen der verlaessliche Weg.
 */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
