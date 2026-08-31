package com.jakober.matchday

import com.jakober.matchday.data.MatchdayRepository
import com.jakober.matchday.data.MatchdayStore
import com.jakober.matchday.data.createSettings
import com.jakober.matchday.data.remote.GroupSnapshot
import com.jakober.matchday.data.remote.MatchdayBackend
import com.jakober.matchday.data.remote.splitMatchId
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.RsvpStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val backend: MatchdayBackend by lazy { MatchdayBackend() }

    private val _group = MutableStateFlow(GroupSnapshot())

    /** Mitglieder und deren Antworten, Stand des letzten Abgleichs. */
    val group: StateFlow<GroupSnapshot> = _group.asStateFlow()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Holt Mitglieder und Zusagen der Gruppe. Fehler werden geschluckt: Ohne
     * Netz bleibt der letzte Stand stehen, das ist besser als eine leere Liste.
     */
    suspend fun refreshGroup() {
        val membership = store.membership.value ?: return
        runCatching {
            GroupSnapshot(
                members = backend.members(membership.groupId),
                rsvps = backend.rsvps(membership.groupId),
            )
        }.onSuccess { _group.value = it }
    }

    /**
     * Setzt die eigene Antwort: erst lokal, damit sie sofort steht und die
     * Erinnerungen stimmen, dann im Hintergrund in die Datenbank.
     */
    fun setRsvp(matchId: String, status: RsvpStatus, comment: String?) {
        store.setRsvp(matchId, status, comment)
        rescheduleReminders()

        val membership = store.membership.value ?: return
        val parts = splitMatchId(matchId) ?: return
        val calendarId = membership.calendarIds[parts.first] ?: return

        scope.launch {
            runCatching {
                if (status == RsvpStatus.UNDECIDED) {
                    backend.clearRsvp(membership, calendarId, parts.second)
                } else {
                    backend.setRsvp(membership, calendarId, parts.second, status, comment)
                }
                refreshGroup()
            }
        }
    }

    /** Nach dem Verlassen der Gruppe den zwischengespeicherten Stand leeren. */
    fun clearGroupSnapshot() {
        _group.value = GroupSnapshot()
    }

    /**
     * Schiebt alle lokal beantworteten Spiele in die Gruppe.
     *
     * Noetig direkt nach dem Beitritt: Wer vorher schon zugesagt hat, waere
     * fuer die anderen sonst unsichtbar, bis er jede Antwort erneut antippt.
     */
    fun pushLocalRsvps() {
        val membership = store.membership.value ?: return
        val local = store.rsvps.value
        if (local.isEmpty()) return

        scope.launch {
            for ((matchId, rsvp) in local) {
                val parts = splitMatchId(matchId) ?: continue
                val calendarId = membership.calendarIds[parts.first] ?: continue
                runCatching {
                    backend.setRsvp(membership, calendarId, parts.second, rsvp.status, rsvp.comment)
                }
            }
            refreshGroup()
        }
    }

    /** Name und Farbe auch in der Gruppe nachziehen. */
    fun saveProfile(profile: Profile) {
        store.saveProfile(profile)
        val membership = store.membership.value ?: return
        scope.launch {
            runCatching { backend.updateProfile(membership, profile.name, profile.colorArgb) }
            refreshGroup()
        }
    }

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
            refreshGroup()
            onDone(errors)
        }
    }
}
