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

    /** a real answer of a backend with B1: Alice added an Install section, then Bob's token "Claude Code" changed two lines */
    @Test
    fun `a structured diff is read with the heading and offset of each hunk`() {
        val diff = """
            {"from":"61939a9de3616d776742b10f2cafd2357eae53ae","to":"837116f685b782ae635c9cb38dd53842d0f67506","hunks":[{"heading":"Setup","headingOccurrence":1,"offset":0,"oldStart":1,"oldLines":8,"newStart":1,"newLines":8,"lines":[{"op":" ","text":"# Setup"},{"op":" ","text":""},{"op":"-","text":"Some text."},{"op":"+","text":"Some better text."},{"op":" ","text":""},{"op":" ","text":"## Install"},{"op":" ","text":""},{"op":" ","text":"Run the installer."},{"op":"-","text":"Then reboot."},{"op":"+","text":"Then reboot twice."}]}]}
        """.trimIndent().toEntity<HistoryDiffModel>()

        val hunk = diff.hunks.single()
        assertEquals("Setup", hunk.heading)
        assertEquals(1, hunk.headingOccurrence)
        assertEquals(0, hunk.offset)
        assertEquals(8, hunk.newLines)
        assertEquals(DiffLineModel(DiffOp.REMOVED, "Some text."), hunk.lines[2])
        assertEquals(DiffLineModel(DiffOp.ADDED, "Some better text."), hunk.lines[3])
        assertEquals(DiffOp.CONTEXT, hunk.lines[1].op)
    }

    @Test
    fun `an entry of a backend with B1 has display data, agents and a summary`() {
        val entry = """
            {"revision":"837116f685b782ae635c9cb38dd53842d0f67506","time":"2026-11-14T04:10:00+01:00",
             "author":{"name":"bob","email":"bob@mkdocseditor.invalid","username":"bob","accountId":"45sqchh6te3waahhswaf573gyi","displayName":"bob","color":"#2f9e44","avatar":null},
             "coAuthors":[{"name":"Test","email":"test@example.com","username":"","accountId":"","displayName":"Test","color":"#1971c2","avatar":null}],
             "message":"Update guide/setup.md","path":"guide/setup.md",
             "appTokens":["Claude Code"],"agents":[{"name":"Claude Code","accountId":"45sqchh6te3waahhswaf573gyi"}],
             "summary":{"linesAdded":2,"linesRemoved":2,"headings":["Setup","Install"]}}
        """.trimIndent().toEntity<HistoryEntryModel>()

        assertEquals("#2f9e44", entry.author.color)
        assertEquals(null, entry.author.avatar)
        assertEquals("Test", entry.coAuthors.single().displayName)
        assertFalse(entry.coAuthors.single().isAccount)
        assertEquals(listOf(HistoryAgentModel("Claude Code", "45sqchh6te3waahhswaf573gyi")), entry.agents)
        assertEquals(HistorySummaryModel(2, 2, listOf("Setup", "Install")), entry.summary)
    }
}
