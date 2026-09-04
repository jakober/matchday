package com.jakober.matchday.data

import com.jakober.matchday.data.ics.IcsParser
import com.jakober.matchday.domain.Subscription
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/** Ergebnis eines Abgleichs mit einem Kalenderfeed. */
sealed interface SyncResult {
    data class Success(val matchCount: Int) : SyncResult
    data class Failure(val message: String) : SyncResult
}

/** Vorschau beim Hinzufuegen eines Abos, bevor es gespeichert wird. */
data class FeedPreview(
    val suggestedName: String,
    val matchCount: Int,
    val normalizedUrl: String,
)

class MatchdayRepository(
    private val store: MatchdayStore,
    private val http: HttpClient,
) {

    /**
     * Laedt einen Feed und liest ihn, ohne etwas zu speichern - damit beim
     * Hinzufuegen sofort sichtbar ist, ob die Adresse taugt.
     */
    suspend fun preview(url: String): Result<FeedPreview> {
        val normalized = normalizeUrl(url)
        return runCatching {
            val ics = fetch(normalized)
            val matches = IcsParser.parse(ics, "vorschau", TimeZone.currentSystemDefault())
            FeedPreview(
                suggestedName = IcsParser.calendarName(ics) ?: guessNameFrom(normalized),
                matchCount = matches.size,
                normalizedUrl = normalized,
            )
        }
    }

    suspend fun sync(subscription: Subscription): SyncResult {
        return try {
            val ics = fetch(subscription.url)
            val matches = IcsParser.parse(
                ics = ics,
                subscriptionId = subscription.id,
                fallbackZone = TimeZone.currentSystemDefault(),
            )
            store.replaceMatchesOf(subscription.id, matches)
            store.updateSubscriptions(
                store.subscriptions.value.map {
                    if (it.id == subscription.id) it.copy(lastSyncedAt = Clock.System.now()) else it
                }
            )
            SyncResult.Success(matches.size)
        } catch (e: Exception) {
            // Ein kaputter Feed darf die anderen nicht mitreissen, deshalb
            // liefern wir den Fehler zurueck statt ihn zu werfen.
            SyncResult.Failure(e.message ?: "Abruf fehlgeschlagen")
        }
    }

    /** Gleicht alle aktiven Abos ab und liefert die Fehlermeldungen zurueck. */
    suspend fun syncAll(): List<String> {
        val errors = mutableListOf<String>()
        for (subscription in store.subscriptions.value.filter { it.enabled }) {
            when (val result = sync(subscription)) {
                is SyncResult.Failure -> errors += "${subscription.name}: ${result.message}"
                is SyncResult.Success -> Unit
            }
        }
        return errors
    }

    private suspend fun fetch(url: String): String {
        // Auch hier normalisieren: Die Adresse kommt kuenftig vom Server, so
        // wie der Admin sie eingegeben hat - womoeglich als webcal://.
        val response = http.get(normalizeUrl(url))
        if (!response.status.isSuccess()) {
            error("Server antwortete mit ${response.status.value}")
        }
        val body = response.bodyAsText()
        if (!body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
            error("Die Adresse liefert keinen Kalender")
        }
        return body
    }

    /**
     * Kalender-Abos werden oft als webcal:// verteilt - das ist technisch
     * dasselbe wie https, nur mit anderem Schema.
     */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("webcal://", ignoreCase = true) ->
                "https://" + trimmed.substringAfter("://")
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    /** Notnagel, wenn der Feed keinen eigenen Namen mitliefert. */
    private fun guessNameFrom(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.")
}
