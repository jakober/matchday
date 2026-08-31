package com.jakober.matchday.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jakober.matchday.Container

/**
 * Ein Neustart loescht alle gesetzten Alarme. Danach planen wir sie neu,
 * sonst faellt jede Erinnerung aus, die vor dem naechsten App-Start faellig
 * gewesen waere.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Container.rescheduleReminders()
    }
}
