package com.jakober.matchday.data

import com.russhwolf.settings.Settings

/**
 * Schluessel-Wert-Ablage der Plattform: SharedPreferences auf Android,
 * NSUserDefaults auf iOS.
 */
expect fun createSettings(): Settings
