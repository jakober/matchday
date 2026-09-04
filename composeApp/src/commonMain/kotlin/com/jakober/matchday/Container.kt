package com.jakober.matchday

import com.jakober.matchday.i18n.S
import com.jakober.matchday.i18n.currentLocale
import com.jakober.matchday.data.FeedPreview
import com.jakober.matchday.data.MatchdayRepository
import com.jakober.matchday.data.MatchdayStore
import com.jakober.matchday.data.createSettings
import com.jakober.matchday.data.remote.GroupSnapshot
import com.jakober.matchday.data.remote.MatchdayBackend
import com.jakober.matchday.data.remote.splitMatchId
import com.jakober.matchday.domain.LogoEntry
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.notify.BackgroundSync
import com.jakober.matchday.notify.ReminderPlanner
import com.jakober.matchday.notify.ReminderScheduler
import com.jakober.matchday.notify.createBackgroundSync
import com.jakober.matchday.notify.createReminderScheduler
import com.jakober.matchday.push.PushRegistrar
import com.jakober.matchday.push.createPushRegistrar
import com.jakober.matchday.ui.components.AvatarColors
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
import kotlin.time.Duration.Companion.days

/** Nach dieser Frist wird ein Name ohne Wappen erneut nachgeschlagen. */
private val LOGO_RETRY = 30.days

/**
 * Zusammenbau der App. Bewusst schlicht statt mit einem
 * Abhaengigkeits-Container - die App hat vier bewegliche Teile.
 */
/** Zustand der Erreichbarkeit fuer Benachrichtigungen aus der Gruppe. */
enum class PushState { UNKNOWN, NO_GROUP, NO_TOKEN, UPLOAD_FAILED, REGISTERED }

/** Stand der Anmeldung. */
sealed interface AuthState {
    /** Gespeicherte Sitzung wird noch geladen. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** Konto angelegt, Adresse noch nicht bestaetigt. */
    data class AwaitingCode(val email: String) : AuthState
    /** Passwort vergessen: Code aus der Mail wird erwartet. */
    data class AwaitingRecoveryCode(val email: String) : AuthState
    /** Code eingeloest, jetzt ein neues Passwort waehlen. */
    data object NewPassword : AuthState
    data object SignedIn : AuthState
}

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

    private val _logos = MutableStateFlow(store.loadLogos())

    /** Wappen je Mannschaftsname, wie er im Kalender steht. */
    val logos: StateFlow<Map<String, LogoEntry>> = _logos.asStateFlow()

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

    // -- Konto --------------------------------------------------------------

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)

    /** Wo der Nutzer in der Anmeldung steht. Entscheidet, welcher Bildschirm zu sehen ist. */
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** Beim Start: gespeicherte Sitzung wiederherstellen oder zur Anmeldung. */
    fun startSession() {
        scope.launch {
            _auth.value = if (backend.restoreSession()) AuthState.SignedIn else AuthState.SignedOut
        }
    }

    /**
     * Registriert und wechselt zur Code-Eingabe. Name und Farbe werden
     * vorgemerkt und nach der Bestaetigung zum Profil - wird die App
     * dazwischen geschlossen, fragt der Einstieg danach noch einmal.
     */
    suspend fun signUp(name: String, email: String, password: String): Result<Unit> =
        runCatching { backend.signUp(email, password) }
            .onSuccess {
                pendingName = name
                _auth.value = AuthState.AwaitingCode(email)
            }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { backend.signIn(email, password) }
            .onSuccess { _auth.value = AuthState.SignedIn }
            .onFailure {
                // Konto vorhanden, Adresse nie bestaetigt: statt einer
                // Sackgasse gleich die Code-Eingabe, mit Neu-Senden.
                if (it.message == MatchdayBackend.UNCONFIRMED) {
                    _auth.value = AuthState.AwaitingCode(email)
                }
            }

    suspend fun confirmEmail(email: String, code: String): Result<Unit> =
        runCatching { backend.confirmEmail(email, code) }
            .onSuccess {
                pendingName?.let { name ->
                    if (store.profile.value == null) {
                        store.saveProfile(Profile(id = com.jakober.matchday.domain.newId(), name = name, colorArgb = AvatarColors.first()))
                    }
                }
                pendingName = null
                _auth.value = AuthState.SignedIn
            }

    suspend fun resendCode(email: String): Result<Unit> = runCatching { backend.resendCode(email) }

    /**
     * Passwort vergessen: Code per Mail, dann neues Passwort - alles in der
     * App. Der uebliche Weg ueber einen Link braeuchte eine Webseite, die es
     * fuer Matchday nicht gibt.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> =
        runCatching { backend.sendPasswordReset(email) }
            .onSuccess { _auth.value = AuthState.AwaitingRecoveryCode(email) }

    suspend fun confirmRecovery(email: String, code: String): Result<Unit> =
        runCatching { backend.verifyRecovery(email, code) }
            .onSuccess { _auth.value = AuthState.NewPassword }

    /** Nach dem Zuruecksetzen: neues Passwort setzen und drin sein. */
    suspend fun setNewPassword(password: String): Result<Unit> =
        runCatching { backend.updatePassword(password) }
            .onSuccess { _auth.value = AuthState.SignedIn }

    /** Aus den Einstellungen heraus, mit bestehender Sitzung. */
    suspend fun changePassword(password: String): Result<Unit> =
        runCatching { backend.updatePassword(password) }

    /** Zurueck zur Anmeldung - etwa wenn man sich in der Adresse vertippt hat. */
    fun cancelSignUp() {
        pendingName = null
        _auth.value = AuthState.SignedOut
    }

    /** Abmelden: Sitzung beenden und alles Lokale verwerfen. */
    fun signOut() {
        scope.launch {
            backend.signOut()
            store.clearAll()
            _group.value = GroupSnapshot()
            _logos.value = emptyMap()
            _pushState.value = PushState.UNKNOWN
            scheduler.replaceAll(emptyList())
            _auth.value = AuthState.SignedOut
        }
    }

    private var pendingName: String? = null

    // -- Gruppe anlegen und beitreten ---------------------------------------

    private val _groupBusy = MutableStateFlow(false)
    val groupBusy: StateFlow<Boolean> = _groupBusy.asStateFlow()

    /** Legt eine Gruppe an; das Profil liefert Name und Farbe des Mitglieds. */
    fun createGroup(groupName: String, profile: Profile, onError: (String) -> Unit) {
        groupAction(onError) {
            backend.createGroup(groupName, profile.name, profile.colorArgb)
        }
    }

    fun joinGroup(code: String, profile: Profile, onError: (String) -> Unit) {
        groupAction(onError) {
            backend.joinGroup(code, profile.name, profile.colorArgb)
        }
    }

    private fun groupAction(onError: (String) -> Unit, action: suspend () -> com.jakober.matchday.data.remote.GroupMembership) {
        _groupBusy.value = true
        acknowledgeMembershipLoss()
        scope.launch {
            runCatching { action() }
                .onSuccess { membership ->
                    store.saveMembership(membership)
                    // Was vor dem Beitritt lokal beantwortet wurde, soll die
                    // Gruppe auch sehen.
                    pushLocalRsvps()
                    uploadPushToken()
                    refreshGroup()
                    // Die Kalender der Gruppe uebernehmen und gleich laden.
                    refreshCalendars()
                    syncAll()
                }
                .onFailure { onError(it.message ?: S.failed) }
            _groupBusy.value = false
        }
    }

    /**
     * Holt Mitglieder und Zusagen der Gruppe. Fehler werden geschluckt: Ohne
     * Netz bleibt der letzte Stand stehen, das ist besser als eine leere Liste.
     */
    suspend fun refreshGroup() {
        val membership = store.membership.value ?: return
        // Ohne gueltiges Token antwortet die Datenbank mit leeren Listen statt
        // mit einem Fehler - das duerfen wir nicht fuer den neuen Stand halten.
        if (!backend.ensureFreshSession()) return

        runCatching {
            GroupSnapshot(
                members = backend.members(membership.groupId),
                rsvps = backend.rsvps(membership.groupId),
                // Die lokale Spiel-Id ist Kalender-Id plus Termin-UID - genau
                // die Felder, unter denen der Server die Markierung fuehrt.
                importantMatchIds = backend.importantMatches(membership.groupId)
                    .map { "${it.calendarId}#${it.matchUid}" }
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
            .onSuccess {
                store.saveMembership(it)
                // Sprache mitgeben - sie kann sich seit dem Beitritt geaendert haben.
                runCatching { backend.updateLocale(it, currentLocale) }
            }
    }

    /**
     * Holt die Kalender der Gruppe und uebernimmt sie in die Abo-Liste.
     *
     * Eine leere Antwort wird nur geglaubt, wenn die Mitgliedschaft
     * ausdruecklich bestaetigt ist: Ohne gueltige Anmeldung liefert die
     * Datenbank ebenfalls eine leere Liste, und die wuerde hier alle Abos
     * samt Spielen und Zusagen loeschen.
     */
    suspend fun refreshCalendars() {
        val membership = store.membership.value ?: return
        if (!backend.ensureFreshSession()) return

        val calendars = runCatching { backend.calendars(membership.groupId) }.getOrNull() ?: return
        if (calendars.isEmpty() && backend.isMemberOf(membership.groupId) != true) return

        store.mergeServerSubscriptions(
            calendars.map { cal ->
                com.jakober.matchday.domain.Subscription(
                    id = cal.id,
                    name = cal.name,
                    url = cal.url,
                    colorArgb = cal.color,
                    logoUrl = cal.logoUrl,
                )
            }
        )
    }

    /**
     * Schlaegt die Wappen aller Mannschaften nach, die noch keins haben.
     *
     * Haengt am Abgleich, nicht am Zeichnen: Ein Ligakalender hat achtzehn
     * Vereine, und die stehen in jeder Listenzeile - dort nachzufragen waere
     * dreistellig pro Bildschirm. So ist es eine Anfrage nach dem Import und
     * danach fast nie wieder.
     */
    suspend fun resolveLogos() {
        val names = store.matches.value
            .flatMap { listOfNotNull(it.homeTeam, it.awayTeam) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        // Fehlschlaege nach einer Weile erneut versuchen - der Dienst waechst.
        val retryBefore = Clock.System.now() - LOGO_RETRY
        val known = _logos.value
        val missing = names.filter { name ->
            val entry = known[name]
            entry == null || (entry.url == null && entry.checkedAt < retryBefore)
        }
        if (missing.isEmpty()) return
        if (!backend.ensureFreshSession()) return

        // In Haeppchen: Der Server nimmt hoechstens 50 Namen je Aufruf, ein
        // Ligakalender bringt mehr mit - sonst blieben die uebrigen bis zum
        // naechsten Abgleich ohne Wappen.
        var merged = known
        for (batch in missing.chunked(50).take(4)) {
            val found = runCatching { backend.resolveLogos(batch) }.getOrNull() ?: break
            val now = Clock.System.now()
            merged = merged + found.mapValues { (_, url) -> LogoEntry(url, now) }
            _logos.value = merged
            store.saveLogos(merged)
        }
    }

    /** Laedt einen Kalender probeweise, ohne etwas zu speichern. */
    suspend fun previewCalendar(url: String): Result<FeedPreview> = repository.preview(url)

    /**
     * Legt einen Kalender in der Gruppe an und laedt ihn sofort, damit die
     * Spiele nicht erst beim naechsten Abgleich auftauchen.
     *
     * Die Datenbank prueft, dass nur der Admin das darf und dass dieselbe
     * Adresse nicht zweimal in der Gruppe landet.
     */
    suspend fun importCalendar(name: String, url: String, colorArgb: Long): Result<Unit> {
        val membership = store.membership.value
            ?: return Result.failure(IllegalStateException(S.needGroup))

        return runCatching {
            backend.ensureFreshSession()
            backend.addCalendar(membership, name, url, colorArgb)
        }.mapCatching { created ->
            refreshCalendars()
            store.subscriptions.value.firstOrNull { it.id == created.id }?.let { repository.sync(it) }
            rescheduleReminders()
            resolveLogos()
        }.recoverCatching { e ->
            val message = e.message.orEmpty()
            throw IllegalStateException(
                when {
                    "23505" in message || "duplicate" in message.lowercase() ->
                        S.calendarExists
                    "42501" in message || "row-level security" in message ->
                        S.onlyAdminCalendars
                    else -> S.addFailed(message)
                }
            )
        }
    }

    /** Entfernt einen Kalender aus der Gruppe; die Abo-Liste zieht nach. */
    fun removeCalendar(calendarId: String, onError: (String) -> Unit = {}) {
        scope.launch {
            runCatching {
                backend.ensureFreshSession()
                backend.removeCalendar(calendarId)
            }
                .onSuccess {
                    refreshCalendars()
                    rescheduleReminders()
                }
                .onFailure { onError(it.message ?: S.removeFailed) }
        }
    }

    fun toggleImportant(matchId: String, onError: (String) -> Unit = {}) {
        val parts = splitMatchId(matchId)
        if (parts == null) {
            onError(S.matchUnknown)
            return
        }
        if (store.membership.value == null) {
            onError(S.needGroup)
            return
        }

        val isImportant = matchId in _group.value.importantMatchIds
        val title = store.matches.value.firstOrNull { it.id == matchId }?.displayTitle

        scope.launch {
            val membership = store.membership.value ?: return@launch
            val calendarId = parts.first

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
                onError(it.message ?: S.changeFailed)
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
        val calendarId = parts.first
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

    private val _resumeTick = MutableStateFlow(0)

    /**
     * Zaehlt hoch, sobald die App in den Vordergrund kommt. Die Oberflaeche
     * haengt Pruefungen daran, die sich ausserhalb der App aendern koennen -
     * etwa ob exakte Alarme inzwischen erlaubt sind, nachdem der Nutzer aus
     * den Systemeinstellungen zurueckkommt.
     */
    val resumeTick: StateFlow<Int> = _resumeTick.asStateFlow()

    /** Von beiden Plattformen bei Rueckkehr in den Vordergrund aufgerufen. */
    fun onResume() {
        if (foregroundSince == null) foregroundSince = Clock.System.now()
        _resumeTick.value += 1
        refreshGroupInBackground()
    }

    /** Beim Verlassen des Vordergrunds: die Nutzungszeit festhalten. */
    fun onPause() {
        val since = foregroundSince ?: return
        val elapsed = (Clock.System.now() - since).inWholeSeconds
        store.saveUsageSeconds(store.usageSeconds + elapsed)
        foregroundSince = null
    }

    private var foregroundSince: kotlinx.datetime.Instant? = null

    /**
     * Wie lange die App insgesamt benutzt wurde. Hinweise, die beim ersten
     * Start nur stoeren wuerden, haengen daran - etwa der auf exakte Alarme.
     */
    fun usageSeconds(): Long {
        val current = foregroundSince?.let { (Clock.System.now() - it).inWholeSeconds } ?: 0L
        return store.usageSeconds + current
    }

    /** Entfernt ein Mitglied und laedt die Gruppe neu. */
    fun removeMember(memberId: String, onError: (String) -> Unit = {}) {
        scope.launch {
            runCatching { backend.removeMember(memberId) }
                .onSuccess { refreshGroup() }
                .onFailure { onError(it.message ?: S.removeFailed) }
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
                val calendarId = parts.first
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
            // Der Admin koennte inzwischen einen Kalender hinzugefuegt haben.
            refreshCalendars()
            val errors = repository.syncAll()
            rescheduleReminders()
            resolveLogos()
            refreshGroup()
            onDone(errors)
        }
    }
}
