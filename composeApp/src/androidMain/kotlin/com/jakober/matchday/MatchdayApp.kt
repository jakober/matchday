package com.jakober.matchday

import android.app.Application
import android.content.Context

/**
 * Haelt einen Anwendungskontext fuer die Plattformschichten bereit
 * (Einstellungen, Benachrichtigungen), die in commonMain keinen Kontext bekommen.
 */
class MatchdayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
