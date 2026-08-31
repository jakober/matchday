package com.jakober.matchday

import com.jakober.matchday.domain.MatchMood
import com.jakober.matchday.domain.Participant
import com.jakober.matchday.domain.RsvpStatus
import com.jakober.matchday.domain.moodOf
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchMoodTest {

    private fun person(name: String, status: RsvpStatus, isMe: Boolean = false) =
        Participant(id = name, name = name, colorArgb = 0xFF000000, status = status, isMe = isMe)

    @Test
    fun `ohne Antworten bleibt es offen`() {
        assertEquals(MatchMood.OPEN, moodOf(emptyList()))
    }

    @Test
    fun `eine Zusage reicht noch nicht fuer gruen`() {
        val moods = moodOf(listOf(person("Max", RsvpStatus.IN, isMe = true)))
        assertEquals(MatchMood.OPEN, moods)
    }

    @Test
    fun `ab zwei Zusagen wird es gruen`() {
        val result = moodOf(
            listOf(
                person("Max", RsvpStatus.IN, isMe = true),
                person("Anna", RsvpStatus.IN),
            )
        )
        assertEquals(MatchMood.ENOUGH_IN, result)
    }

    @Test
    fun `eine Absage allein faerbt rot`() {
        val result = moodOf(listOf(person("Max", RsvpStatus.OUT, isMe = true)))
        assertEquals(MatchMood.DECLINED, result)
    }

    @Test
    fun `genug Zusagen schlagen eine Absage`() {
        // Wenn zwei mitkommen, findet es statt - auch wenn ein Dritter passt.
        val result = moodOf(
            listOf(
                person("Max", RsvpStatus.IN),
                person("Anna", RsvpStatus.IN),
                person("Ben", RsvpStatus.OUT),
            )
        )
        assertEquals(MatchMood.ENOUGH_IN, result)
    }

    @Test
    fun `eine Zusage und eine Absage bleibt rot`() {
        val result = moodOf(
            listOf(
                person("Max", RsvpStatus.IN),
                person("Ben", RsvpStatus.OUT),
            )
        )
        assertEquals(MatchMood.DECLINED, result)
    }
}
