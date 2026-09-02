package com.jakober.matchday

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Wurde die Erlaubnis erteilt, planen wir sofort neu - vorher
            // gesetzte Alarme haetten sonst nichts anzuzeigen.
            if (granted) Container.rescheduleReminders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForNotificationPermission()
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        // Das geplante Fenster laeuft mit der Zeit leer, deshalb bei jeder
        // Rueckkehr in die App nachlegen.
        Container.rescheduleReminders()
        // Waehrend die App weg war, koennen andere geantwortet haben.
        Container.refreshGroupInBackground()
    }

    /** Seit Android 13 ist das Anzeigen von Benachrichtigungen erlaubnispflichtig. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
