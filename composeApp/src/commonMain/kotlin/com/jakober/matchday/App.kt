package com.jakober.matchday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.jakober.matchday.domain.Match
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.theme.MatchdayTheme
import com.jakober.matchday.theme.Pitch
import com.jakober.matchday.ui.detail.MatchDetailSheet
import com.jakober.matchday.ui.home.HomeScreen
import com.jakober.matchday.ui.home.HomeView
import com.jakober.matchday.ui.onboarding.OnboardingScreen
import com.jakober.matchday.ui.settings.SettingsScreen
import com.jakober.matchday.ui.subs.SubscriptionsScreen
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private enum class Screen { HOME, SUBSCRIPTIONS, SETTINGS }

@Composable
fun App() {
    MatchdayTheme {
        val profile by Container.store.profile.collectAsState()

        val current = profile
        if (current == null) {
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

    var screen by remember { mutableStateOf(Screen.HOME) }
    var view by remember { mutableStateOf(HomeView.LIST) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var syncing by remember { mutableStateOf(false) }

    // Beim Start: Erlaubnis klaeren, Feeds abgleichen, Erinnerungen neu planen.
    LaunchedEffect(Unit) {
        Container.scheduler.ensurePermission()
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

    val activeProfile = profile ?: return

    when (screen) {
        Screen.HOME -> HomeScreen(
            profile = activeProfile,
            matches = matches,
            rsvps = rsvps,
            view = view,
            isSyncing = syncing,
            hasSubscriptions = subscriptions.isNotEmpty(),
            accentOf = accentOf,
            onViewChange = { view = it },
            onSelect = { selected = it },
            onOpenSubscriptions = { screen = Screen.SUBSCRIPTIONS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onRefresh = {
                syncing = true
                Container.syncAll { syncing = false }
            },
        )

        Screen.SUBSCRIPTIONS -> SubscriptionsScreen(
            subscriptions = subscriptions,
            onBack = { screen = Screen.HOME },
        )

        Screen.SETTINGS -> SettingsScreen(
            profile = activeProfile,
            reminders = reminders,
            subscriptionCount = subscriptions.size,
            onProfileChange = { Container.store.saveProfile(it) },
            onRemindersChange = {
                Container.store.saveReminders(it)
                // Geaenderte Vorlaufzeit wirkt sich sofort auf alle
                // vorgemerkten Benachrichtigungen aus.
                Container.rescheduleReminders()
            },
            onOpenSubscriptions = { screen = Screen.SUBSCRIPTIONS },
            onBack = { screen = Screen.HOME },
        )
    }

    selected?.let { match ->
        MatchDetailSheet(
            match = match,
            status = rsvps[match.id] ?: RsvpStatus.UNDECIDED,
            accent = accentOf(match),
            onSetStatus = { status ->
                Container.store.setRsvp(match.id, status)
                // Eine Zusage nimmt die Wochen-Nachfrage aus dem Plan,
                // eine Ruecknahme bringt sie zurueck.
                Container.rescheduleReminders()
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
}
