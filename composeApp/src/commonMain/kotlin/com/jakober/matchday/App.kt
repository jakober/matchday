package com.jakober.matchday

import com.jakober.matchday.i18n.S
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.jakober.matchday.domain.Profile
import com.jakober.matchday.ui.auth.AuthScreen
import com.jakober.matchday.ui.auth.CodeScreen
import com.jakober.matchday.ui.auth.PasswordScreen
import com.jakober.matchday.ui.components.SystemBackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.jakober.matchday.data.remote.participantsOfMatch
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.Subscription
import com.jakober.matchday.notify.NotificationDiagnostics
import com.jakober.matchday.theme.MatchdayTheme
import com.jakober.matchday.theme.Pitch
import com.jakober.matchday.ui.detail.MatchDetailSheet
import com.jakober.matchday.ui.group.GroupScreen
import com.jakober.matchday.ui.group.InviteResult
import com.jakober.matchday.ui.home.HomeScreen
import com.jakober.matchday.ui.home.HomeView
import com.jakober.matchday.ui.onboarding.OnboardingScreen
import com.jakober.matchday.ui.settings.SettingsScreen
import com.jakober.matchday.ui.subs.ImportScreen
import com.jakober.matchday.ui.subs.TeamsScreen
import com.jakober.matchday.data.FeedPreview
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private enum class Screen { HOME, TEAMS, IMPORT, SETTINGS, GROUP }

/** Ab dieser Nutzungsdauer darf die App um exakte Alarme bitten. */
private const val EXACT_ALARM_PROMPT_AFTER_SECONDS = 5 * 60L

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
        val auth by Container.auth.collectAsState()
        val profile by Container.store.profile.collectAsState()
        val membership by Container.store.membership.collectAsState()

        LaunchedEffect(Unit) { Container.startSession() }

        // Vier Tore, in dieser Reihenfolge: Konto, Profil, Gruppe, App. Ohne
        // Gruppe gibt es nichts zu sehen - die Kalender gehoeren der Gruppe.
        when (val state = auth) {
            AuthState.Loading -> LoadingScreen()
            AuthState.SignedOut -> AuthFlow()
            is AuthState.AwaitingCode -> CodeFlow(state.email)
            is AuthState.AwaitingRecoveryCode -> RecoveryCodeFlow(state.email)
            AuthState.NewPassword -> NewPasswordFlow()
            AuthState.SignedIn -> when {
                profile == null -> OnboardingScreen(
                    onDone = { newProfile ->
                        Container.store.saveProfile(newProfile)
                        // Beim ersten Start gleich nach der Erlaubnis fragen -
                        // ohne sie waeren alle Erinnerungen wirkungslos.
                        Container.requestNotificationPermission()
                    },
                )
                membership == null -> GroupGate(profile!!)
                else -> Root()
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Anmelden und Registrieren, mit dem Zustand, den der Bildschirm braucht. */
@Composable
private fun AuthFlow() {
    val scope = rememberCoroutineScope()
    val pendingInvite by Container.pendingInvite.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Result<Unit>, onOk: () -> Unit = {}) {
        busy = true
        error = null
        notice = null
        scope.launch {
            block().onSuccess { onOk() }.onFailure { error = it.message ?: S.failed }
            busy = false
        }
    }

    AuthScreen(
        busy = busy,
        error = error,
        notice = notice,
        inviteCode = pendingInvite,
        onSignIn = { email, password -> run({ Container.signIn(email, password) }) },
        onSignUp = { name, email, password -> run({ Container.signUp(name, email, password) }) },
        onForgotPassword = { email -> run({ Container.requestPasswordReset(email) }) },
        onAcceptInvite = { code, password ->
            // Der Code bleibt vorgemerkt: Weist der Server ab, weil ein Konto
            // besteht oder eine Registrierung noetig ist, fuehrt der Weg ueber
            // Anmeldung bzw. Registrierung - und danach ist der Code noch da.
            Container.handleUrl("code=$code")
            run({ Container.acceptInvite(code, password) })
        },
    )
}

/** Code aus der Zuruecksetz-Mail. */
@Composable
private fun RecoveryCodeFlow(email: String) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Result<Unit>, onOk: () -> Unit = {}) {
        busy = true
        error = null
        scope.launch {
            block().onSuccess { onOk() }.onFailure { error = it.message ?: S.failed }
            busy = false
        }
    }

    CodeScreen(
        email = email,
        busy = busy,
        error = error,
        notice = notice,
        title = S.resetTitle,
        cancelLabel = S.backToSignIn,
        onConfirm = { code -> run({ Container.confirmRecovery(email, code) }) },
        onResend = {
            notice = null
            run({ Container.requestPasswordReset(email) }) { notice = S.newCodeSent }
        },
        onCancel = { Container.cancelSignUp() },
    )
}

@Composable
private fun NewPasswordFlow() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PasswordScreen(
        busy = busy,
        error = error,
        onSubmit = { password ->
            busy = true
            error = null
            scope.launch {
                Container.setNewPassword(password)
                    .onFailure { error = it.message ?: S.saveFailed }
                busy = false
            }
        },
    )
}

/** Bestaetigungscode aus der Mail. */
@Composable
private fun CodeFlow(email: String) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    CodeScreen(
        email = email,
        busy = busy,
        error = error,
        notice = notice,
        onConfirm = { code ->
            busy = true
            error = null
            scope.launch {
                Container.confirmEmail(email, code)
                    .onSuccess { Container.requestNotificationPermission() }
                    .onFailure { error = it.message ?: S.confirmFailed }
                busy = false
            }
        },
        onResend = {
            busy = true
            error = null
            notice = null
            scope.launch {
                Container.resendCode(email)
                    .onSuccess { notice = S.newCodeSent }
                    .onFailure { error = it.message ?: S.sendFailed }
                busy = false
            }
        },
        onCancel = { Container.cancelSignUp() },
    )
}

/**
 * Ohne Gruppe geht es nicht weiter: anlegen oder mit Code beitreten. Der
 * einzige andere Ausweg ist das Abmelden.
 */
@Composable
private fun GroupGate(profile: Profile) {
    val busy by Container.groupBusy.collectAsState()
    val membershipLost by Container.membershipLost.collectAsState()
    val pendingInvite by Container.pendingInvite.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }

    GroupScreen(
        membership = null,
        members = emptyList(),
        busy = busy,
        error = error ?: if (membershipLost) {
            S.membershipLostGate
        } else {
            null
        },
        invite = null,
        onCreate = { name ->
            error = null
            Container.createGroup(name, profile) { error = it }
        },
        onJoin = { code ->
            error = null
            Container.joinGroup(code, profile) { error = it }
        },
        onCreateInvite = { _, _, _ -> },
        onRemoveMember = {},
        onLeave = {},
        onBack = {},
        onSignOut = { Container.signOut() },
        initialCode = pendingInvite,
    )
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
    val logos by Container.logos.collectAsState()
    val resumeTick by Container.resumeTick.collectAsState()

    val activeProfile = profile ?: return
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var view by remember { mutableStateOf(HomeView.LIST) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val groupBusy by Container.groupBusy.collectAsState()
    var inviteBusy by remember { mutableStateOf(false) }
    var accountNotice by remember { mutableStateOf<String?>(null) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var invite by remember { mutableStateOf<InviteResult?>(null) }
    var importantError by remember { mutableStateOf<String?>(null) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<FeedPreview?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var deviceId by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<NotificationDiagnostics?>(null) }

    // Beim Oeffnen der Einstellungen und bei jeder Rueckkehr in die App neu
    // erheben - die Erlaubnis wird in den Systemeinstellungen erteilt, und von
    // dort kommt man zurueck, ohne dass sich hier sonst etwas aendert.
    LaunchedEffect(screen, rsvps, resumeTick) {
        if (screen == Screen.SETTINGS) {
            diagnostics = runCatching { Container.scheduler.diagnostics() }.getOrNull()
        }
    }

    // Hinweis auf exakte Alarme, aber erst nach fuenf Minuten Nutzung: Beim
    // ersten Start prasseln ohnehin Erlaubnisfragen auf den Nutzer ein. Wer
    // "nicht mehr fragen" waehlt, sieht ihn nie wieder; wer "spaeter" waehlt,
    // beim naechsten Oeffnen der App.
    var exactAlarmPrompt by remember { mutableStateOf(false) }
    var exactAlarmAsked by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        while (!exactAlarmAsked && !Container.store.exactAlarmPromptDismissed) {
            if (Container.usageSeconds() >= EXACT_ALARM_PROMPT_AFTER_SECONDS) {
                val current = runCatching { Container.scheduler.diagnostics() }.getOrNull()
                if (current == null || !current.exactAlarmsRelevant || current.exactAlarmsAllowed) break
                exactAlarmPrompt = true
                exactAlarmAsked = true
                break
            }
            delay(30_000)
        }
    }
    if (exactAlarmPrompt) {
        AlertDialog(
            onDismissRequest = { exactAlarmPrompt = false },
            title = { Text(S.exactPromptTitle) },
            text = { Text(S.exactPromptText) },
            confirmButton = {
                TextButton(onClick = {
                    exactAlarmPrompt = false
                    Container.scheduler.openExactAlarmSettings()
                }) { Text(S.exactPromptAllow) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        Container.store.dismissExactAlarmPrompt()
                        exactAlarmPrompt = false
                    }) { Text(S.exactPromptNever) }
                    TextButton(onClick = { exactAlarmPrompt = false }) { Text(S.exactPromptLater) }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        Container.scheduler.ensurePermission()
        // Wiederkehrenden Abgleich einrichten, damit verlegte Anstosszeiten
        // auch ankommen, wenn die App laenger nicht geoeffnet wird.
        Container.backgroundSync.schedulePeriodic()
        deviceId = Container.backend.currentUserId()
        syncing = true
        // Erst die Mitgliedschaft, dann die Kalender der Gruppe, dann die
        // Spielplaene - die Abos kommen vom Server. In anderer Reihenfolge
        // zeigte die App nach einer Neuinstallation beim ersten Start einen
        // leeren Kalender und fuellte sich erst beim zweiten.
        Container.refreshMembership()
        Container.refreshCalendars()
        Container.repository.syncAll()
        Container.rescheduleReminders()
        Container.refreshGroup()
        Container.uploadPushToken()
        syncing = false
        // Wappen nach dem Sichtbarwerden - sie sind Kosmetik und duerfen
        // die Anzeige nicht aufhalten.
        Container.resolveLogos()
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

    val subscriptionById = remember(subscriptions) { subscriptions.associateBy { it.id } }
    val accentOf: (Match) -> Color = {
        subscriptionById[it.subscriptionId]?.let { sub -> Color(sub.colorArgb) } ?: Pitch
    }
    val subscriptionOf: (String) -> Subscription? = { subscriptionById[it] }
    val logoOf: (String?) -> String? = { name -> name?.let { logos[it.trim()]?.url } }

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
            Screen.IMPORT -> Screen.TEAMS
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
            subscriptionOf = subscriptionOf,
            logoOf = logoOf,
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
            isAdmin = membership?.isAdmin == true,
            error = calendarError,
            matchCountOf = { id -> allMatches.count { it.subscriptionId == id } },
            onAdd = {
                importPreview = null
                importError = null
                calendarError = null
                screen = Screen.IMPORT
            },
            onRemove = { subscription ->
                calendarError = null
                Container.removeCalendar(subscription.id) { calendarError = it }
            },
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

        Screen.IMPORT -> ImportScreen(
            busy = importBusy,
            preview = importPreview,
            error = importError,
            onPreview = { url ->
                importBusy = true
                importError = null
                importPreview = null
                scope.launch {
                    Container.previewCalendar(url)
                        .onSuccess { importPreview = it }
                        .onFailure { importError = it.message ?: S.checkFailed }
                    importBusy = false
                }
            },
            onAdd = { name, url, colorArgb ->
                importBusy = true
                importError = null
                scope.launch {
                    Container.importCalendar(name, url, colorArgb)
                        .onSuccess {
                            importPreview = null
                            screen = Screen.TEAMS
                        }
                        .onFailure { importError = it.message ?: S.addFailedShort }
                    importBusy = false
                }
            },
            onBack = { screen = Screen.TEAMS },
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
            email = Container.backend.currentEmail(),
            accountNotice = accountNotice,
            onChangePassword = { password ->
                scope.launch {
                    Container.changePassword(password)
                        .onSuccess { accountNotice = S.passwordChanged }
                        .onFailure { accountNotice = it.message }
                }
            },
            onSignOut = { Container.signOut() },
            onBack = { screen = Screen.HOME },
        )

        Screen.GROUP -> GroupScreen(
            membership = membership,
            members = groupSnapshot.members,
            busy = groupBusy || inviteBusy,
            error = groupError ?: if (membershipLost) {
                S.membershipLostSettings
            } else {
                null
            },
            onCreate = { name ->
                groupError = null
                Container.createGroup(name, activeProfile) { groupError = it }
            },
            onJoin = { code ->
                groupError = null
                Container.joinGroup(code, activeProfile) { groupError = it }
            },
            // Der Parameter heisst bewusst nicht "scope" - das waere der
            // CoroutineScope von oben und wuerde verdeckt.
            onCreateInvite = { visibility, email, name ->
                val groupId = membership?.groupId
                if (groupId != null) {
                    inviteBusy = true
                    groupError = null
                    invite = null
                    scope.launch {
                        runCatching {
                            if (email == null) {
                                InviteResult(Container.backend.createInvite(groupId, visibility), visibility)
                            } else {
                                val sent = Container.backend.sendInvite(groupId, visibility, email, name.orEmpty())
                                InviteResult(sent.code, visibility, sent.sentTo, sent.warning)
                            }
                        }
                            .onSuccess { invite = it }
                            .onFailure {
                                groupError = it.message ?: S.errInviteFailed
                            }
                        inviteBusy = false
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
