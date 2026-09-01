package com.jakober.matchday.push

/** Adresse, an die dieses Geraet Benachrichtigungen empfangen kann. */
data class PushToken(
    val value: String,
    /** "android" oder "ios" - die Edge Function waehlt danach den Versandweg. */
    val platform: String,
)

/**
 * Besorgt die Push-Kennung des Geraets.
 *
 * Auf Android vergibt sie Firebase Cloud Messaging, auf iOS Apple selbst. Beide
 * koennen sich aendern (Neuinstallation, Wiederherstellung aus einem Backup),
 * deshalb wird sie bei jedem Start neu geholt und hochgeschrieben.
 */
interface PushRegistrar {
    suspend fun token(): PushToken?
}

expect fun createPushRegistrar(): PushRegistrar
