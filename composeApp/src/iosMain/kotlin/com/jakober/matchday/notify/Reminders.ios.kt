package com.jakober.matchday.notify

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual fun createReminderScheduler(): ReminderScheduler = IosReminderScheduler()

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
        ) { granted, _ ->
            cont.resume(granted)
        }
    }

    override fun replaceAll(reminders: List<ScheduledReminder>) {
        center.removeAllPendingNotificationRequests()

        val now = Clock.System.now()
        for (reminder in reminders) {
            val seconds = (reminder.at - now).inWholeSeconds.toDouble()
            // Ein Ausloeser in der Vergangenheit wird von iOS abgelehnt.
            if (seconds <= 0.0) continue

            val content = UNMutableNotificationContent().apply {
                setTitle(reminder.title)
                setBody(reminder.body)
                setSound(UNNotificationSound.defaultSound())
            }

            val trigger = UNTimeIntervalNotificationTrigger
                .triggerWithTimeInterval(seconds, repeats = false)

            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = reminder.id,
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(request, null)
        }
    }
}
