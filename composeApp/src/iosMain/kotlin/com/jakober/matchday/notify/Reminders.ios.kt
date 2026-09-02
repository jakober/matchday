package com.jakober.matchday.notify

import com.jakober.matchday.Container
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual fun createReminderScheduler(): ReminderScheduler = IosReminderScheduler()

/**
 * Sorgt dafuer, dass Benachrichtigungen auch dann sichtbar sind, wenn die App
 * gerade offen ist.
 *
 * Ohne diesen Delegaten unterdrueckt iOS die Anzeige im Vordergrund
 * vollstaendig - die Erinnerung waere zugestellt, aber unsichtbar, und man
 * haelt sie faelschlich fuer kaputt.
 */
private class ForegroundPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        // Eine eintreffende Meldung heisst: In der Gruppe hat sich etwas
        // geaendert. Die offene Ansicht gleicht sich damit von selbst ab.
        Container.refreshGroupInBackground()

        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound
        )
    }
}

// Starke Referenz noetig: Der Delegat wird nur schwach gehalten und waere
// sonst sofort wieder eingesammelt.
private val foregroundPresenter = ForegroundPresenter()

/** Vom App-Start aufzurufen, bevor die erste Benachrichtigung ausgeloest wird. */
fun configureNotifications() {
    UNUserNotificationCenter.currentNotificationCenter().delegate = foregroundPresenter
}

/**
 * Nutzt die lokalen Benachrichtigungen von iOS.
 *
 * Wichtig: iOS haelt hoechstens 64 vorgemerkte Benachrichtigungen pro App
 * und verwirft weitere kommentarlos. Die Begrenzung passiert in
 * [ReminderPlanner], hier wird nur noch eingetragen.
 */
class IosReminderScheduler : ReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun ensurePermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted: Boolean, _: NSError? ->
            cont.resume(granted)
        }
    }

    override suspend fun diagnostics(): NotificationDiagnostics {
        val granted = suspendCancellableCoroutine { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
                val status = settings?.authorizationStatus
                cont.resume(
                    status == UNAuthorizationStatusAuthorized ||
                        status == UNAuthorizationStatusProvisional
                )
            }
        }
        val pending = suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                cont.resume(requests?.size ?: 0)
            }
        }
        return NotificationDiagnostics(
            permissionGranted = granted,
            pendingCount = pending,
            // Exakte Alarme sind eine Android-Eigenheit.
            exactAlarmsAllowed = true,
            exactAlarmsRelevant = false,
        )
    }

    override fun sendTest() {
        add(
            id = "test",
            seconds = TEST_DELAY_SECONDS.toDouble(),
            title = "Testbenachrichtigung",
            body = "Wenn du das siehst, funktionieren die Erinnerungen.",
        )
    }

    /** Auf iOS gibt es keine gesonderte Erlaubnis fuer exakte Zeitpunkte. */
    override fun openExactAlarmSettings() = Unit

    override fun replaceAll(reminders: List<ScheduledReminder>) {
        center.removeAllPendingNotificationRequests()

        val now = Clock.System.now()
        for (reminder in reminders) {
            val seconds = (reminder.at - now).inWholeSeconds.toDouble()
            // Ein Ausloeser in der Vergangenheit wird von iOS abgelehnt.
            if (seconds <= 0.0) continue
            add(reminder.id, seconds, reminder.title, reminder.body)
        }
    }

    private fun add(id: String, seconds: Double, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }
        val trigger = UNTimeIntervalNotificationTrigger
            .triggerWithTimeInterval(seconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }
}

/** Von der Swift-Seite bei Rueckkehr in den Vordergrund aufgerufen. */
fun refreshGroupOnResume() {
    Container.refreshGroupInBackground()
}
