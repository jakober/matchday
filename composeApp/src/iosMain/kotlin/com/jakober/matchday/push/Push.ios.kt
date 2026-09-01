package com.jakober.matchday.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Duration.Companion.seconds

/**
 * Auf iOS kommt die Kennung nicht auf Anfrage, sondern nachtraeglich ueber
 * einen Rueckruf im App-Delegaten. Sie wird hier abgelegt, sobald sie da ist.
 */
private val apnsToken = MutableStateFlow<String?>(null)

/** Wird von der Swift-Seite aufgerufen, sobald Apple die Kennung liefert. */
fun onApnsToken(token: String) {
    apnsToken.value = token
}

actual fun createPushRegistrar(): PushRegistrar = IosPushRegistrar()

class IosPushRegistrar : PushRegistrar {
    override suspend fun token(): PushToken? {
        // Muss auf dem Hauptthread passieren, sonst verweigert UIKit den Dienst.
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.registerForRemoteNotifications()
        }

        // Apple antwortet ueblicherweise in Sekundenbruchteilen. Kommt nichts -
        // etwa ohne Netz oder im Simulator -, geht es ohne Push weiter.
        val value = withTimeoutOrNull(10.seconds) {
            apnsToken.filterNotNull().first()
        }
        return value?.let { PushToken(it, "ios") }
    }
}
