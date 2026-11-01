package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinkTarget
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinks
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviewLinksTest {

    private val tree = WikiTree(
        sectionData(
            "root",
            subsections = listOf(
                sectionData(
                    "guide",
                    documents = listOf(documentData("page"), documentData("other")),
                    resources = listOf(resourceData("image", "a.png")),
                )
            ),
        )
    )

    private fun resolve(href: String) = PreviewLinks.resolve(tree, "page", href)

    @Test
    fun `an external link needs no tree`() {
        assertEquals(
            PreviewLinkTarget.External("https://example.com"),
            PreviewLinks.resolve(null, null, "https://example.com"),
        )
    }

    @Test
    fun `a wiki link needs the tree and the document`() {
        assertNull(PreviewLinks.resolve(null, "page", "other.md"))
        assertNull(PreviewLinks.resolve(tree, null, "other.md"))
    }

    @Test
    fun `wiki links resolve relative to the folder of the document`() {
        assertEquals(PreviewLinkTarget.Document("other"), resolve("other.md"))
        assertEquals(PreviewLinkTarget.Resource("image", "a.png"), resolve("a.png"))
    }

    @Test
    fun `a link to nothing says so`() {
        assertEquals(PreviewLinkTarget.NotFound, resolve("missing.md"))
    }
}
