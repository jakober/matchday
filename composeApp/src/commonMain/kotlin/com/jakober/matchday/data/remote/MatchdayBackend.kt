package com.jakober.matchday.data.remote

import com.jakober.matchday.data.createSettings
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.push.PushToken
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Anbindung an Supabase.
 *
 * Alles laeuft ueber anonyme Anmeldung: Jedes Geraet bekommt eine dauerhafte
 * Kennung, ohne dass jemand ein Konto anlegen muss. Wer was sehen und aendern
 * darf, entscheiden die Regeln in der Datenbank - der Client kann sie nicht
 * umgehen, auch wenn jemand den Schluessel aus der App liest.
 */
class MatchdayBackend {

    private val client = createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth) {
            // Eigene Ablage statt der Voreinstellung. Die Voreinstellung hat
            // auf Android nicht gegriffen: Das Geraet meldete sich bei jedem
            // Start als neuer anonymer Nutzer an und verlor dabei lautlos
            // seine Gruppenzugehoerigkeit.
            sessionManager = PersistentSessionManager(createSettings())
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Functions)
    }

    /** Kennung des angemeldeten Geraets, sofern eine Sitzung besteht. */
    fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id

    /**
     * Prueft, ob diese Kennung Mitglied der Gruppe ist.
     *
     * Liefert null, wenn es sich nicht feststellen laesst - etwa ohne Netz
     * oder solange keine Sitzung geladen ist. Der Unterschied ist wichtig:
     * "unbekannt" darf niemals dazu fuehren, dass eine Mitgliedschaft
     * verworfen wird.
     */
    suspend fun isMemberOf(groupId: String): Boolean? {
        // Ohne gueltige Anmeldung antwortet die Datenbank mit einer leeren
        // Liste - das saehe aus wie "kein Mitglied" und wuerde die Gruppe
        // loeschen. Erst ein gueltiges Token macht die Antwort aussagekraeftig.
        if (!ensureFreshSession()) return null
        val userId = currentUserId() ?: return null
        return runCatching {
            client.from("members").select {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", userId)
                }
            }.decodeList<MemberDto>().isNotEmpty()
        }.getOrNull()
    }

    /**
     * Sorgt dafuer, dass Anfragen mit einem gueltigen Token hinausgehen.
     *
     * Das Zugriffstoken gilt nur eine Stunde. Liegt die App laenger im
     * Hintergrund, ist es beim Zurueckkommen abgelaufen, und die Anfrage geht
     * unangemeldet raus. Die Datenbank antwortet darauf nicht mit einem
     * Fehler, sondern mit einer leeren Liste: Die Zeilenregeln filtern alles
     * weg, weil auth.uid() null ist. Fuer die App sah das aus wie eine Gruppe,
     * aus der alle anderen verschwunden sind.
     *
     * Liefert false, wenn sich kein gueltiges Token beschaffen laesst - dann
     * darf der Aufrufer das Ergebnis einer Abfrage nicht glauben.
     */
    suspend fun ensureFreshSession(): Boolean {
        client.auth.awaitInitialization()
        val session = client.auth.currentSessionOrNull() ?: return false
        // Etwas Vorlauf: Ein Token, das waehrend der Anfrage ablaeuft, ist so
        // wertlos wie ein bereits abgelaufenes.
        if (session.expiresAt > Clock.System.now() + 60.seconds) return true
        runCatching { client.auth.refreshCurrentSession() }
        return client.auth.currentSessionOrNull() != null
    }

    /**
     * Meldet das Geraet an, falls noch keine Sitzung besteht.
     *
     * Das Warten auf die Initialisierung ist der Kern: Die gespeicherte
     * Sitzung wird nebenlaeufig geladen. Ohne dieses Warten faellt die
     * Pruefung zu frueh aus, die App meldet einen neuen anonymen Nutzer an -
     * und ueberschreibt dabei die gespeicherte Sitzung. Genau daran ging
     * bisher bei jedem Start die Gruppenzugehoerigkeit verloren.
     */
    suspend fun signInIfNeeded() {
        client.auth.awaitInitialization()

        if (client.auth.currentSessionOrNull() != null) {
            // Erst auffrischen: Ein bloss abgelaufenes Token wuerde die
            // folgende Pruefung scheitern lassen und saehe damit aus wie ein
            // geloeschter Nutzer - die App meldete sich als jemand Neues an
            // und die Gruppe waere weg.
            ensureFreshSession()

            // Die gespeicherte Sitzung kann auf einen Nutzer verweisen, den es
            // nicht mehr gibt - etwa nach einem Aufraeumen in der Datenbank.
            // Das Token bleibt bis zum Ablauf gueltig, der Fehler faellt sonst
            // erst beim Schreiben auf, als Fremdschluesselverletzung.
            val check = runCatching {
                client.auth.retrieveUserForCurrentSession(updateSession = true)
            }
            // Nur ein klares Nein des Servers beendet die Sitzung. Ein
            // Netzwerkfehler darf das nicht: Sonst kostet ein Funkloch beim
            // Start die Gruppenzugehoerigkeit.
            val serverSaysGone = check.exceptionOrNull() is RestException
            if (!serverSaysGone) return
            runCatching { client.auth.signOut() }
        }

        client.auth.signInAnonymously()
    }

    /**
     * Legt eine Gruppe an und tritt ihr bei. Die Datenbankfunktion erledigt
     * beides in einem Rutsch, damit keine Gruppe ohne Mitglied entstehen kann.
     */
    suspend fun createGroup(groupName: String, displayName: String, colorArgb: Long): GroupMembership {
        signInIfNeeded()
        val created = client.postgrest.rpc(
            function = "create_group",
            parameters = JsonObject(
                mapOf(
                    "p_group_name" to JsonPrimitive(groupName),
                    "p_display_name" to JsonPrimitive(displayName),
                    "p_color" to JsonPrimitive(colorArgb),
                )
            ),
        ).decodeList<CreatedGroupDto>().first()

        // Wer anlegt, ist Admin - das traegt die Datenbankfunktion selbst ein.
        return finishSetup(
            groupId = created.groupId,
            inviteCode = created.inviteCode,
            groupName = groupName,
            adminMemberId = null,
            forceAdmin = true,
        )
    }

    /** Tritt einer bestehenden Gruppe per Einladungscode bei. */
    suspend fun joinGroup(code: String, displayName: String, colorArgb: Long): GroupMembership {
        signInIfNeeded()
        val groupId = client.postgrest.rpc(
            function = "join_group",
            parameters = JsonObject(
                mapOf(
                    "p_code" to JsonPrimitive(code.trim().uppercase()),
                    "p_display_name" to JsonPrimitive(displayName),
                    "p_color" to JsonPrimitive(colorArgb),
                )
            ),
        ).decodeAs<String>()

        val group = client.from("groups").select {
            filter { eq("id", groupId) }
        }.decodeList<GroupDto>().firstOrNull()

        return finishSetup(
            groupId = groupId,
            inviteCode = group?.inviteCode ?: code.uppercase(),
            groupName = group?.name ?: "Gruppe",
            adminMemberId = group?.adminMemberId,
        )
    }

    /**
     * Liest die eigene Mitgliedschaft neu vom Server.
     *
     * Repariert dabei alles, was in einer aelteren App-Fassung noch nicht
     * gespeichert wurde - Adminrolle, Sichtbarkeit und vor allem die
     * Kalenderzuordnung, ohne die sich kein Spiel hervorheben laesst.
     */
    suspend fun reloadMembership(existing: GroupMembership): GroupMembership {
        val group = client.from("groups").select {
            filter { eq("id", existing.groupId) }
        }.decodeList<GroupDto>().firstOrNull()

        return finishSetup(
            groupId = existing.groupId,
            inviteCode = group?.inviteCode ?: existing.inviteCode,
            groupName = group?.name ?: existing.groupName,
            adminMemberId = group?.adminMemberId,
        )
    }

    /**
     * Erzeugt eine einmalige Einladung. Die Sichtbarkeit wird dabei
     * festgelegt und beim Beitritt uebernommen.
     */
    suspend fun createInvite(groupId: String, scope: String): String =
        client.postgrest.rpc(
            function = "create_invite",
            parameters = JsonObject(
                mapOf(
                    "p_group_id" to JsonPrimitive(groupId),
                    "p_scope" to JsonPrimitive(scope),
                )
            ),
        ).decodeAs()

    /**
     * Entfernt ein Mitglied aus der Gruppe. Die Datenbank prueft, dass nur der
     * Admin das darf - und dass er sich nicht selbst entfernt.
     */
    suspend fun removeMember(memberId: String) {
        client.postgrest.rpc(
            function = "remove_member",
            parameters = JsonObject(mapOf("p_member_id" to JsonPrimitive(memberId))),
        )
    }

    suspend fun importantMatches(groupId: String): List<ImportantMatchDto> =
        client.from("important_matches").select {
            filter { eq("group_id", groupId) }
        }.decodeList()

    /** Hebt ein Spiel hervor. Die Datenbank laesst das nur beim Admin zu. */
    suspend fun markImportant(
        membership: GroupMembership,
        calendarId: String,
        matchUid: String,
        matchTitle: String?,
    ) {
        client.from("important_matches").insert(
            ImportantMatchDto(
                groupId = membership.groupId,
                calendarId = calendarId,
                matchUid = matchUid,
                matchTitle = matchTitle,
            )
        )
    }

    suspend fun unmarkImportant(
        membership: GroupMembership,
        calendarId: String,
        matchUid: String,
    ) {
        client.from("important_matches").delete {
            filter {
                eq("group_id", membership.groupId)
                eq("calendar_id", calendarId)
                eq("match_uid", matchUid)
            }
        }
    }

    /** Merkt sich das eigene Mitglied samt Rolle und Sichtbarkeit. */
    private suspend fun finishSetup(
        groupId: String,
        inviteCode: String,
        groupName: String,
        adminMemberId: String?,
        forceAdmin: Boolean = false,
    ): GroupMembership {
        val me = ownMember(groupId)
            ?: error("Mitgliedschaft konnte nicht gelesen werden")
        return GroupMembership(
            groupId = groupId,
            memberId = me.id,
            inviteCode = inviteCode,
            groupName = groupName,
            isAdmin = forceAdmin || adminMemberId == me.id,
            scope = me.scope,
        )
    }

    /**
     * Die Kalender der Gruppe. Sie sind die gemeinsame Wahrheit darueber, was
     * die Gruppe schaut; jedes Geraet uebernimmt sie in seine Abo-Liste.
     */
    suspend fun calendars(groupId: String): List<CalendarDto> =
        client.from("calendars").select {
            filter { eq("group_id", groupId) }
        }.decodeList()

    /**
     * Legt einen Kalender in der Gruppe an. Die Datenbank laesst das nur beim
     * Admin zu und vergibt die Id - sie muss auf allen Geraeten gleich sein.
     */
    suspend fun addCalendar(
        membership: GroupMembership,
        name: String,
        url: String,
        colorArgb: Long,
    ): CalendarDto =
        client.from("calendars").insert(
            NewCalendarDto(
                groupId = membership.groupId,
                name = name,
                url = url,
                color = colorArgb,
                createdBy = membership.memberId,
            )
        ) {
            select()
        }.decodeSingle()

    /** Entfernt einen Kalender. Zusagen und Markierungen dazu verschwinden mit. */
    suspend fun removeCalendar(calendarId: String) {
        client.from("calendars").delete {
            filter { eq("id", calendarId) }
        }
    }

    /** Eigener Mitgliedseintrag in dieser Gruppe. */
    private suspend fun ownMember(groupId: String): MemberDto? {
        val userId = client.auth.currentSessionOrNull()?.user?.id ?: return null
        return client.from("members").select {
            filter {
                eq("group_id", groupId)
                eq("user_id", userId)
            }
        }.decodeList<MemberDto>().firstOrNull()
    }

    suspend fun members(groupId: String): List<MemberDto> =
        client.from("members").select {
            filter { eq("group_id", groupId) }
        }.decodeList()

    suspend fun rsvps(groupId: String): List<RsvpDto> =
        client.from("rsvps").select {
            filter { eq("group_id", groupId) }
        }.decodeList()

    /** Hinterlegt die Push-Kennung dieses Geraets. */
    suspend fun upsertDeviceToken(membership: GroupMembership, token: PushToken) {
        client.from("device_tokens").upsert(
            DeviceTokenDto(
                groupId = membership.groupId,
                memberId = membership.memberId,
                platform = token.platform,
                token = token.value,
            )
        ) {
            onConflict = "token"
        }
    }

    /** Setzt die eigene Antwort. Vorhandene wird ueberschrieben. */
    suspend fun setRsvp(
        membership: GroupMembership,
        calendarId: String,
        matchUid: String,
        status: RsvpStatus,
        comment: String?,
        matchTitle: String?,
    ) {
        client.from("rsvps").upsert(
            RsvpDto(
                groupId = membership.groupId,
                memberId = membership.memberId,
                calendarId = calendarId,
                matchUid = matchUid,
                status = status.name,
                comment = comment,
                matchTitle = matchTitle,
            )
        ) {
            onConflict = "member_id,calendar_id,match_uid"
        }
    }

    /**
     * Bittet den Server, die uebrigen Mitglieder ueber die eigene Antwort zu
     * benachrichtigen.
     *
     * Uebergeben wird nur, WELCHE Antwort gemeint ist. Wer der Absender ist und
     * was drinsteht, liest die Function selbst aus der Datenbank - so kann sich
     * niemand als jemand anderes ausgeben.
     */
    suspend fun notifyGroup(calendarId: String, matchUid: String) {
        client.functions.invoke(
            function = "rsvp-notify",
            body = JsonObject(
                mapOf(
                    "calendar_id" to JsonPrimitive(calendarId),
                    "match_uid" to JsonPrimitive(matchUid),
                )
            ),
        )
    }

    /** Nimmt die eigene Antwort zurueck. */
    suspend fun clearRsvp(membership: GroupMembership, calendarId: String, matchUid: String) {
        client.from("rsvps").delete {
            filter {
                eq("member_id", membership.memberId)
                eq("calendar_id", calendarId)
                eq("match_uid", matchUid)
            }
        }
    }

    /** Aendert Name und Farbe des eigenen Mitglieds. */
    suspend fun updateProfile(membership: GroupMembership, displayName: String, colorArgb: Long) {
        client.from("members").update(
            {
                set("display_name", displayName)
                set("color", colorArgb)
            }
        ) {
            filter { eq("id", membership.memberId) }
        }
    }
}
