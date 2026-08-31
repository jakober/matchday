package com.jakober.matchday.notify

/**
 * Regelmaessiger Abgleich der Spielplaene im Hintergrund.
 *
 * Warum das noetig ist: Die Erinnerungen werden im Voraus beim Betriebssystem
 * vorgemerkt. Verlegt ein Verein eine Anstosszeit, ist die vorgemerkte
 * Erinnerung falsch, bis die App die Aenderung mitbekommt. Ohne
 * Hintergrundabgleich passiert das erst beim naechsten Oeffnen - womoeglich
 * nach dem Spiel.
 *
 * Was die Plattformen zusagen, ist unterschiedlich:
 * - Android fuehrt die Arbeit ueber WorkManager zuverlaessig aus, der Zeitpunkt
 *   kann sich aber verschieben.
 * - iOS entscheidet selbst, ob und wann es die App weckt. Der Abgleich ist dort
 *   eine Gelegenheit, keine Garantie - deshalb wird zusaetzlich bei jedem
 *   Oeffnen der App abgeglichen.
 */
interface BackgroundSync {
    /** Richtet den wiederkehrenden Abgleich ein. Mehrfachaufruf ist harmlos. */
    fun schedulePeriodic()
}

expect fun createBackgroundSync(): BackgroundSync

/** Abstand zwischen zwei Abgleichen. */
const val SYNC_INTERVAL_HOURS = 6L
