package de.markusressel.mkdocsrestclient.history

import de.markusressel.mkdocsrestclient.reference.ApiErrorModel
import de.markusressel.mkdocsrestclient.toEntity
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Real answers of a backend with B2: Carol saw the page, then Alice added a section and Bob's token changed a line */
class ChangesModelsTest {

    @Test
    fun `changes since the last look are read with their authors, agents and hunks`() {
        val changes = """
            {"since":{"time":"2026-11-14T03:00:24.962Z","seenToken":"ab181433912a1fe6082d0247c32d137940b7bd532f841748418ffa06af8b11d2"},"seenToken":"b62168b4464c85c3b78186f3af9f4d83c12361060c7087a7753ce26851115a3b","authors":[{"accountId":"5y7wrjicdbpk5b3lo7xzewblee","username":"alice","displayName":"alice","color":"#e8590c","avatar":null,"agents":[]},{"accountId":"nogzraprjnjhwib3z42cub4qia","username":"bob","displayName":"bob","color":"#2f9e44","avatar":null,"agents":["Claude Code"]}],"hunks":[{"heading":"Setup","headingOccurrence":1,"offset":0,"oldStart":1,"oldLines":3,"newStart":1,"newLines":7,"lines":[{"op":" ","text":"# Setup"},{"op":" ","text":""},{"op":"-","text":"Some text."},{"op":"+","text":"Some better text."},{"op":"+","text":""},{"op":"+","text":"## Install"},{"op":"+","text":""},{"op":"+","text":"Run the installer."}]}],"summary":{"linesAdded":5,"linesRemoved":1,"headings":["Setup","Install"]}}
        """.trimIndent().toEntity<DocumentChangesModel>()

        assertEquals(Date.from(Instant.parse("2026-11-14T03:00:24.962Z")), changes.since?.time)
        assertEquals("b62168b4464c85c3b78186f3af9f4d83c12361060c7087a7753ce26851115a3b", changes.seenToken)
        assertEquals(listOf("alice", "bob"), changes.authors.map { it.username })
        assertEquals(listOf("Claude Code"), changes.authors[1].agents)
        assertEquals(8, changes.hunks.single().lines.size)
        assertEquals(HistorySummaryModel(5, 1, listOf("Setup", "Install")), changes.summary)
    }

    @Test
    fun `a page that never was seen has no since and no hunks`() {
        val changes = """
            {"since":null,"seenToken":"b62168b4","authors":[],"hunks":[],"summary":{"linesAdded":0,"linesRemoved":0,"headings":[]}}
        """.trimIndent().toEntity<DocumentChangesModel>()

        assertNull(changes.since)
        assertEquals(emptyList(), changes.hunks)
    }

    @Test
    fun `a token the document no longer knows is told apart by its code`() {
        val body = """{"name":"Conflict","code":"unknown-revision","message":"the document does not know this revision, get the changes again"}"""

        assertEquals("unknown-revision", ApiErrorModel.parse(body.toByteArray())?.code)
    }
}
