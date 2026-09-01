package com.jakober.matchday.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Abbilder der Datenbanktabellen. Die Spalten heissen dort mit Unterstrich,
 * im Kotlin-Code bleibt es bei der ueblichen Schreibweise - dafuer die
 * SerialName-Angaben.
 */

@Serializable
data class MemberDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val color: Long,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class CalendarDto(
    val id: String,
    val name: String,
    val url: String,
    val color: Long,
)

@Serializable
data class RsvpDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("calendar_id") val calendarId: String,
    @SerialName("match_uid") val matchUid: String,
    val status: String,
    val comment: String? = null,
    /**
     * Titel der Begegnung, mitgeschickt statt nachgeschlagen: Die Datenbank
     * kennt keine Spielplaene, und die Benachrichtigung soll sagen, um welches
     * Spiel es geht.
     */
    @SerialName("match_title") val matchTitle: String? = null,
)

/** Adresse, an die ein Geraet Benachrichtigungen empfangen kann. */
@Serializable
data class DeviceTokenDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("member_id") val memberId: String,
    val platform: String,
    val token: String,
)

/** Neuer Kalendereintrag - ohne id, die vergibt die Datenbank. */
@Serializable
data class NewCalendarDto(
    @SerialName("group_id") val groupId: String,
    val name: String,
    val url: String,
    val color: Long,
)

/** Rueckgabe von create_group. */
@Serializable
data class CreatedGroupDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("invite_code") val inviteCode: String,
)

/** Was die App lokal ueber ihre Gruppe wissen muss. */
@Serializable
data class GroupMembership(
    val groupId: String,
    val memberId: String,
    val inviteCode: String,
    val groupName: String,
    /** Kalender-Id je Mannschaft, z.B. "fcbayern" -> UUID. */
    val calendarIds: Map<String, String> = emptyMap(),
)
