package com.jakober.matchday

import com.jakober.matchday.data.MatchdayRepository
import com.jakober.matchday.data.MatchdayStore
import com.jakober.matchday.data.createSettings
import com.jakober.matchday.notify.BackgroundSync
import com.jakober.matchday.notify.ReminderPlanner
import com.jakober.matchday.notify.ReminderScheduler
import com.jakober.matchday.notify.createBackgroundSync
import com.jakober.matchday.notify.createReminderScheduler
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Zusammenbau der App. Bewusst schlicht statt mit einem
 * Abhaengigkeits-Container - die App hat vier bewegliche Teile.
 */
object Container {

    val store: MatchdayStore by lazy { MatchdayStore(createSettings()) }

    private val http: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    val repository: MatchdayRepository by lazy { MatchdayRepository(store, http) }

    val scheduler: ReminderScheduler by lazy { createReminderScheduler() }

    val backgroundSync: BackgroundSync by lazy { createBackgroundSync() }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Rechnet die Benachrichtigungen neu durch. Aufzurufen nach jeder Aenderung
     * an Spielplan, Zusagen oder Einstellungen - und beim App-Start, weil das
     * geplante Fenster mit der Zeit leerlaeuft.
     */
    fun rescheduleReminders() {
        val plan = ReminderPlanner.plan(
            matches = store.matches.value,
            rsvps = store.rsvps.value,
            settings = store.reminders.value,
            now = Clock.System.now(),
        )
        scheduler.replaceAll(plan)
    }

    /** Fragt die Benachrichtigungserlaubnis ab, ohne auf das Ergebnis zu warten. */
    fun requestNotificationPermission() {
        scope.launch { scheduler.ensurePermission() }
    }

    /** Abgleich aller Abos samt anschliessender Neuplanung. */
    fun syncAll(onDone: (List<String>) -> Unit = {}) {
        scope.launch {
            val errors = repository.syncAll()
            rescheduleReminders()
            onDone(errors)
        }
    }
}
