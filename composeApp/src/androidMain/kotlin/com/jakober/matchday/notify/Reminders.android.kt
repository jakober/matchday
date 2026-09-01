package com.jakober.matchday.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jakober.matchday.MatchdayApp
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

actual fun createReminderScheduler(): ReminderScheduler =
    AndroidReminderScheduler(MatchdayApp.appContext)

/**
 * Setzt fuer jede Erinnerung einen Alarm, der beim Ausloesen die
 * Benachrichtigung baut. Die Ids der gesetzten Alarme merken wir uns, weil
 * der AlarmManager kein Auflisten anbietet und wir sie sonst nicht mehr
 * zurueckziehen koennten.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val prefs =
        context.getSharedPreferences("matchday_alarms", Context.MODE_PRIVATE)

    override suspend fun ensurePermission(): Boolean {
        // Vor Android 13 gab es die Laufzeitberechtigung noch nicht.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun diagnostics(): NotificationDiagnostics =
        NotificationDiagnostics(
            permissionGranted = ensurePermission(),
            pendingCount = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().size,
            exactAlarmsAllowed = canScheduleExact(),
            exactAlarmsRelevant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )

    override fun sendTest() {
        // Bewusst ueber denselben Weckmechanismus wie echte Erinnerungen -
        // eine sofort angezeigte Benachrichtigung wuerde nur beweisen, dass
        // der Kanal steht, nicht dass der Alarm ausloest.
        schedule(
            ScheduledReminder(
                id = "test",
                at = Clock.System.now() + TEST_DELAY_SECONDS.seconds,
                title = "Testbenachrichtigung",
                body = "Wenn du das siehst, funktionieren die Erinnerungen.",
            )
        )
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:" + context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    override fun replaceAll(reminders: List<ScheduledReminder>) {
        cancelPrevious()

        reminders.forEach(::schedule)
        prefs.edit().putStringSet(KEY_SCHEDULED, reminders.map { it.id }.toSet()).apply()
    }

    private fun schedule(reminder: ScheduledReminder) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderReceiver.EXTRA_BODY, reminder.body)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAt = reminder.at.toEpochMilliseconds()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending,
                )
            } else {
                // Ohne die Erlaubnis fuer exakte Alarme bleibt nur ein
                // Zeitfenster. Das kann im Doze-Modus einige Minuten
                // spaeter feuern - fuer eine Spielerinnerung vertretbar.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /**
     * Ab Android 12 ist der exakte Alarm eine eigene Erlaubnis, die der
     * Nutzer in den Systemeinstellungen erteilt.
     */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun cancelPrevious() {
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        for (id in previous) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    companion object {
        private const val KEY_SCHEDULED = "scheduled_ids"
        const val CHANNEL_ID = "matches"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spiele",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Erinnerungen an anstehende Spiele"
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
