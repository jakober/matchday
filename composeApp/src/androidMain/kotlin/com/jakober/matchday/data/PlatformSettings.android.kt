package com.jakober.matchday.data

import android.content.Context
import com.jakober.matchday.MatchdayApp
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings

actual fun createSettings(): Settings {
    val prefs = MatchdayApp.appContext
        .getSharedPreferences("matchday", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
