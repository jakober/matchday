package com.jakober.matchday.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jakober.matchday.MainActivity
import com.jakober.matchday.R

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

        val notification = NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Berechtigung wurde zwischenzeitlich entzogen - nichts zu tun.
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
