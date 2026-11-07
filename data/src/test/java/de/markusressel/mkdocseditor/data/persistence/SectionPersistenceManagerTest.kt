package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SectionPersistenceManagerTest : PersistenceTestBase() {

    private fun tree() = section(
        id = "root",
        documents = listOf("d1"),
        resources = listOf("r1"),
        subsections = listOf(
            section("s1", documents = listOf("d2"), subsections = listOf(section("s2", documents = listOf("d3")))),
        ),
    )

    @Test
    fun `the whole tree is stored with its relations`() {
        sections.insertOrUpdateRoot(tree())

        val root = sections.findById("root")!!
        assertEquals(listOf("s1"), root.subsections.map { it.id })
        assertEquals(listOf("d1"), root.documents.map { it.id })
        assertEquals(listOf("r1"), root.resources.map { it.id })
        val s1 = sections.findById("s1")!!
        assertEquals("root", s1.parentSection.target?.id)
        assertEquals(listOf("s2"), s1.subsections.map { it.id })
        assertEquals("s2", document("d3").parentSection.target.id)
        assertEquals(3, sections.standardOperation().count())
        assertEquals(3, documents.standardOperation().count())
        assertEquals(1, resources.standardOperation().count())
    }

    @Test
    fun `the page metadata of a document is stored`() {
        val root = section("root")
        val document = DocumentEntity(
            id = "d1",
            name = "setup",
            title = "Getting started",
            metaTitle = "Getting started",
            description = "First steps",
            icon = "material/rocket",
            status = "new",
            tags = listOf("setup", "intro"),
            searchExclude = true,
        )
        document.parentSection.target = root
        root.documents.add(document)

        sections.insertOrUpdateRoot(root)

        val stored = document("d1")
        assertEquals("Getting started", stored.title)
        assertEquals("First steps", stored.description)
        assertEquals(listOf("setup", "intro"), stored.tags)
        assertEquals(true, stored.searchExclude)
    }

    @Test
    fun `a document without page metadata has none`() {
        sections.insertOrUpdateRoot(tree())

        val stored = document("d1")
        assertNull(stored.title)
        assertNull(stored.metaTitle)
        assertNull(stored.tags)
        assertEquals(false, stored.searchExclude)
    }

    @Test
    fun `items that are gone from the server are removed`() {
        sections.insertOrUpdateRoot(tree())

        sections.insertOrUpdateRoot(section("root", documents = listOf("d1")))

        assertEquals(listOf("root"), sections.standardOperation().all.map { it.id })
        assertEquals(listOf("d1"), documents.standardOperation().all.map { it.id })
        assertEquals(0, resources.standardOperation().count())
    }

    @Test
    fun `an empty tree removes everything below the root`() {
        sections.insertOrUpdateRoot(tree())

        sections.insertOrUpdateRoot(section("root"))

        assertEquals(1, sections.standardOperation().count())
        assertEquals(0, documents.standardOperation().count())
        assertEquals(0, resources.standardOperation().count())
    }

    @Test
    fun `a new tree keeps the cached content of a document`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d1", text = "cached")

        sections.insertOrUpdateRoot(tree())

        assertEquals("cached", document("d1").content.target?.text)
    }

    @Test
    fun `cached content is gone once its document is gone from the server`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d2", text = "cached")

        sections.insertOrUpdateRoot(section("root", documents = listOf("d1")))

        assertEquals(0, contents.standardOperation().count())
    }

    @Test
    fun `a document moved to another section keeps its cached content`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d1", text = "cached")

        sections.insertOrUpdateRoot(section("root", subsections = listOf(section("s1", documents = listOf("d1")))))

        assertEquals("s1", document("d1").parentSection.target.id)
        assertEquals("cached", document("d1").content.target?.text)
    }

    @Test
    fun `content saved for an existing document is reachable from it at once`() {
        sections.insertOrUpdateRoot(tree())

        contents.insertOrUpdate("d1", text = "cached")

        assertNotNull(document("d1").content.target)
    }

    @Test
    fun `the content points to the current document after a new tree`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d1", text = "cached")

        sections.insertOrUpdateRoot(tree())

        val content = contents.standardOperation().all.single()
        assertEquals("d1", content.documentEntity.target?.id)
    }

    @Test
    fun `saving only a part keeps the other parts of the content`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d1", text = "cached", selection = 3)

        contents.insertOrUpdate("d1", zoomLevel = 2F)

        val content = contents.standardOperation().all.single()
        assertEquals("cached", content.text)
        assertEquals(3, content.selection)
        assertEquals(2F, content.zoomLevel)
    }

    @Test
    fun `content of a document the tree does not know yet is stored without a relation`() {
        contents.insertOrUpdate("unknown", text = "cached")

        assertNull(contents.standardOperation().all.single().documentEntity.target)
    }

    @Test
    fun `a tree stored while content already exists links them when the document arrives later`() {
        contents.insertOrUpdate("d1", text = "cached")

        sections.insertOrUpdateRoot(tree())

        assertEquals("cached", document("d1").content.target?.text)
        assertEquals("d1", contents.standardOperation().all.single().documentEntity.target?.id)
    }

    @Test
    fun `a new subsection knows its parent in both directions`() {
        sections.insertOrUpdateRoot(tree())

        sections.addSubsection(sections.findById("s1")!!, section("new"))

        assertEquals("s1", sections.findById("new")!!.parentSection.target?.id)
        assertEquals(listOf("s2", "new"), sections.findById("s1")!!.subsections.map { it.id })
    }

    @Test
    fun `clearing the cache removes the tree and all content`() {
        sections.insertOrUpdateRoot(tree())
        contents.insertOrUpdate("d1", text = "cached")

        sections.deleteTreeAndContent()

        assertEquals(0, sections.standardOperation().count())
        assertEquals(0, documents.standardOperation().count())
        assertEquals(0, resources.standardOperation().count())
        assertEquals(0, contents.standardOperation().count())
    }

    @Test
    fun `removing a section by id leaves the others`() {
        sections.insertOrUpdateRoot(tree())

        sections.delete("s2")

        assertNull(sections.findById("s2"))
        assertNotNull(sections.findById("s1"))
    }
}
