package com.jakober.matchday

import androidx.compose.runtime.Composable
import com.jakober.matchday.ui.components.SystemBackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.jakober.matchday.data.remote.participantsOfMatch
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.notify.NotificationDiagnostics
import com.jakober.matchday.theme.MatchdayTheme
import com.jakober.matchday.theme.Pitch
import com.jakober.matchday.ui.detail.MatchDetailSheet
import com.jakober.matchday.ui.group.GroupScreen
import com.jakober.matchday.ui.home.HomeScreen
import com.jakober.matchday.ui.home.HomeView
import com.jakober.matchday.ui.onboarding.OnboardingScreen
import com.jakober.matchday.ui.settings.SettingsScreen
import com.jakober.matchday.ui.subs.TeamsScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private enum class Screen { HOME, TEAMS, SETTINGS, GROUP }

@Composable
fun App() {
    // Wappen werden ueber das Netz geladen; Coil braucht dafuer auf beiden
    // Plattformen den Ktor-Lader. Der Zwischenspeicher sorgt dafuer, dass das
    // nur einmal passiert.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    MatchdayTheme {
        val profile by Container.store.profile.collectAsState()

        if (profile == null) {
            OnboardingScreen(
                onDone = { newProfile ->
                    Container.store.saveProfile(newProfile)
                    // Beim ersten Start gleich nach der Erlaubnis fragen -
                    // ohne sie waeren alle Erinnerungen wirkungslos.
                    Container.requestNotificationPermission()
                },
            )
        } else {
            Root()
        }
    }
}

@Composable
private fun Root() {
    val profile by Container.store.profile.collectAsState()
    val allMatches by Container.store.matches.collectAsState()
    val rsvps by Container.store.rsvps.collectAsState()
    val subscriptions by Container.store.subscriptions.collectAsState()
    val reminders by Container.store.reminders.collectAsState()
    val membership by Container.store.membership.collectAsState()
    val groupSnapshot by Container.group.collectAsState()
    val membershipLost by Container.membershipLost.collectAsState()
    val pushState by Container.pushState.collectAsState()

    val activeProfile = profile ?: return
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var view by remember { mutableStateOf(HomeView.LIST) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var groupBusy by remember { mutableStateOf(false) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var invite by remember { mutableStateOf<Pair<String, String>?>(null) }
    var importantError by remember { mutableStateOf<String?>(null) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<NotificationDiagnostics?>(null) }

    // Beim Oeffnen der Einstellungen neu erheben - die Erlaubnis kann
    // zwischenzeitlich in den Systemeinstellungen geaendert worden sein.
    LaunchedEffect(screen, rsvps) {
        if (screen == Screen.SETTINGS) {
            diagnostics = runCatching { Container.scheduler.diagnostics() }.getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        Container.scheduler.ensurePermission()
        // Wiederkehrenden Abgleich einrichten, damit verlegte Anstosszeiten
        // auch ankommen, wenn die App laenger nicht geoeffnet wird.
        Container.backgroundSync.schedulePeriodic()
        // Anonyme Anmeldung: gibt dem Geraet eine dauerhafte Kennung, ohne
        // dass jemand ein Konto anlegen muss.
        runCatching { Container.backend.signInIfNeeded() }
        deviceId = Container.backend.currentUserId()
        syncing = true
        Container.repository.syncAll()
        Container.rescheduleReminders()
        // Repariert Mitgliedschaften aus aelteren App-Fassungen, denen
        // Adminrolle oder Kalenderzuordnung fehlen.
        Container.refreshMembership()
        Container.refreshGroup()
        Container.uploadPushToken()
        syncing = false
    }

    // Vergangene Spiele blenden wir aus - der Tag selbst bleibt sichtbar,
    // damit ein laufendes Spiel nicht verschwindet.
    val startOfToday = remember(allMatches) {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
    }
    val visibleAll = remember(allMatches, membership, groupSnapshot) {
        Container.visibleMatches(allMatches)
    }
    val matches = remember(visibleAll, startOfToday) {
        visibleAll.filter { it.start >= startOfToday }
    }

    val colorBySubscription = remember(subscriptions) {
        subscriptions.associate { it.id to Color(it.colorArgb) }
    }
    val accentOf: (Match) -> Color = { colorBySubscription[it.subscriptionId] ?: Pitch }

    val participantsOf = remember(rsvps, membership, groupSnapshot, activeProfile) {
        ParticipantsSource { matchId ->
            participantsOfMatch(
                matchId = matchId,
                membership = membership,
                snapshot = groupSnapshot,
                localRsvps = rsvps,
                ownName = activeProfile.name,
                ownColor = activeProfile.colorArgb,
            )
        }
    }

    // Zurueck-Geste: Auf Android das Wischen vom Rand oder die Zurueck-Taste,
    // auf iOS das Wischen vom linken Rand. Ohne diese Behandlung landet die
    // Geste beim System und beendet die App, statt eine Ebene zurueckzugehen.
    SystemBackHandler(enabled = selected != null) { selected = null }
    SystemBackHandler(enabled = selected == null && screen != Screen.HOME) {
        screen = when (screen) {
            // Die Gruppe wird aus den Einstellungen heraus geoeffnet, also
            // fuehrt der Weg zurueck auch dorthin.
            Screen.GROUP -> Screen.SETTINGS
            else -> Screen.HOME
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            profile = activeProfile,
            matches = matches,
            calendarMatches = visibleAll,
            rsvps = rsvps,
            view = view,
            isSyncing = syncing,
            hasSubscriptions = subscriptions.any { it.enabled },
            participantsOf = participantsOf,
            importantIds = groupSnapshot.importantMatchIds,
            accentOf = accentOf,
            onViewChange = { view = it },
            onSelect = {
                importantError = null
                selected = it
            },
            onOpenSubscriptions = { screen = Screen.TEAMS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onRefresh = {
                syncing = true
                Container.syncAll { syncing = false }
            },
        )

        Screen.TEAMS -> TeamsScreen(
            subscriptions = subscriptions,
            matchCountOf = { id -> allMatches.count { it.subscriptionId == id } },
            onToggle = { id, enabled ->
                Container.store.setSubscriptionEnabled(id, enabled)
                // Eingeschaltet: Spielplan holen. Ausgeschaltet: nur die
                // Erinnerungen aufraeumen, die Spiele sind schon weg.
                if (enabled) {
                    syncing = true
                    Container.syncAll { syncing = false }
                } else {
                    Container.rescheduleReminders()
                }
            },
            onBack = { screen = Screen.HOME },
        )

        Screen.SETTINGS -> SettingsScreen(
            profile = activeProfile,
            reminders = reminders,
            subscriptionCount = subscriptions.count { it.enabled },
            groupName = membership?.groupName,
            memberCount = groupSnapshot.members.size,
            diagnostics = diagnostics,
            deviceId = deviceId,
            pushState = pushState,
            onSendTest = { Container.scheduler.sendTest() },
            onOpenExactAlarmSettings = { Container.scheduler.openExactAlarmSettings() },
            onProfileChange = { Container.saveProfile(it) },
            onRemindersChange = {
                Container.store.saveReminders(it)
                // Geaenderte Vorlaufzeit wirkt sich sofort auf alle
                // vorgemerkten Benachrichtigungen aus.
                Container.rescheduleReminders()
            },
            onOpenSubscriptions = { screen = Screen.TEAMS },
            onOpenGroup = { screen = Screen.GROUP },
            onBack = { screen = Screen.HOME },
        )

        Screen.GROUP -> GroupScreen(
            membership = membership,
            members = groupSnapshot.members,
            busy = groupBusy,
            error = groupError ?: if (membershipLost) {
                "Deine bisherige Gruppe gehört zu einer früheren Installation der App " +
                    "und ist nicht mehr erreichbar. Lege eine neue an oder lass dich neu einladen."
            } else {
                null
            },
            onCreate = { name ->
                groupBusy = true
                groupError = null
                Container.acknowledgeMembershipLoss()
                scope.launch {
                    runCatching {
                        Container.backend.createGroup(
                            groupName = name,
                            displayName = activeProfile.name,
                            colorArgb = activeProfile.colorArgb,
                        )
                    }
                        .onSuccess {
                            Container.store.saveMembership(it)
                            // Was vor dem Beitritt lokal beantwortet wurde,
                            // soll die Gruppe auch sehen.
                            Container.pushLocalRsvps()
                            Container.uploadPushToken()
                            Container.refreshGroup()
                        }
                        .onFailure { groupError = it.message ?: "Anlegen fehlgeschlagen" }
                    groupBusy = false
                }
            },
            onJoin = { code ->
                groupBusy = true
                groupError = null
                Container.acknowledgeMembershipLoss()
                scope.launch {
                    runCatching {
                        Container.backend.joinGroup(
                            code = code,
                            displayName = activeProfile.name,
                            colorArgb = activeProfile.colorArgb,
                        )
                    }
                        .onSuccess {
                            Container.store.saveMembership(it)
                            Container.pushLocalRsvps()
                            Container.uploadPushToken()
                            Container.refreshGroup()
                        }
                        .onFailure { groupError = it.message ?: "Code nicht gefunden" }
                    groupBusy = false
                }
            },
            // Der Parameter heisst bewusst nicht "scope" - das waere der
            // CoroutineScope von oben und wuerde verdeckt.
            onCreateInvite = { visibility ->
                val groupId = membership?.groupId
                if (groupId != null) {
                    groupBusy = true
                    groupError = null
                    invite = null
                    scope.launch {
                        runCatching { Container.backend.createInvite(groupId, visibility) }
                            .onSuccess { code -> invite = code to visibility }
                            .onFailure {
                                groupError = it.message ?: "Einladung fehlgeschlagen"
                            }
                        groupBusy = false
                    }
                }
            },
            invite = invite,
            onRemoveMember = { member ->
                groupError = null
                Container.removeMember(member.id) { groupError = it }
            },
            onLeave = {
                Container.store.clearMembership()
                Container.clearGroupSnapshot()
                invite = null
            },
            onBack = { screen = Screen.SETTINGS },
        )
    }

    selected?.let { match ->
        MatchDetailSheet(
            match = match,
            status = rsvps[match.id]?.status ?: RsvpStatus.UNDECIDED,
            comment = rsvps[match.id]?.comment,
            participants = participantsOf(match.id),
            accent = accentOf(match),
            isImportant = match.id in groupSnapshot.importantMatchIds,
            canEditImportant = membership?.isAdmin == true,
            importantError = importantError,
            onToggleImportant = {
                importantError = null
                Container.toggleImportant(match.id) { importantError = it }
            },
            onSetStatus = { status, comment ->
                Container.setRsvp(match.id, status, comment)
            },
            onDismiss = { selected = null },
        )
    }
}
