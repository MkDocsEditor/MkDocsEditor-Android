package de.markusressel.mkdocsrestclient.reference

import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class MovedListModelTest {

    @Test
    fun `reads the answer of the server`() {
        val answer = """
            {"moved": [{
              "oldId": "577",
              "type": "document",
              "item": {"type": "document", "id": "130", "path": "Handbuch/guide/setup.md", "name": "setup"},
              "movedAt": "2026-11-01T10:00:00Z"
            }]}
        """.trimIndent().toEntity<MovedListModel>()

        val moved = answer.moved.single()
        assertEquals("577", moved.oldId)
        assertEquals(ItemType.DOCUMENT, moved.item.type)
        assertEquals("130", moved.item.id)
    }

    @Test
    fun `reads an answer without moved items`() {
        assertEquals(emptyList(), """{"moved": []}""".toEntity<MovedListModel>().moved)
    }
}
