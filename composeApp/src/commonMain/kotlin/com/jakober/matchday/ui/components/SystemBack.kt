package com.jakober.matchday.ui.components

import androidx.compose.runtime.Composable

/**
 * Faengt die Zurueck-Geste des Systems ab.
 *
 * Ohne sie landet das Wischen vom Rand beim Betriebssystem und beendet die
 * App, statt eine Ebene zurueckzugehen.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
