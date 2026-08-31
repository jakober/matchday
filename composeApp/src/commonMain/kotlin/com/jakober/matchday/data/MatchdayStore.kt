package com.jakober.matchday.data

import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Haelt den gesamten Zustand der App und schreibt ihn bei jeder Aenderung
 * zurueck in die Plattform-Ablage.
 *
 * Bewusst ohne Datenbank: Ein Spielplan umfasst ein paar hundert Eintraege,
 * die als JSON zu halten ist einfacher als ein Schema samt Migrationen.
 * Sollte die Datenmenge einmal wachsen, ist hier die Stelle zum Austauschen.
 */
class MatchdayStore(private val settings: Settings) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _profile = MutableStateFlow(load<Profile>(KEY_PROFILE))
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _subscriptions = MutableStateFlow(loadOrSeedSubscriptions())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _matches = MutableStateFlow(loadList<Match>(KEY_MATCHES))
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _rsvps = MutableStateFlow(loadRsvps())
    val rsvps: StateFlow<Map<String, RsvpStatus>> = _rsvps.asStateFlow()

    private val _reminders = MutableStateFlow(load<ReminderSettings>(KEY_REMINDERS) ?: ReminderSettings())
    val reminders: StateFlow<ReminderSettings> = _reminders.asStateFlow()

    // -- Profil -------------------------------------------------------------

    fun saveProfile(profile: Profile) {
        _profile.value = profile
        settings.putString(KEY_PROFILE, json.encodeToString(profile))
    }

    // -- Abos ---------------------------------------------------------------

    /**
     * Beim allerersten Start werden die fest hinterlegten Mannschaften
     * eingetragen. Der Merker verhindert, dass sie wieder auftauchen, wenn der
     * Nutzer beide abwaehlt.
     */
    private fun loadOrSeedSubscriptions(): List<Subscription> {
        val stored = loadList<Subscription>(KEY_SUBSCRIPTIONS)
        if (stored.isNotEmpty() || settings.getBoolean(KEY_SEEDED, false)) return stored
        val seeded = TeamCatalog.defaultSubscriptions()
        settings.putBoolean(KEY_SEEDED, true)
        settings.putString(KEY_SUBSCRIPTIONS, json.encodeToString(seeded))
        return seeded
    }

    /** Schaltet eine Mannschaft an oder ab. */
    fun setSubscriptionEnabled(id: String, enabled: Boolean) {
        updateSubscriptions(
            _subscriptions.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        )
        if (!enabled) {
            // Spiele einer abgewaehlten Mannschaft verschwinden aus Liste und
            // Erinnerungsplanung. Die Zusagen bleiben erhalten - schaltet man
            // sie wieder ein, stehen sie wieder da, weil die Spiel-Id gleich
            // bleibt.
            updateMatches(_matches.value.filterNot { it.subscriptionId == id })
        }
    }

    fun addSubscription(subscription: Subscription) {
        // Dieselbe Adresse nicht doppelt aufnehmen.
        if (_subscriptions.value.any { it.url.equals(subscription.url, ignoreCase = true) }) return
        updateSubscriptions(_subscriptions.value + subscription)
    }

    fun removeSubscription(id: String) {
        updateSubscriptions(_subscriptions.value.filterNot { it.id == id })
        // Die Spiele des Abos verschwinden mit; Zusagen dazu ebenso.
        val remaining = _matches.value.filterNot { it.subscriptionId == id }
        val removedIds = _matches.value.filter { it.subscriptionId == id }.map { it.id }.toSet()
        updateMatches(remaining)
        updateRsvps(_rsvps.value - removedIds)
    }

    fun updateSubscriptions(list: List<Subscription>) {
        _subscriptions.value = list
        settings.putString(KEY_SUBSCRIPTIONS, json.encodeToString(list))
    }

    // -- Spiele -------------------------------------------------------------

    /**
     * Ersetzt die Spiele genau eines Abos und laesst die anderen unberuehrt,
     * damit ein fehlgeschlagener Sync eines Feeds nicht die uebrigen leert.
     */
    fun replaceMatchesOf(subscriptionId: String, fresh: List<Match>) {
        val others = _matches.value.filterNot { it.subscriptionId == subscriptionId }
        updateMatches((others + fresh).sortedBy { it.start })
    }

    private fun updateMatches(list: List<Match>) {
        _matches.value = list
        settings.putString(KEY_MATCHES, json.encodeToString(list))
    }

    // -- Zusagen ------------------------------------------------------------

    fun setRsvp(matchId: String, status: RsvpStatus) {
        // UNDECIDED bedeutet "zurueckgenommen" und wird nicht gespeichert -
        // damit greift wieder die Wochen-Erinnerung.
        val next = if (status == RsvpStatus.UNDECIDED) {
            _rsvps.value - matchId
        } else {
            _rsvps.value + (matchId to status)
        }
        updateRsvps(next)
    }

    fun rsvpOf(matchId: String): RsvpStatus = _rsvps.value[matchId] ?: RsvpStatus.UNDECIDED

    private fun updateRsvps(map: Map<String, RsvpStatus>) {
        _rsvps.value = map
        settings.putString(KEY_RSVPS, json.encodeToString(map.mapValues { it.value.name }))
    }

    // -- Erinnerungen -------------------------------------------------------

    fun saveReminders(value: ReminderSettings) {
        _reminders.value = value
        settings.putString(KEY_REMINDERS, json.encodeToString(value))
    }

    // -- Laden --------------------------------------------------------------

    private inline fun <reified T> load(key: String): T? {
        val raw = settings.getStringOrNull(key) ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private inline fun <reified T> loadList(key: String): List<T> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadRsvps(): Map<String, RsvpStatus> {
        val raw = settings.getStringOrNull(KEY_RSVPS) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
                .mapNotNull { (id, name) ->
                    runCatching { id to RsvpStatus.valueOf(name) }.getOrNull()
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_PROFILE = "profile"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_MATCHES = "matches"
        const val KEY_RSVPS = "rsvps"
        const val KEY_REMINDERS = "reminders"
        const val KEY_SEEDED = "subscriptions_seeded"
    }
}

