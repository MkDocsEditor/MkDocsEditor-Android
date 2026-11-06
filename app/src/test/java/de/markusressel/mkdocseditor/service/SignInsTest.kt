package de.markusressel.mkdocseditor.service

import kotlin.test.Test
import kotlin.test.assertEquals

class SignInsTest {

    private val sessions = mutableSetOf<Long>()
    private val signIns = SignIns { it in sessions }

    @Test
    fun `a new session is a sign-in`() {
        sessions += 1

        assertEquals(true, signIns.onSessionChanged(1))
    }

    @Test
    fun `a renewed token is no new sign-in`() {
        sessions += 1
        signIns.onSessionChanged(1)

        assertEquals(false, signIns.onSessionChanged(1))
    }

    @Test
    fun `signing out and in again is a sign-in again`() {
        sessions += 1
        signIns.onSessionChanged(1)
        sessions -= 1
        assertEquals(false, signIns.onSessionChanged(1))
        sessions += 1

        assertEquals(true, signIns.onSessionChanged(1))
    }

    @Test
    fun `servers count on their own`() {
        sessions += setOf(1L, 2L)
        signIns.onSessionChanged(1)

        assertEquals(true, signIns.onSessionChanged(2))
    }
}
