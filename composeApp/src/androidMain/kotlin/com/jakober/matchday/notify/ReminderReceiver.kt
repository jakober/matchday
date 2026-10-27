package com.jakober.matchday.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jakober.matchday.MainActivity
import com.jakober.matchday.R
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.i18n.S

/** Baut die Benachrichtigung, wenn ein geplanter Alarm ausloest. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()

        AndroidReminderScheduler.createChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)

        // Antworten ohne die App zu oeffnen. Android reicht die Knoepfe an
        // eine gekoppelte Uhr weiter, dort ist das der einzige brauchbare Weg.
        intent.getStringExtra(EXTRA_MATCH_ID)?.let { matchId ->
            builder.addAction(0, S.notifyIn, answer(context, id, matchId, RsvpStatus.IN))
            builder.addAction(0, S.notifyOut, answer(context, id, matchId, RsvpStatus.OUT))
        }

        val notification = builder.build()

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Berechtigung wurde zwischenzeitlich entzogen - nichts zu tun.
        }
    }

    /**
     * Ein Knopf in der Benachrichtigung. Die Kennung unterscheidet die beiden
     * Antworten - mit derselben wuerde die zweite die erste ueberschreiben,
     * und beide Knoepfe saegen dasselbe.
     */
    private fun answer(
        context: Context,
        reminderId: String,
        matchId: String,
        status: RsvpStatus,
    ): PendingIntent {
        val intent = Intent(context, RsvpActionReceiver::class.java).apply {
            putExtra(RsvpActionReceiver.EXTRA_MATCH_ID, matchId)
            putExtra(RsvpActionReceiver.EXTRA_STATUS, status.name)
            putExtra(RsvpActionReceiver.EXTRA_NOTIFICATION_ID, reminderId.hashCode())
        }
        return PendingIntent.getBroadcast(
            context,
            (reminderId + status.name).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_MATCH_ID = "match_id"
    }
}
