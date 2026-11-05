package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.data.ClientInstanceId
import de.markusressel.mkdocseditor.feature.search.InMemoryKeyValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientInstanceIdTest {

    @Test
    fun `every editor and every start of the app is the same instance, so coming back ends the ghost`() {
        val storage = InMemoryKeyValues()

        val first = ClientInstanceId(storage).value

        assertTrue(first.matches(Regex("^[0-9a-f]{32}$")), first)
        assertEquals(first, ClientInstanceId(storage).value)
    }
}
