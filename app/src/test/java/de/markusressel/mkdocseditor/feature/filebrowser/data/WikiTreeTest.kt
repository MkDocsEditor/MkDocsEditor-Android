package de.markusressel.mkdocseditor.feature.filebrowser.data

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikiTreeTest {

    companion object {
        fun doc(id: String, name: String) = DocumentData(0, id, name, 0, Date(0), "$name/", null, false)
        fun res(id: String, name: String) = ResourceData(0, id, name, 0, Date(0), false)
        fun section(
            id: String,
            name: String,
            subsections: List<SectionData> = emptyList(),
            documents: List<DocumentData> = emptyList(),
            resources: List<ResourceData> = emptyList(),
        ) = SectionData(0, id, name, subsections, documents, resources)

        /**
         * root: index.md, logo.png
         *   guide: index.md, setup.md, img/ (photo 1.png)
         *   My Notes: Küche.md
         */
        val img = section("s-img", "img", resources = listOf(res("r-photo", "photo 1.png")))
        val guide = section(
            "s-guide", "guide",
            subsections = listOf(img),
            documents = listOf(doc("d-guide-index", "index"), doc("d-setup", "setup")),
        )
        val notes = section("s-notes", "My Notes", documents = listOf(doc("d-kitchen", "Küche")))
        val root = section(
            "root", "root",
            subsections = listOf(guide, notes),
            documents = listOf(doc("d-index", "index")),
            resources = listOf(res("r-logo", "logo.png")),
        )
        val tree = WikiTree(root)
    }

    @Test
    fun `resolves relative documents resources and sections`() {
        assertEquals("d-setup", tree.resolve(guide, "setup.md")?.id)
        assertEquals("d-setup", tree.resolve(guide, "./setup.md#install")?.id)
        assertEquals("d-index", tree.resolve(guide, "../index.md")?.id)
        assertEquals("r-logo", tree.resolve(guide, "../logo.png")?.id)
        assertEquals("r-photo", tree.resolve(guide, "img/photo%201.png")?.id)
        assertEquals("r-photo", tree.resolve(guide, "img/photo 1.png")?.id)
        assertEquals("d-kitchen", tree.resolve(guide, "../My%20Notes/Küche.md")?.id)
        assertEquals("s-img", tree.resolve(guide, "img")?.id)
    }

    @Test
    fun `resolves absolute paths from the root`() {
        assertEquals("r-photo", tree.resolve(notes, "/guide/img/photo%201.png")?.id)
        assertEquals("d-index", tree.resolve(notes, "/index.md")?.id)
    }

    @Test
    fun `directory urls point to the index or a document`() {
        assertEquals("d-guide-index", tree.resolve(root, "guide/")?.id)
        assertEquals("d-setup", tree.resolve(guide, "setup/")?.id)
        assertEquals(WikiItem.Section(notes), tree.resolve(root, "My%20Notes/"))
    }

    @Test
    fun `external links anchors and missing targets resolve to nothing`() {
        assertNull(tree.resolve(root, "https://example.com/a.md"))
        assertNull(tree.resolve(root, "mailto:a@b.c"))
        assertNull(tree.resolve(root, "//cdn.example.com/x.png"))
        assertNull(tree.resolve(root, "#heading"))
        assertNull(tree.resolve(root, "missing.md"))
        assertNull(tree.resolve(root, "../outside.md"))
        assertNull(tree.resolve(root, "bad%zzescape.md"))
    }

    @Test
    fun `paths of items`() {
        assertEquals("guide/img", tree.pathOf(img))
        assertEquals("", tree.pathOf(root))
        assertEquals("guide/img/photo 1.png", tree.pathOf(WikiItem.Resource(img.resources.first())))
        assertEquals("My Notes/Küche.md", tree.pathOf(WikiItem.Document(notes.documents.first())))
    }

    @Test
    fun `relative links between items`() {
        val photo = WikiItem.Resource(img.resources.first())
        val kitchen = WikiItem.Document(notes.documents.first())
        assertEquals("img/photo%201.png", tree.relativeLink(guide, photo))
        assertEquals("../guide/img/photo%201.png", tree.relativeLink(notes, photo))
        assertEquals("../My%20Notes/Küche.md", tree.relativeLink(guide, kitchen))
        assertEquals("setup.md", tree.relativeLink(guide, WikiItem.Document(guide.documents[1])))
        assertEquals("../index.md", tree.relativeLink(guide, WikiItem.Document(root.documents.first())))
        assertEquals("guide/", tree.relativeLink(root, WikiItem.Section(guide)))
        assertEquals("../", tree.relativeLink(img, WikiItem.Section(guide)))
        assertEquals("./", tree.relativeLink(guide, WikiItem.Section(guide)))
    }

    @Test
    fun `relative links resolve back to their target`() {
        tree.allItems().forEach { target ->
            listOf(root, guide, img, notes).forEach { from ->
                val link = tree.relativeLink(from, target)
                val resolved = tree.resolve(from, link)
                val expected = if (target is WikiItem.Section && target.section.documents.any { it.name == "index" }) {
                    target.section.documents.first { it.name == "index" }.id
                } else {
                    target.id
                }
                assertEquals(expected, resolved?.id, "$link from ${from.name}")
            }
        }
    }

    @Test
    fun `parents`() {
        assertEquals(guide, tree.parentOfDocument("d-setup"))
        assertEquals(img, tree.parentOfResource("r-photo"))
        assertEquals(guide, tree.parentOfSection("s-img"))
        assertNull(tree.parentOfSection("root"))
    }
}
