package com.jakober.matchday.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jakober.matchday.Container
import com.jakober.matchday.MatchdayApp
import java.util.concurrent.TimeUnit

actual fun createBackgroundSync(): BackgroundSync =
    AndroidBackgroundSync(MatchdayApp.appContext)

class AndroidBackgroundSync(private val context: Context) : BackgroundSync {

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP: Ein laufender Zeitplan wird nicht bei jedem App-Start
        // zurueckgesetzt, sonst kaeme er nie zur Ausfuehrung.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "matchday-sync"
    }
}

/**
 * Holt die Spielplaene und plant anschliessend die Erinnerungen neu - der
 * zweite Schritt ist der eigentliche Zweck der Uebung.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Container.repository.syncAll()
            Container.rescheduleReminders()
            Result.success()
        } catch (_: Exception) {
            // Beim naechsten Durchlauf erneut versuchen; ein fehlgeschlagener
            // Abgleich ist kein Grund, den Zeitplan aufzugeben.
            Result.retry()
        }
    }
}
