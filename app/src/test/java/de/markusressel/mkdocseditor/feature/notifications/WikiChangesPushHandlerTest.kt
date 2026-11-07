package de.markusressel.mkdocseditor.feature.notifications

import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesCheck
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class WikiChangesPushHandlerTest {

    private var checks = 0
    private val handler = WikiChangesPushHandler(WikiChangesCheck { checks++ })

    @Test
    fun `a feed change runs the check`() {
        handler.onMessage("""{"type":"feed-changed","entryId":"42"}""".toByteArray(), decrypted = true)

        assertEquals(1, checks)
    }

    @Test
    fun `other types are ignored`() {
        handler.onMessage("""{"type":"something-new"}""".toByteArray(), decrypted = true)
        handler.onMessage("not json".toByteArray(), decrypted = true)

        assertEquals(0, checks)
    }

    @Test
    fun `a message that could not be decrypted is not from the backend`() {
        handler.onMessage("""{"type":"feed-changed"}""".toByteArray(), decrypted = false)

        assertEquals(0, checks)
    }
}
