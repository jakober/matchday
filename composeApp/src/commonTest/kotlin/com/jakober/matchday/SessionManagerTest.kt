package com.jakober.matchday

import com.jakober.matchday.data.remote.PersistentSessionManager
import com.russhwolf.settings.MapSettings
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Anmeldesitzung muss App-Neustarts ueberstehen. Geht sie verloren, meldet
 * sich das Geraet als neuer anonymer Nutzer an und verliert seine
 * Gruppenzugehoerigkeit - genau der Fehler, den diese Klasse behebt.
 */
class SessionManagerTest {

    private fun session(token: String) = UserSession(
        accessToken = token,
        refreshToken = "refresh-$token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = null,
    )

    @Test
    fun `Sitzung uebersteht einen Neustart`() = runTest {
        val settings = MapSettings()
        PersistentSessionManager(settings).saveSession(session("abc"))

        // Neue Instanz auf denselben Daten - wie nach einem App-Start.
        val geladen = PersistentSessionManager(settings).loadSession()

        assertEquals("abc", geladen?.accessToken)
        assertEquals("refresh-abc", geladen?.refreshToken)
    }

    @Test
    fun `ohne gespeicherte Sitzung kommt null`() = runTest {
        assertNull(PersistentSessionManager(MapSettings()).loadSession())
    }

    @Test
    fun `Abmelden entfernt die Sitzung`() = runTest {
        val settings = MapSettings()
        val manager = PersistentSessionManager(settings)
        manager.saveSession(session("abc"))

        manager.deleteSession()

        assertNull(manager.loadSession())
    }

    @Test
    fun `unlesbare Daten fuehren nicht zum Absturz`() = runTest {
        // Etwa nach einem Formatwechsel: Die App soll sich dann neu anmelden,
        // nicht beim Start scheitern.
        val settings = MapSettings("supabase_session" to "kein gueltiges JSON")

        assertNull(PersistentSessionManager(settings).loadSession())
    }
}
