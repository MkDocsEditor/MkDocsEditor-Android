package de.markusressel.mkdocseditor.auth.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushMessagesTest {

    @Test
    fun `the type is read, other fields are ignored`() {
        val payload = """{"entryId": "42", "x": {"a": 1}, "type": "feed-changed"}""".toByteArray()
        assertEquals(PushMessages.FEED_CHANGED, PushMessages.type(payload))
    }

    @Test
    fun `a payload that is no object with a type has none`() {
        assertNull(PushMessages.type("""{"entryId": "42"}""".toByteArray()))
        assertNull(PushMessages.type("""{"type": 1}""".toByteArray()))
        assertNull(PushMessages.type("not json".toByteArray()))
        assertNull(PushMessages.type(ByteArray(0)))
    }
}
