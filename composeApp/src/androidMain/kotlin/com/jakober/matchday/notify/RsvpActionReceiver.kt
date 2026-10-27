package com.jakober.matchday.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jakober.matchday.Container
import com.jakober.matchday.MainActivity
import com.jakober.matchday.R
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.i18n.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nimmt die Antwort entgegen, die jemand direkt in der Benachrichtigung gibt -
 * auf dem Handy oder auf einer gekoppelten Uhr, die Android die Knoepfe
 * weiterreicht.
 *
 * Der Weg ist bewusst kurz: kein Start der App, keine Oberflaeche. Dafuer muss
 * hier die Sitzung aus dem Speicher geholt werden, denn der Prozess kann
 * gerade erst fuer diesen Empfang gestartet worden sein.
 */
class RsvpActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return
        val status = runCatching {
            RsvpStatus.valueOf(intent.getStringExtra(EXTRA_STATUS).orEmpty())
        }.getOrNull() ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, matchId.hashCode())

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Sofort wegnehmen: Die Frage ist beantwortet, auch wenn das Speichern
        // noch laeuft. Bleibt sie stehen, tippt man zweimal.
        manager.cancel(notificationId)

        // Ohne goAsync beendet das System den Prozess, sobald diese Methode
        // zurueckkehrt - die Antwort waere dann nur lokal gespeichert.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val ok = withTimeoutOrNull(TIMEOUT_MS) {
                Container.answerFromNotification(matchId, status)
            } ?: false
            runCatching { show(context, manager, notificationId, status, ok) }
            pending.finish()
        }
    }

    /**
     * Kurze Rueckmeldung. Bei Erfolg verschwindet sie von selbst wieder - sie
     * ist eine Bestaetigung, keine Nachricht. Nur der Fehlerfall bleibt
     * stehen, denn dann muss jemand etwas tun.
     */
    private fun show(
        context: Context,
        manager: NotificationManager,
        notificationId: Int,
        status: RsvpStatus,
        ok: Boolean,
    ) {
        AndroidReminderScheduler.createChannel(context)
        val text = when {
            !ok -> S.notifySaveFailed
            status == RsvpStatus.IN -> S.notifySavedIn
            else -> S.notifySavedOut
        }
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(if (ok) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .apply { if (ok) setTimeoutAfter(CONFIRMATION_MS) }
            .build()
        try {
            manager.notify(notificationId, note)
        } catch (_: SecurityException) {
            // Berechtigung entzogen - die Antwort ist trotzdem gespeichert.
        }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_STATUS = "status"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** So lange darf das Speichern dauern, bevor wir den Fehler melden. */
        private const val TIMEOUT_MS = 20_000L

        /** So lange steht die Bestaetigung, dann raeumt das System sie weg. */
        private const val CONFIRMATION_MS = 6_000L
    }
}
