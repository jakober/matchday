package com.jakober.matchday.domain

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Kurze, eindeutige Id aus Zeitstempel und Zufall. Reicht fuer lokale
 * Objekte; sobald das Backend dazukommt, vergibt die Datenbank die Ids.
 */
fun newId(): String {
    val stamp = Clock.System.now().toEpochMilliseconds().toString(36)
    val noise = Random.nextInt(0, 1 shl 24).toString(36)
    return "$stamp$noise"
}
