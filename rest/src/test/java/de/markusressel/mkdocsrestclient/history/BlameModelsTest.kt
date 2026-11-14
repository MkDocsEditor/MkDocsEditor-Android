package de.markusressel.mkdocsrestclient.history

import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real answers of a backend with blame and section restore (MkDocsEditor-Backend#101), the blame shortened */
class BlameModelsTest {

    private val blame = """
        {"revision":"80ad3763823b0f9963237b665ff4cfaa69a042a1ea42d771e8bb30230bb5aea2","lines":12,"blocks":[
         {"start":1,"end":2,"pending":false,"revision":"bde4a3ccafc50e7cc80407b00afe242e438f7d98","time":"2026-11-14T03:10:43Z",
          "message":"Initial","author":{"displayName":"Test","color":"#1971c2","avatar":null},"agents":[]},
         {"start":3,"end":3,"pending":false,"revision":"ef16954b863aa386884140a04a5321cb5422706a","time":"2026-11-14T03:37:00Z",
          "message":"Revert f5be247d069c on guide/setup.md",
          "author":{"accountId":"e4dheissqqro4keaag5e6f2qhq","username":"anna","displayName":"Anna","color":"#1971c2","avatar":null},"agents":[]},
         {"start":8,"end":8,"pending":true,"revision":null,"time":null,"message":null,
          "author":{"displayName":"Server","color":"#868e96","avatar":null},"agents":[]},
         {"start":12,"end":12,"pending":true,"revision":null,"time":null,"message":null,
          "author":{"accountId":"xdumn6fjdax2tjxiebt53aqymu","username":"bob","displayName":"Bob","color":"#2f9e44","avatar":null},
          "agents":["Claude Code"]}]}
    """.trimIndent().toEntity<BlameModel>()

    @Test
    fun `a committed block has its commit`() {
        val block = blame.blockAt(3)!!

        assertEquals("ef16954b863aa386884140a04a5321cb5422706a", block.revision)
        assertEquals("anna", block.author?.username)
        assertEquals(1794627420000, block.time?.time)
    }

    @Test
    fun `the author of the initial import is no account`() {
        assertNull(blame.blockAt(2)!!.author?.accountId)
        assertEquals("Test", blame.blockAt(1)!!.author?.displayName)
    }

    @Test
    fun `pending lines have no commit but their agent`() {
        val block = blame.blockAt(12)!!

        assertTrue(block.pending)
        assertNull(block.revision)
        assertNull(block.time)
        assertEquals(listOf("Claude Code"), block.agents)
    }

    @Test
    fun `lines between the blocks belong to none`() {
        assertNull(blame.blockAt(5))
        assertNull(blame.blockAt(13))
    }

    @Test
    fun `a section restore names the text without the section`() {
        val body = """{"name":"Conflict","code":"section-not-found","message":"the revision has no such section","details":{"in":"revision"}}"""

        val error = SectionRestoreErrorModel.parse(body.toByteArray())

        assertEquals(SectionRestoreErrorModel.SECTION_NOT_FOUND, error?.code)
        assertEquals(SectionNotFoundModel.REVISION, error?.details?.missingIn)
    }
}
