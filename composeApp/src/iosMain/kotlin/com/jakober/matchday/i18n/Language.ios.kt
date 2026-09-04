package com.jakober.matchday.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** Erste bevorzugte Sprache, etwa "en-US" - nur das Kuerzel davor zaehlt. */
actual fun deviceLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.take(2) ?: "de"
