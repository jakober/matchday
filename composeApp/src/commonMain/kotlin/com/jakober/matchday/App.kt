package com.jakober.matchday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.ParticipantsSource
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.theme.MatchdayTheme
import com.jakober.matchday.theme.Pitch
import com.jakober.matchday.ui.detail.MatchDetailSheet
import com.jakober.matchday.ui.home.HomeScreen
import com.jakober.matchday.ui.home.HomeView
import com.jakober.matchday.ui.onboarding.OnboardingScreen
import com.jakober.matchday.ui.settings.SettingsScreen
import com.jakober.matchday.ui.subs.TeamsScreen
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private enum class Screen { HOME, TEAMS, SETTINGS }

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

    val activeProfile = profile ?: return

    var screen by remember { mutableStateOf(Screen.HOME) }
    var view by remember { mutableStateOf(HomeView.LIST) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Container.scheduler.ensurePermission()
        // Wiederkehrenden Abgleich einrichten, damit verlegte Anstosszeiten
        // auch ankommen, wenn die App laenger nicht geoeffnet wird.
        Container.backgroundSync.schedulePeriodic()
        syncing = true
        Container.repository.syncAll()
        Container.rescheduleReminders()
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
    val matches = remember(allMatches, startOfToday) {
        allMatches.filter { it.start >= startOfToday }
    }

    val colorBySubscription = remember(subscriptions) {
        subscriptions.associate { it.id to Color(it.colorArgb) }
    }
    val accentOf: (Match) -> Color = { colorBySubscription[it.subscriptionId] ?: Pitch }

    // Trennstelle zur Gruppe: Solange kein Backend angebunden ist, kennt die
    // App nur die eigene Antwort. Sobald Supabase dranhaengt, wird hier die
    // Liste der Gruppenmitglieder eingesetzt - die Oberflaeche bleibt gleich.
    val participantsOf = remember(rsvps, activeProfile) {
        ParticipantsSource { matchId ->
            val entry = rsvps[matchId]
            if (entry == null) {
                emptyList()
            } else {
                listOf(
                    Participant(
                        id = activeProfile.id,
                        name = activeProfile.name,
                        colorArgb = activeProfile.colorArgb,
                        status = entry.status,
                        comment = entry.comment,
                        isMe = true,
                    )
                )
            }
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            profile = activeProfile,
            matches = matches,
            calendarMatches = allMatches,
            rsvps = rsvps,
            view = view,
            isSyncing = syncing,
            hasSubscriptions = subscriptions.any { it.enabled },
            participantsOf = participantsOf,
            accentOf = accentOf,
            onViewChange = { view = it },
            onSelect = { selected = it },
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
            onProfileChange = { Container.store.saveProfile(it) },
            onRemindersChange = {
                Container.store.saveReminders(it)
                // Geaenderte Vorlaufzeit wirkt sich sofort auf alle
                // vorgemerkten Benachrichtigungen aus.
                Container.rescheduleReminders()
            },
            onOpenSubscriptions = { screen = Screen.TEAMS },
            onBack = { screen = Screen.HOME },
        )
    }

    selected?.let { match ->
        MatchDetailSheet(
            match = match,
            status = rsvps[match.id]?.status ?: RsvpStatus.UNDECIDED,
            comment = rsvps[match.id]?.comment,
            participants = participantsOf(match.id),
            accent = accentOf(match),
            onSetStatus = { status, comment ->
                Container.store.setRsvp(match.id, status, comment)
                // Eine Zusage nimmt die Wochen-Nachfrage aus dem Plan,
                // eine Ruecknahme bringt sie zurueck.
                Container.rescheduleReminders()
            },
            onDismiss = { selected = null },
        )
    }
}
