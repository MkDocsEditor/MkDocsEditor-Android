package de.markusressel.mkdocsrestclient.history

import de.markusressel.mkdocsrestclient.toEntity
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryModelsTest {

    /** the answer of a real backend (TestHistory setup in MkDocsEditor-Backend), only the account ids changed */
    private val history = """
        [
          {
            "revision": "bfc59905fcb742f8160345f1047b33056386ff17",
            "time": "2026-11-14T03:45:33+01:00",
            "author": {
              "name": "alice",
              "email": "alice@mkdocseditor.invalid",
              "username": "alice",
              "accountId": "a1"
            },
            "coAuthors": [
              {
                "name": "bob",
                "email": "bob@mkdocseditor.invalid",
                "username": "bob",
                "accountId": "a2"
              }
            ],
            "message": "Update guide/setup.md",
            "path": "guide/setup.md",
            "appTokens": ["Claude Code"]
          },
          {
            "revision": "9d8af80923338d9e86ebbdf016f547b96089cc07",
            "time": "2026-11-14T03:45:32+01:00",
            "author": {
              "name": "Test",
              "email": "test@example.com",
              "username": "",
              "accountId": ""
            },
            "coAuthors": [],
            "message": "Initial",
            "path": "guide/setup.md",
            "appTokens": []
          }
        ]
    """.trimIndent()

    @Test
    fun `a history of the backend is read with all its fields`() {
        val entries = history.toEntity<Array<HistoryEntryModel>>().toList()

        val update = entries[0]
        assertEquals("bfc59905fcb742f8160345f1047b33056386ff17", update.revision)
        assertEquals(Date.from(Instant.parse("2026-11-14T02:45:33Z")), update.time)
        assertEquals("alice", update.author.username)
        assertTrue(update.author.isAccount)
        assertEquals(listOf("bob"), update.coAuthors.map { it.username })
        assertEquals(listOf("Claude Code"), update.appTokens)
        assertEquals("guide/setup.md", update.path)
    }

    @Test
    fun `an author who is not an account comes with empty strings`() {
        val initial = history.toEntity<Array<HistoryEntryModel>>()[1]

        assertFalse(initial.author.isAccount)
        assertEquals("", initial.author.username)
        assertEquals(emptyList(), initial.coAuthors)
        assertEquals(emptyList(), initial.appTokens)
    }
}
