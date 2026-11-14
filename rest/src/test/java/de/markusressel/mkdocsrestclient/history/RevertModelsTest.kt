package de.markusressel.mkdocsrestclient.history

import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real answers of a backend with revert (MkDocsEditor-Backend#99) */
class RevertModelsTest {

    @Test
    fun `a revert answers with the entry of its commit`() {
        val entry = """
            {"revision":"ef16954b863aa386884140a04a5321cb5422706a","time":"2026-11-14T04:37:00+01:00",
             "author":{"name":"Anna","email":"anna@mkdocseditor.invalid","username":"anna","accountId":"e4dheissqqro4keaag5e6f2qhq","displayName":"Anna","color":"#1971c2","avatar":null},
             "coAuthors":[],"message":"Revert f5be247d069c on guide/setup.md","path":"guide/setup.md","appTokens":[],"agents":[],
             "summary":{"linesAdded":5,"linesRemoved":1,"headings":["Setup","Install","Troubleshooting"]}}
        """.trimIndent().toEntity<HistoryEntryModel>()

        assertEquals("Revert f5be247d069c on guide/setup.md", entry.message)
        assertEquals("anna", entry.author.username)
    }

    @Test
    fun `a conflict names the headings where later edits are in the way`() {
        val body = """
            {"name":"Conflict","code":"revert-conflict","message":"later edits changed the same lines, edit the document by hand",
             "details":{"conflicts":[{"heading":"Setup","headingOccurrence":1,"line":3}],"headings":["Setup"]}}
        """.trimIndent()

        val error = RevertErrorModel.parse(body.toByteArray())

        assertEquals(RevertErrorModel.CONFLICT, error?.code)
        assertEquals(listOf<String?>("Setup"), error?.details?.headings)
    }

    @Test
    fun `a check tells whether a revert would work, and why not`() {
        // real answers of a backend with revert-check (MkDocsEditor-Backend#104)
        assertTrue("""{"possible":true}""".toEntity<RevertCheckModel>().possible)

        val gone = """{"possible":false,"code":"nothing-to-revert"}""".toEntity<RevertCheckModel>()
        assertEquals(RevertErrorModel.NOTHING_TO_REVERT, gone.code)
        assertNull(gone.details)

        val conflict = """
            {"possible":false,"code":"revert-conflict",
             "details":{"conflicts":[{"heading":"Install","headingOccurrence":1,"line":7}],"headings":["Install"]}}
        """.trimIndent().toEntity<RevertCheckModel>()
        assertEquals(RevertErrorModel.CONFLICT, conflict.code)
        assertEquals(listOf<String?>("Install"), conflict.details?.headings)
    }

    @Test
    fun `a change that is gone already has no details`() {
        val body = """{"name":"Conflict","code":"nothing-to-revert","message":"the change of this revision is not in the document anymore"}"""

        val error = RevertErrorModel.parse(body.toByteArray())

        assertEquals(RevertErrorModel.NOTHING_TO_REVERT, error?.code)
        assertNull(error?.details)
    }
}
