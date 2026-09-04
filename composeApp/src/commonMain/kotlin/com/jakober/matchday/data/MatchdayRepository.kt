package com.jakober.matchday.data

import com.jakober.matchday.data.ics.IcsParser
import com.jakober.matchday.domain.Subscription
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days

/** Wie weit zurueck Spiele behalten werden. */
private val HISTORY = 60.days

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
            val parsed = IcsParser.parse(
                ics = ics,
                subscriptionId = subscription.id,
                fallbackZone = TimeZone.currentSystemDefault(),
            )
            // Laengst Vergangenes verwerfen. Ein Ligakalender hat ueber 300
            // Termine, und die Ablage schreibt bei jeder Aenderung die ganze
            // Liste neu - was niemand mehr ansieht, muss da nicht mitlaufen.
            val cutoff = Clock.System.now() - HISTORY
            val matches = parsed.filter { it.start >= cutoff }
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

    /**
     * Holt den Kalendertext. Die Fehlermeldungen sind fuer Menschen
     * geschrieben, nicht fuer Entwickler: Sie landen unveraendert auf dem
     * Bildschirm dessen, der gerade eine Adresse eingetippt hat.
     */
    private suspend fun fetch(url: String): String {
        // Auch hier normalisieren: Die Adresse kommt vom Server, so wie der
        // Admin sie eingegeben hat - womoeglich als webcal://.
        val response = try {
            http.get(normalizeUrl(url))
        } catch (e: HttpRequestTimeoutException) {
            error("Der Server antwortet nicht. Bitte später erneut versuchen.")
        } catch (e: ConnectTimeoutException) {
            error("Der Server antwortet nicht. Bitte später erneut versuchen.")
        } catch (e: SocketTimeoutException) {
            error("Der Server antwortet nicht. Bitte später erneut versuchen.")
        } catch (e: IllegalArgumentException) {
            // Ktor lehnt Adressen mit Leerzeichen oder falschem Aufbau so ab.
            error("Das ist keine gültige Adresse. Bitte auf Tippfehler prüfen.")
        }

        when (response.status.value) {
            404 -> error("Die Adresse gibt es nicht. Bitte auf Tippfehler prüfen.")
            401, 403 -> error("Der Kalender ist nicht öffentlich - diese Adresse verlangt eine Anmeldung.")
        }
        if (!response.status.isSuccess()) {
            error("Der Server antwortete mit Fehler ${response.status.value}.")
        }
        val body = response.bodyAsText()
        if (!body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
            error(
                "Unter dieser Adresse liegt kein Kalender. Wahrscheinlich ist es die Adresse der " +
                    "Webseite statt die des Kalenders - suche dort nach „Kalender abonnieren“."
            )
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
