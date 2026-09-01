package com.jakober.matchday.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual fun createPushRegistrar(): PushRegistrar = AndroidPushRegistrar()

class AndroidPushRegistrar : PushRegistrar {
    override suspend fun token(): PushToken? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                // Ohne Google-Play-Dienste schlaegt das fehl. Kein Grund fuer
                // einen Absturz - die App funktioniert dann nur ohne Push.
                val value = if (task.isSuccessful) task.result else null
                cont.resume(value?.let { PushToken(it, "android") })
            }
    }
}
