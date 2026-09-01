package com.jakober.matchday.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jakober.matchday.Container
import com.jakober.matchday.MainActivity
import com.jakober.matchday.R
import com.jakober.matchday.notify.AndroidReminderScheduler

/**
 * Empfaengt die Benachrichtigungen ueber Zusagen der anderen.
 *
 * Die Edge Function schickt bewusst eine reine Datennachricht statt einer
 * fertigen Benachrichtigung: Nur so laesst sich der Text hier im selben Stil
 * bauen wie die uebrigen Erinnerungen, und wir behalten die Kontrolle darueber,
 * was angezeigt wird.
 */
class MatchdayMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Kennungen koennen sich jederzeit aendern; dann sofort nachtragen,
        // sonst gehen Benachrichtigungen ins Leere.
        Container.uploadPushToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: return
        val body = message.data["body"].orEmpty()

        AndroidReminderScheduler.createChannel(this)

        val openApp = PendingIntent.getActivity(
            this,
            title.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(message.messageId.hashCode(), notification) }
    }
}
