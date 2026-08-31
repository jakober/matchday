package com.jakober.matchday.data.remote

/**
 * Zugang zum Backend.
 *
 * Der Schluessel ist ein "publishable key" und dafuer gemacht, in der App zu
 * stehen - er verschafft keinen Zugriff. Was jemand sehen und aendern darf,
 * entscheiden allein die Row-Level-Security-Regeln in der Datenbank:
 * lesen nur als Mitglied der Gruppe, schreiben nur die eigene Zusage.
 */
object SupabaseConfig {
    const val URL = "https://synhjexjxcwctcxcslpq.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_UMsHvLnTqZFlKznCJ2ewOQ_FHSeVHGG"
}
