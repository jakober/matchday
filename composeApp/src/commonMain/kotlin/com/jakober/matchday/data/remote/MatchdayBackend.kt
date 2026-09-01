package com.jakober.matchday.data.remote

import com.jakober.matchday.data.TeamCatalog
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.push.PushToken
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
        install(Auth)
        install(Postgrest)
        install(Functions)
    }

    /** Meldet das Geraet an, falls noch keine Sitzung besteht. */
    suspend fun signInIfNeeded() {
        if (client.auth.currentSessionOrNull() == null) {
            client.auth.signInAnonymously()
        }
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

    /**
     * Sorgt dafuer, dass beide Mannschaftskalender in der Gruppe angelegt sind,
     * und merkt sich das eigene Mitglied. Die Kalender braucht es, weil die
     * Zusagen auf sie verweisen.
     */
    private suspend fun finishSetup(
        groupId: String,
        inviteCode: String,
        groupName: String,
        adminMemberId: String?,
        forceAdmin: Boolean = false,
    ): GroupMembership {
        val calendarIds = ensureCalendars(groupId)
        val me = ownMember(groupId)
            ?: error("Mitgliedschaft konnte nicht gelesen werden")
        return GroupMembership(
            groupId = groupId,
            memberId = me.id,
            inviteCode = inviteCode,
            groupName = groupName,
            calendarIds = calendarIds,
            isAdmin = forceAdmin || adminMemberId == me.id,
            scope = me.scope,
        )
    }

    /** Legt fehlende Mannschaftskalender an und liefert die Zuordnung. */
    private suspend fun ensureCalendars(groupId: String): Map<String, String> {
        val existing = client.from("calendars").select {
            filter { eq("group_id", groupId) }
        }.decodeList<CalendarDto>()

        val missing = TeamCatalog.ALL.filter { team ->
            existing.none { it.url == team.url }
        }
        if (missing.isNotEmpty()) {
            client.from("calendars").insert(
                missing.map { team ->
                    NewCalendarDto(groupId, team.name, team.url, team.colorArgb)
                }
            )
        }

        val all = client.from("calendars").select {
            filter { eq("group_id", groupId) }
        }.decodeList<CalendarDto>()

        // Zuordnung ueber die Adresse - der Name koennte sich aendern, die
        // Kalenderquelle nicht.
        return TeamCatalog.ALL.mapNotNull { team ->
            all.firstOrNull { it.url == team.url }?.let { team.id to it.id }
        }.toMap()
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
