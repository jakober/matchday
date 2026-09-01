package com.jakober.matchday.data.remote

import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Legt die Anmeldesitzung selbst ab, statt sich auf die Voreinstellung des
 * Supabase-Clients zu verlassen.
 *
 * Warum das noetig ist: Ohne dauerhafte Ablage meldet sich das Geraet bei
 * jedem App-Start als *neuer* anonymer Nutzer an. Die Gruppenzugehoerigkeit
 * haengt aber an der Nutzerkennung - sie ginge damit bei jedem Start verloren,
 * ohne dass irgendwo ein Fehler erschiene. Genau das ist passiert: In der
 * Datenbank standen siebenunddreissig anonyme Nutzer fuer ein einziges Geraet.
 *
 * Hier wird dieselbe Ablage benutzt, in der auch Profil und Spielplan liegen -
 * die ueberlebt nachweislich Neustarts und App-Aktualisierungen.
 */
class PersistentSessionManager(private val settings: Settings) : SessionManager {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        settings.putString(KEY, json.encodeToString(session))
    }

    override suspend fun loadSession(): UserSession? {
        val raw = settings.getStringOrNull(KEY) ?: return null
        // Eine unlesbare Sitzung wird wie keine behandelt; die App meldet sich
        // dann neu an, statt beim Start zu scheitern.
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "supabase_session"
    }
}
