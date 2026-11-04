package de.markusressel.mkdocsrestclient.sync.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreeChangesTest {

    @Test
    fun `only a tree change counts as a change`() {
        assertTrue(isTreeChanged("""{"type":"tree-changed"}"""))
        assertFalse(isTreeChanged("""{"type":"heartbeat"}"""))
        assertFalse(isTreeChanged("""{"type":"online","documents":[]}"""))
    }

    @Test
    fun `an unreadable message is no change`() {
        assertFalse(isTreeChanged("not json"))
    }

    @Test
    fun `waiting grows with each attempt, up to a limit`() {
        assertEquals(1_000, reconnectDelayMs(0))
        assertEquals(2_000, reconnectDelayMs(1))
        assertEquals(30_000, reconnectDelayMs(5))
        assertEquals(30_000, reconnectDelayMs(1_000))
    }
}
