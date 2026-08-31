package com.jakober.matchday.notify

import com.jakober.matchday.Container
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/** Kennung, die auch in der Info.plist unter BGTaskSchedulerPermittedIdentifiers steht. */
const val REFRESH_TASK_ID = "com.jakober.matchday.refresh"

actual fun createBackgroundSync(): BackgroundSync = IosBackgroundSync()

class IosBackgroundSync : BackgroundSync {
    override fun schedulePeriodic() = submitRefreshRequest()
}

/**
 * Meldet die Hintergrundaufgabe beim System an.
 *
 * Muss aufgerufen werden, bevor der App-Start abgeschlossen ist - sonst
 * verweigert iOS die Registrierung. Deshalb ruft die Swift-Seite das in
 * `iOSApp.init()` auf und nicht der Kotlin-Code irgendwann spaeter.
 */
fun registerBackgroundRefresh() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
        identifier = REFRESH_TASK_ID,
        usingQueue = null,
    ) { task: BGTask? ->
        val refreshTask = task as? BGAppRefreshTask
        if (refreshTask == null) {
            task?.setTaskCompletedWithSuccess(false)
            return@registerForTaskWithIdentifier
        }

        // Direkt den naechsten Termin anmelden. Wer das vergisst, wird genau
        // einmal geweckt und danach nie wieder.
        submitRefreshRequest()

        refreshTask.expirationHandler = {
            refreshTask.setTaskCompletedWithSuccess(false)
        }

        Container.scope.launch {
            val errors = Container.repository.syncAll()
            Container.rescheduleReminders()
            refreshTask.setTaskCompletedWithSuccess(errors.isEmpty())
        }
    }
}

/**
 * Bittet um das naechste Zeitfenster. iOS entscheidet selbst, wann es die App
 * tatsaechlich weckt - der Wunschzeitpunkt ist ein fruehestens, kein genau.
 */
private fun submitRefreshRequest() {
    val request = BGAppRefreshTaskRequest(REFRESH_TASK_ID).apply {
        earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(
            SYNC_INTERVAL_HOURS.toDouble() * 3600.0
        )
    }
    // Wirft, wenn die Kennung nicht in der Info.plist steht oder der Simulator
    // keine Hintergrundaufgaben unterstuetzt. Kein Grund, die App zu beenden.
    runCatching { BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null) }
}
