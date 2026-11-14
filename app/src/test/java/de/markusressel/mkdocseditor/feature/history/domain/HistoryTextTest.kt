package de.markusressel.mkdocseditor.feature.history.domain

import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.editor.domain.ChangedLines
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.HistoryAgentModel
import de.markusressel.mkdocsrestclient.history.HistoryAuthorModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTextTest {

    private fun hunk(vararg ops: DiffOp, offset: Int? = 4) = DiffHunkModel(
        heading = "Setup",
        headingOccurrence = 2,
        oldStart = 10,
        oldLines = ops.count { it != DiffOp.ADDED },
        newStart = 12,
        newLines = ops.count { it != DiffOp.REMOVED },
        lines = ops.map { DiffLineModel(it, "x") },
        offset = offset,
    )

    @Test
    fun `a hunk opens the editor at its first added lines, relative to its heading`() {
        val location = hunk(DiffOp.CONTEXT, DiffOp.CONTEXT, DiffOp.REMOVED, DiffOp.ADDED, DiffOp.ADDED, DiffOp.CONTEXT).changeLocation()

        assertEquals(ChangeLocation(listOf("Setup"), ranges = listOf(ChangedLines(14, 2, "Setup", 2, 6))), location)
    }

    @Test
    fun `a hunk that only removed lines opens at the place they were`() {
        val location = hunk(DiffOp.CONTEXT, DiffOp.REMOVED, DiffOp.CONTEXT).changeLocation()

        assertEquals(listOf(ChangedLines(13, 0, "Setup", 2, 5)), location.ranges)
    }

    @Test
    fun `without an offset the editor opens at the section`() {
        assertEquals(ChangeLocation(listOf("Setup")), hunk(DiffOp.ADDED, offset = null).changeLocation())
    }

    private fun author(name: String, accountId: String = "", displayName: String? = null) =
        HistoryAuthorModel(name = name, email = "$name@mkdocseditor.invalid", username = name, accountId = accountId, displayName = displayName)

    private fun entry(
        author: HistoryAuthorModel = author("anna", "a1"),
        coAuthors: List<HistoryAuthorModel> = emptyList(),
        message: String = "Update guide/setup.md",
        appTokens: List<String> = emptyList(),
        agents: List<HistoryAgentModel> = emptyList(),
    ) = HistoryEntryModel("r", Date(0), author, coAuthors, message, "guide/setup.md", appTokens, agents)

    @Test
    fun `an author shows the name of the account, else the one of the commit`() {
        assertEquals("Anna", author("anna", "a1", displayName = "Anna").shownName)
        assertEquals("anna", author("anna", "a1", displayName = " ").shownName)
        assertEquals("Test", HistoryAuthorModel(name = "Test", email = "test@example.com").shownName)
    }

    @Test
    fun `a co-author who is also the author shows once`() {
        val anna = author("anna", "a1")
        assertEquals(listOf("anna", "bob"), entry(anna, listOf(author("bob", "a2"), anna)).people.map { it.shownName })
    }

    @Test
    fun `agents come from the agents list, else from the token names`() {
        assertEquals(listOf("Claude Code"), entry(agents = listOf(HistoryAgentModel("Claude Code", "a1"))).agentNames)
        assertEquals(listOf("Claude Code"), entry(appTokens = listOf("Claude Code")).agentNames)
    }

    @Test
    fun `the standard message of an edit is left out, others are shown`() {
        assertNull(entry().notableMessage)
        assertEquals("Restore guide/setup.md to 9d8af8092333", entry(message = "Restore guide/setup.md to 9d8af8092333").notableMessage)
    }
}
