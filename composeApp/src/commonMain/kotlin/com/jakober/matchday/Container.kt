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
import com.jakober.matchday.push.PushRegistrar
import com.jakober.matchday.push.createPushRegistrar
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
/** Zustand der Erreichbarkeit fuer Benachrichtigungen aus der Gruppe. */
enum class PushState { UNKNOWN, NO_GROUP, NO_TOKEN, UPLOAD_FAILED, REGISTERED }

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

    private val pushRegistrar: PushRegistrar by lazy { createPushRegistrar() }

    val backend: MatchdayBackend by lazy { MatchdayBackend() }

    // Aus der Ablage vorbelegt: Beim Start steht damit sofort der letzte
    // bekannte Stand da, statt einer leeren Gruppe, die sich erst nach der
    // Abfrage fuellt.
    private val _group = MutableStateFlow(store.loadGroupSnapshot())

    /** Mitglieder und deren Antworten, Stand des letzten Abgleichs. */
    val group: StateFlow<GroupSnapshot> = _group.asStateFlow()

    private val _pushState = MutableStateFlow(PushState.UNKNOWN)

    /** Ob dieses Geraet fuer Benachrichtigungen der anderen erreichbar ist. */
    val pushState: StateFlow<PushState> = _pushState.asStateFlow()

    private val _membershipLost = MutableStateFlow(false)

    /** Wird gesetzt, wenn die gespeicherte Gruppe nicht mehr zu dieser App-Installation gehoert. */
    val membershipLost: StateFlow<Boolean> = _membershipLost.asStateFlow()

    fun acknowledgeMembershipLoss() {
        _membershipLost.value = false
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Holt Mitglieder und Zusagen der Gruppe. Fehler werden geschluckt: Ohne
     * Netz bleibt der letzte Stand stehen, das ist besser als eine leere Liste.
     */
    suspend fun refreshGroup() {
        val membership = store.membership.value ?: return
        // Ohne gueltiges Token antwortet die Datenbank mit leeren Listen statt
        // mit einem Fehler - das duerfen wir nicht fuer den neuen Stand halten.
        if (!backend.ensureFreshSession()) return
        // Kalender-Id zurueck auf die Mannschaft abbilden, damit sich die
        // Markierungen den lokalen Spielen zuordnen lassen.
        val teamByCalendar = membership.calendarIds.entries.associate { (team, cal) -> cal to team }

        runCatching {
            GroupSnapshot(
                members = backend.members(membership.groupId),
                rsvps = backend.rsvps(membership.groupId),
                importantMatchIds = backend.importantMatches(membership.groupId)
                    .mapNotNull { entry ->
                        teamByCalendar[entry.calendarId]?.let { team -> "$team#${entry.matchUid}" }
                    }
                    .toSet(),
            )
        }.onSuccess { fresh ->
            // Eine Gruppe ohne Mitglieder gibt es nicht - man selbst ist immer
            // darin. Eine leere Antwort ist deshalb kein Stand, sondern eine
            // Frage, die nicht beantwortet wurde. Den alten Stand stehen zu
            // lassen ist in jedem Fall richtiger, als alles zu verwerfen.
            if (fresh.members.isEmpty()) return@onSuccess
            _group.value = fresh
            store.saveGroupSnapshot(fresh)
        }
    }

    /**
     * Hebt ein Spiel hervor oder nimmt die Hervorhebung zurueck.
     * Nur der Admin darf das; die Datenbank weist alle anderen ab.
     */
    /**
     * Holt die eigene Mitgliedschaft neu vom Server und legt sie ab.
     *
     * Wichtig fuer Installationen, die aus einer aelteren App-Fassung stammen:
     * Dort fehlen Adminrolle, Sichtbarkeit und teils die Kalenderzuordnung,
     * weil es diese Felder damals noch nicht gab.
     */
    suspend fun refreshMembership() {
        val current = store.membership.value ?: return

        // Gehoert die gespeicherte Gruppe noch zu dieser Anmeldung? Nach einer
        // Neuinstallation ist die anonyme Kennung eine andere, und der
        // Mitgliedseintrag gehoert dann jemand anderem. Ohne diese Pruefung
        // bliebe eine Gruppe stehen, in der man nichts mehr darf - und der
        // Grund waere nicht erkennbar.
        // Nur bei einem eindeutigen Nein loesen. Bei "unbekannt" - kein Netz,
        // Sitzung noch nicht geladen - bleibt die Gruppe unangetastet. Alles
        // andere waere Datenverlust aus Unwissenheit.
        val stillMember = backend.isMemberOf(current.groupId)
        if (stillMember == false) {
            store.clearMembership()
            clearGroupSnapshot()
            _membershipLost.value = true
            return
        }

        runCatching { backend.reloadMembership(current) }
            .onSuccess { store.saveMembership(it) }
    }

    fun toggleImportant(matchId: String, onError: (String) -> Unit = {}) {
        val parts = splitMatchId(matchId)
        if (parts == null) {
            onError("Spiel konnte nicht zugeordnet werden")
            return
        }
        if (store.membership.value == null) {
            onError("Dafür brauchst du eine Gruppe")
            return
        }

        val isImportant = matchId in _group.value.importantMatchIds
        val title = store.matches.value.firstOrNull { it.id == matchId }?.displayTitle

        scope.launch {
            // Fehlt die Kalenderzuordnung, stammt die gespeicherte
            // Mitgliedschaft aus einer aelteren Fassung - einmal nachladen
            // repariert das, statt wortlos nichts zu tun.
            if (store.membership.value?.calendarIds?.containsKey(parts.first) != true) {
                refreshMembership()
            }

            val membership = store.membership.value
            val calendarId = membership?.calendarIds?.get(parts.first)
            if (membership == null || calendarId == null) {
                onError("Kalender der Gruppe nicht gefunden - bitte Gruppe neu betreten")
                return@launch
            }

            runCatching {
                if (isImportant) {
                    backend.unmarkImportant(membership, calendarId, parts.second)
                } else {
                    backend.markImportant(membership, calendarId, parts.second, title)
                }
                refreshGroup()
                // Die Erinnerungen haengen an der Sichtbarkeit: Fuer ein
                // eingeschraenktes Mitglied aendert eine Markierung, ob es zu
                // diesem Spiel ueberhaupt erinnert wird.
                rescheduleReminders()
            }.onFailure {
                onError(it.message ?: "Ändern nicht möglich")
            }
        }
    }

    /**
     * Spiele, die der Nutzer sehen darf. Eingeschraenkte Mitglieder bekommen
     * nur die hervorgehobenen.
     *
     * Das ist Aufraeumen, keine Absperrung: Die Spielplaene stammen aus einem
     * oeffentlichen Kalenderfeed. Abgesichert sind dagegen die
     * Benachrichtigungen - darueber entscheidet der Server.
     */
    fun visibleMatches(all: List<com.jakober.matchday.domain.Match>): List<com.jakober.matchday.domain.Match> {
        val membership = store.membership.value ?: return all
        if (!membership.seesOnlyImportant) return all
        val important = _group.value.importantMatchIds
        return all.filter { it.id in important }
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
        // Der Titel wandert mit in die Datenbank, damit die Benachrichtigung
        // an die anderen sagen kann, um welches Spiel es geht.
        val title = store.matches.value.firstOrNull { it.id == matchId }?.displayTitle

        scope.launch {
            runCatching {
                if (status == RsvpStatus.UNDECIDED) {
                    backend.clearRsvp(membership, calendarId, parts.second)
                } else {
                    backend.setRsvp(membership, calendarId, parts.second, status, comment, title)
                    // Erst speichern, dann melden: Die Function liest die
                    // Antwort aus der Datenbank, sie muss also schon dort sein.
                    backend.notifyGroup(calendarId, parts.second)
                }
                refreshGroup()
            }
        }
    }

    /**
     * Hinterlegt die Push-Kennung dieses Geraets in der Gruppe.
     *
     * Bei jedem Start erneut: Kennungen aendern sich bei Neuinstallation oder
     * Wiederherstellung aus einem Backup, und eine veraltete Kennung fuehrt
     * dazu, dass Benachrichtigungen stillschweigend ins Leere gehen.
     */
    fun uploadPushToken() {
        val membership = store.membership.value
        if (membership == null) {
            _pushState.value = PushState.NO_GROUP
            return
        }
        scope.launch {
            val token = runCatching { pushRegistrar.token() }.getOrNull()
            if (token == null) {
                // Auf iOS heisst das meist: Die Anmeldung bei Apple ist
                // fehlgeschlagen, etwa weil der App die Push-Berechtigung
                // fehlt. Ohne Kennung gibt es keinen Empfaenger.
                _pushState.value = PushState.NO_TOKEN
                return@launch
            }
            runCatching { backend.upsertDeviceToken(membership, token) }
                .onSuccess { _pushState.value = PushState.REGISTERED }
                .onFailure { _pushState.value = PushState.UPLOAD_FAILED }
        }
    }

    /**
     * Stoesst einen Abgleich an, ohne auf das Ergebnis zu warten.
     *
     * Gedacht fuer Ausloeser von aussen: eine eintreffende Benachrichtigung
     * oder die Rueckkehr in die App. Trifft eine Meldung ein, hat sich in der
     * Gruppe etwas geaendert - dann soll die offene Ansicht das auch zeigen,
     * ohne dass jemand den Aktualisieren-Knopf sucht.
     */
    fun refreshGroupInBackground() {
        scope.launch { refreshGroup() }
    }

    /** Entfernt ein Mitglied und laedt die Gruppe neu. */
    fun removeMember(memberId: String, onError: (String) -> Unit = {}) {
        scope.launch {
            runCatching { backend.removeMember(memberId) }
                .onSuccess { refreshGroup() }
                .onFailure { onError(it.message ?: "Entfernen nicht möglich") }
        }
    }

    /** Nach dem Verlassen der Gruppe den zwischengespeicherten Stand leeren. */
    fun clearGroupSnapshot() {
        _group.value = GroupSnapshot()
        store.clearGroupSnapshot()
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
                val title = store.matches.value.firstOrNull { it.id == matchId }?.displayTitle
                runCatching {
                    backend.setRsvp(
                        membership, calendarId, parts.second, rsvp.status, rsvp.comment, title,
                    )
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
            // Eingeschraenkte Mitglieder werden nur zu hervorgehobenen Spielen
            // erinnert - sonst kaeme eine Erinnerung zu einem Spiel, das sie
            // in der Liste gar nicht sehen.
            matches = visibleMatches(store.matches.value),
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
