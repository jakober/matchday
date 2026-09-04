package com.jakober.matchday.data

import com.jakober.matchday.data.remote.GroupMembership
import com.jakober.matchday.data.remote.GroupSnapshot
import com.jakober.matchday.domain.LogoEntry
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.ReminderSettings
import com.jakober.matchday.domain.Rsvp
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

    private val _subscriptions = MutableStateFlow(loadList<Subscription>(KEY_SUBSCRIPTIONS))
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _matches = MutableStateFlow(loadList<Match>(KEY_MATCHES))
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _rsvps = MutableStateFlow(loadRsvps())
    val rsvps: StateFlow<Map<String, Rsvp>> = _rsvps.asStateFlow()

    private val _reminders = MutableStateFlow(load<ReminderSettings>(KEY_REMINDERS) ?: ReminderSettings())
    val reminders: StateFlow<ReminderSettings> = _reminders.asStateFlow()

    private val _membership = MutableStateFlow(load<GroupMembership>(KEY_MEMBERSHIP))
    val membership: StateFlow<GroupMembership?> = _membership.asStateFlow()

    // -- Gruppe ---------------------------------------------------------------

    fun saveMembership(value: GroupMembership) {
        _membership.value = value
        settings.putString(KEY_MEMBERSHIP, json.encodeToString(value))
    }

    /** Letzter bekannter Stand der Gruppe, damit der Start nicht leer beginnt. */
    fun loadGroupSnapshot(): GroupSnapshot = load<GroupSnapshot>(KEY_SNAPSHOT) ?: GroupSnapshot()

    fun saveGroupSnapshot(value: GroupSnapshot) {
        settings.putString(KEY_SNAPSHOT, json.encodeToString(value))
    }

    fun clearGroupSnapshot() {
        settings.remove(KEY_SNAPSHOT)
    }

    /** Gruppe verlassen - nur lokal, der Eintrag in der Datenbank bleibt. */
    fun clearMembership() {
        _membership.value = null
        settings.remove(KEY_MEMBERSHIP)
    }

    /**
     * Beim Abmelden: alles weg, was zu diesem Konto gehoert. Auf demselben
     * Geraet kann sich als naechstes jemand anderes anmelden - der darf weder
     * fremde Zusagen noch eine fremde Gruppe vorfinden.
     */
    fun clearAll() {
        _membership.value = null
        _subscriptions.value = emptyList()
        _matches.value = emptyList()
        _rsvps.value = emptyMap()
        _profile.value = null
        for (key in listOf(KEY_MEMBERSHIP, KEY_SUBSCRIPTIONS, KEY_MATCHES, KEY_RSVPS, KEY_PROFILE, KEY_SNAPSHOT, KEY_LOGOS)) {
            settings.remove(key)
        }
    }

    // -- Profil -------------------------------------------------------------

    fun saveProfile(profile: Profile) {
        _profile.value = profile
        settings.putString(KEY_PROFILE, json.encodeToString(profile))
    }

    // -- Abos ---------------------------------------------------------------

    /**
     * Uebernimmt die Kalender der Gruppe in die lokale Abo-Liste.
     *
     * Der Server bestimmt, welche Kalender es gibt, wie sie heissen und
     * aussehen. Ob ein Abo auf diesem Geraet eingeschaltet ist und wann es
     * zuletzt geladen wurde, bleibt lokal - sonst schaltete jeder Abgleich die
     * Abwahl eines Mitglieds wieder ein.
     *
     * Die Liste bleibt lokal gespeichert, statt bei Bedarf abgefragt zu werden:
     * Der Hintergrundabgleich laeuft ohne Sitzungspruefung und saehe bei
     * abgelaufenem Token sonst "keine Abos".
     */
    fun mergeServerSubscriptions(server: List<Subscription>) {
        val local = _subscriptions.value.associateBy { it.id }

        // Was der Admin entfernt hat, verschwindet samt Spielen und Zusagen -
        // die Zusagen dazu gibt es serverseitig ohnehin nicht mehr.
        val serverIds = server.map { it.id }.toSet()
        for (id in local.keys - serverIds) removeSubscription(id)

        updateSubscriptions(
            server.map { remote ->
                val known = local[remote.id]
                if (known == null) remote
                else remote.copy(enabled = known.enabled, lastSyncedAt = known.lastSyncedAt)
            }
        )
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

    fun setRsvp(matchId: String, status: RsvpStatus, comment: String? = null) {
        // UNDECIDED bedeutet "zurueckgenommen" und wird nicht gespeichert -
        // damit greift wieder die Wochen-Erinnerung.
        val next = if (status == RsvpStatus.UNDECIDED) {
            _rsvps.value - matchId
        } else {
            _rsvps.value + (matchId to Rsvp(status, comment?.trim()?.ifEmpty { null }))
        }
        updateRsvps(next)
    }

    fun rsvpOf(matchId: String): Rsvp? = _rsvps.value[matchId]

    private fun updateRsvps(map: Map<String, Rsvp>) {
        _rsvps.value = map
        settings.putString(KEY_RSVPS, json.encodeToString(map))
    }

    // -- Wappen -------------------------------------------------------------

    /** Nachgeschlagene Wappen je Mannschaftsname, wie er im Kalender steht. */
    fun loadLogos(): Map<String, LogoEntry> {
        val raw = settings.getStringOrNull(KEY_LOGOS) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, LogoEntry>>(raw) }.getOrDefault(emptyMap())
    }

    fun saveLogos(map: Map<String, LogoEntry>) {
        settings.putString(KEY_LOGOS, json.encodeToString(map))
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

    /**
     * Liest die Antworten. Bis Version 0.3 stand hier nur der Statusname je
     * Spiel; seit dem Kommentarfeld ist es ein Objekt. Die alte Form wird noch
     * gelesen, damit vorhandene Zusagen ein Update ueberleben.
     */
    private fun loadRsvps(): Map<String, Rsvp> {
        val raw = settings.getStringOrNull(KEY_RSVPS) ?: return emptyMap()

        runCatching { json.decodeFromString<Map<String, Rsvp>>(raw) }
            .onSuccess { return it }

        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
                .mapNotNull { (id, name) ->
                    runCatching { id to Rsvp(RsvpStatus.valueOf(name)) }.getOrNull()
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
        const val KEY_MEMBERSHIP = "membership"
        const val KEY_SNAPSHOT = "group_snapshot"
        const val KEY_LOGOS = "team_logos"
    }
}

