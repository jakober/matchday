package com.jakober.matchday.data.remote

import com.jakober.matchday.i18n.S
import com.jakober.matchday.i18n.currentLocale
import com.jakober.matchday.data.createSettings
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.push.PushToken
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/** Fehler der Anmeldung, mit einer Meldung fuer den Bildschirm. */
class AuthException(message: String) : Exception(message)

/** Ergebnis einer angenommenen Einladung. */
data class AcceptedInvite(val email: String, val name: String)

/**
 * Abgelehnte Einladung. Die beiden Kennzeichen sagen der App, wohin sie den
 * Nutzer stattdessen schickt: zur Anmeldung oder zur Registrierung.
 */
class AcceptInviteException(
    message: String,
    val accountExists: Boolean,
    val needsSignup: Boolean,
) : Exception(message)

/**
 * Anbindung an Supabase.
 *
 * Jeder Nutzer hat ein Konto mit E-Mail und Passwort; die Zugehoerigkeit zu
 * einer Gruppe haengt am Konto, nicht am Geraet. Wer was sehen und aendern
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

    // -- Konto --------------------------------------------------------------

    /**
     * Stellt die gespeicherte Sitzung wieder her. Liefert false, wenn keine
     * besteht oder der Server sie eindeutig ablehnt - dann muss sich der
     * Nutzer anmelden.
     *
     * Das Warten auf die Initialisierung ist der Kern: Die gespeicherte
     * Sitzung wird nebenlaeufig geladen; ohne dieses Warten saehe die App
     * faelschlich "nicht angemeldet".
     */
    suspend fun restoreSession(): Boolean {
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) return false

        // Erst auffrischen: Ein bloss abgelaufenes Token wuerde die folgende
        // Pruefung scheitern lassen und saehe aus wie ein geloeschtes Konto.
        ensureFreshSession()

        // Nur ein klares Nein des Servers beendet die Sitzung. Ein
        // Netzwerkfehler darf das nicht: Sonst wirft ein Funkloch beim Start
        // den Nutzer aus der App.
        val check = runCatching {
            client.auth.retrieveUserForCurrentSession(updateSession = true)
        }
        if (check.exceptionOrNull() is RestException) {
            runCatching { client.auth.signOut() }
            return false
        }
        return true
    }

    /** E-Mail-Adresse des angemeldeten Kontos. */
    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    /**
     * Legt ein Konto an. Die Bestaetigung ist auf dem Server Pflicht: Es gibt
     * danach noch keine Sitzung, erst der Code aus der Mail schaltet sie frei.
     */
    suspend fun signUp(email: String, password: String) {
        authCall {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        authCall {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }
    }

    /** Bestaetigt die Adresse mit dem Code aus der Mail; danach besteht eine Sitzung. */
    suspend fun confirmEmail(email: String, code: String) {
        authCall {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.SIGNUP,
                email = email,
                token = code.trim(),
            )
        }
    }

    suspend fun resendCode(email: String) {
        authCall { client.auth.resendEmail(OtpType.Email.SIGNUP, email) }
    }

    /** Schickt die Mail mit dem Code zum Zuruecksetzen des Passworts. */
    suspend fun sendPasswordReset(email: String) {
        authCall { client.auth.resetPasswordForEmail(email) }
    }

    /** Loest den Code aus der Zuruecksetz-Mail ein; danach besteht eine Sitzung. */
    suspend fun verifyRecovery(email: String, code: String) {
        authCall {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email,
                token = code.trim(),
            )
        }
    }

    /** Setzt ein neues Passwort fuer die bestehende Sitzung. */
    suspend fun updatePassword(password: String) {
        authCall { client.auth.updateUser { this.password = password } }
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }

    /**
     * Loescht das Konto auf dem Server. Beide Stores verlangen das fuer
     * jede App mit Registrierung. Die Function loescht den Nutzer, an dem
     * das Token haengt; die Datenbank raeumt den Rest per Cascade weg.
     */
    suspend fun deleteAccount() {
        ensureFreshSession()
        val response = client.functions.invoke(function = "account-delete")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (body["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw IllegalStateException(body["error"]?.jsonPrimitive?.contentOrNull ?: S.errServerNoAnswer)
        }
        runCatching { client.auth.signOut() }
    }

    /**
     * Uebersetzt die Meldungen des Anmeldedienstes. Sie sind englisch und
     * technisch, landen aber unveraendert auf dem Bildschirm.
     */
    private suspend fun authCall(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: RestException) {
            val raw = (e.message ?: "").lowercase()
            val friendly = when {
                "invalid login" in raw || "invalid_credentials" in raw ->
                    S.errInvalidLogin
                "not confirmed" in raw || "email_not_confirmed" in raw ->
                    UNCONFIRMED
                "already registered" in raw || "user_already_exists" in raw ->
                    S.errAlreadyRegistered
                "at least" in raw || "weak_password" in raw ->
                    S.errWeakPassword
                "expired" in raw || "invalid" in raw && "token" in raw || "otp_expired" in raw ->
                    S.errCodeInvalid
                "rate limit" in raw || "over_email_send_rate" in raw ->
                    S.errRateLimit
                "invalid email" in raw || "validation_failed" in raw ->
                    S.errInvalidEmail
                else -> S.errAuthGeneric(e.message)
            }
            throw AuthException(friendly)
        }
    }

    companion object {
        /** Kennung dafuer, dass das Konto existiert, aber die Adresse noch unbestaetigt ist. */
        const val UNCONFIRMED = "E-Mail-Adresse noch nicht bestätigt"
    }

    /**
     * Legt eine Gruppe an und tritt ihr bei. Die Datenbankfunktion erledigt
     * beides in einem Rutsch, damit keine Gruppe ohne Mitglied entstehen kann.
     */
    suspend fun createGroup(groupName: String, displayName: String, colorArgb: Long): GroupMembership {
        ensureFreshSession()
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
        ensureFreshSession()
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
     * Erzeugt eine Einladung und laesst den Server den Code per Mail
     * verschicken. Liefert den Code - er wird auch angezeigt, damit eine
     * vertippte Adresse die Einladung nicht verschluckt.
     *
     * Erwartbare Fehler kommen als Meldung im JSON, nicht als HTTP-Fehler;
     * sie sind fuer den Bildschirm geschrieben.
     */
    suspend fun sendInvite(groupId: String, scope: String, email: String, name: String): SentInvite {
        val response = client.functions.invoke(
            function = "invite-send",
            body = JsonObject(
                mapOf(
                    "group_id" to JsonPrimitive(groupId),
                    "scope" to JsonPrimitive(scope),
                    "email" to JsonPrimitive(email),
                    "name" to JsonPrimitive(name),
                    "locale" to JsonPrimitive(currentLocale),
                )
            ),
        )
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val code = json["code"]?.jsonPrimitive?.contentOrNull
        val error = json["error"]?.jsonPrimitive?.contentOrNull
        if (code == null) error(error ?: S.errInviteFailed)
        return SentInvite(code = code, sentTo = json["sent_to"]?.jsonPrimitive?.contentOrNull, warning = error)
    }

    /**
     * Nimmt eine Einladung mit hinterlegtem Namen an: Der Server legt das
     * Konto an und traegt die Person in die Gruppe ein. Liefert die Adresse,
     * mit der sich die App anschliessend anmeldet.
     */
    suspend fun acceptInvite(code: String, password: String): AcceptedInvite {
        val response = client.functions.invoke(
            function = "accept-invite",
            body = JsonObject(
                mapOf(
                    "code" to JsonPrimitive(code),
                    "password" to JsonPrimitive(password),
                    "locale" to JsonPrimitive(currentLocale),
                )
            ),
        )
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val ok = json["ok"]?.jsonPrimitive?.booleanOrNull == true
        if (!ok) {
            throw AcceptInviteException(
                message = json["error"]?.jsonPrimitive?.contentOrNull ?: S.failed,
                accountExists = json["exists"]?.jsonPrimitive?.booleanOrNull == true,
                needsSignup = json["needs_signup"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
        return AcceptedInvite(
            email = json["email"]?.jsonPrimitive?.content ?: error(S.failed),
            name = json["name"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /**
     * Findet die Gruppe des angemeldeten Kontos - nach einer Neuinstallation
     * oder auf einem zweiten Geraet. Die Zugehoerigkeit haengt am Konto, das
     * Geraet muss sie nur wiederfinden.
     */
    suspend fun membershipOfCurrentUser(): GroupMembership? {
        val userId = currentUserId() ?: return null
        val me = client.from("members").select {
            filter { eq("user_id", userId) }
        }.decodeList<MemberDto>().firstOrNull() ?: return null
        val groupId = me.groupId ?: return null
        val group = client.from("groups").select {
            filter { eq("id", groupId) }
        }.decodeList<GroupDto>().firstOrNull() ?: return null
        return finishSetup(
            groupId = groupId,
            inviteCode = group.inviteCode,
            groupName = group.name,
            adminMemberId = group.adminMemberId,
        )
    }

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
            ?: error(S.errMembershipRead)
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

    /**
     * Loest Mannschaftsnamen in Wappen auf. Der Server haelt den
     * Zwischenspeicher und fragt den Wappendienst - nicht die App, damit
     * jeder Name genau einmal nachgeschlagen wird statt einmal je Geraet.
     * Ein Name ohne Treffer kommt als null zurueck.
     */
    suspend fun resolveLogos(names: List<String>): Map<String, String?> {
        val response = client.functions.invoke(
            function = "team-logo",
            body = JsonObject(mapOf("names" to JsonArray(names.map { JsonPrimitive(it) }))),
        )
        val logos = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["logos"]?.jsonObject ?: return emptyMap()
        return logos.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull }
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

    /**
     * Hinterlegt die Geraetesprache beim eigenen Mitglied, damit der Server
     * Push-Meldungen in der richtigen Sprache schreibt.
     */
    suspend fun updateLocale(membership: GroupMembership, locale: String) {
        client.from("members").update(
            { set("locale", locale) }
        ) {
            filter { eq("id", membership.memberId) }
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
