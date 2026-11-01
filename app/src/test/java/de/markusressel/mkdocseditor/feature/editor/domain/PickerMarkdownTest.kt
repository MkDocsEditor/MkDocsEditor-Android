package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.res
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickerMarkdownTest {

    /**
     * root: index.md, Strand (2).png, notes.pdf
     *   Rezepte: Kürbissuppe.md, Apfelkuchen.md
     */
    private val rezepte = section("s-r", "Rezepte", documents = listOf(doc("d-suppe", "Kürbissuppe"), doc("d-apfel", "Apfelkuchen")))
    private val root = section(
        "root", "root",
        subsections = listOf(rezepte),
        documents = listOf(doc("d-index", "index")),
        resources = listOf(res("r-strand", "Strand (2).png"), res("r-pdf", "notes.pdf")),
    )
    private val tree = WikiTree(root)
    private val from = "Rezepte/Apfelkuchen.md"

    @Test
    fun `links to pages, headings and folders`() {
        val suppe = WikiItem.Document(rezepte.documents[0])
        assertEquals("[Kürbissuppe](Kürbissuppe.md)", PickerMarkdown.markdownLink(tree, from, suppe, asLink = true))
        assertEquals(
            "[Zutaten](Kürbissuppe.md#zutaten)",
            PickerMarkdown.markdownLink(tree, from, suppe, text = "Zutaten", anchor = "zutaten", asLink = true),
        )
        assertEquals("[Teig](#teig)", PickerMarkdown.markdownLink(tree, from, WikiItem.Document(rezepte.documents[1]), "Teig", "teig", true))
        assertEquals("[root](../index.md)", PickerMarkdown.markdownLink(tree, from, WikiItem.Section(root), asLink = true))
    }

    @Test
    fun `images embed with the name as alt text, other files link`() {
        val strand = WikiItem.Resource(root.resources[0])
        assertEquals("![Strand (2)](../Strand%20%282%29.png)", PickerMarkdown.markdownLink(tree, from, strand))
        assertEquals("![Beach](../Strand%20%282%29.png)", PickerMarkdown.markdownLink(tree, from, strand, text = " Beach "))
        assertEquals("[Strand (2).png](../Strand%20%282%29.png)", PickerMarkdown.markdownLink(tree, from, strand, asLink = true))
        assertEquals("[notes.pdf](../notes.pdf)", PickerMarkdown.markdownLink(tree, from, WikiItem.Resource(root.resources[1])))
    }

    @Test
    fun `link text is escaped`() {
        assertEquals("[a \\[1\\] \\\\ b](../index.md)", PickerMarkdown.markdownLink(tree, from, WikiItem.Document(root.documents[0]), "a [1] \\ b", asLink = true))
    }

    @Test
    fun `web addresses`() {
        assertEquals("[https://example.com/a b](https://example.com/a%20b)", PickerMarkdown.webLink(" https://example.com/a b ", ""))
        assertEquals("[Example](https://example.com/%28x%29)", PickerMarkdown.webLink("https://example.com/(x)", "Example"))
        assertEquals("![cat.png](https://example.com/img/cat.png?size=2)", PickerMarkdown.webImage("https://example.com/img/cat.png?size=2", ""))
        assertTrue(PickerMarkdown.isWebAddress(" https://x.y"))
        assertFalse(PickerMarkdown.isWebAddress("Kürbissuppe"))
    }

    @Test
    fun `upload names are safe and unique`() {
        assertEquals("a-b- c.png", PickerMarkdown.sanitizeFileName(" a/b:  c.png"))
        assertEquals("hidden", PickerMarkdown.sanitizeFileName("..hidden"))
        assertEquals("file", PickerMarkdown.sanitizeFileName("..."))
        assertEquals("Foto.jpg", PickerMarkdown.uniqueName(listOf("x.jpg"), "Foto.jpg"))
        assertEquals("Foto (3).JPG", PickerMarkdown.uniqueName(listOf("foto.jpg", "Foto (2).JPG"), "Foto.JPG"))
        assertEquals("README (2)", PickerMarkdown.uniqueName(listOf("readme"), "README"))
    }

    @Test
    fun `search finds names containing every word, pages first`() {
        val hits = tree.search("kurbis")
        assertEquals(listOf("d-suppe"), hits.map { it.item.id })
        assertEquals(listOf("Rezepte"), hits.single().folders)
        assertEquals(listOf("r-strand"), tree.search("STRAND 2").map { it.item.id })
        // documents first, then folders, then files, each by name
        assertEquals(listOf("d-apfel", "d-index", "d-suppe", "s-r", "r-pdf"), tree.search("e").map { it.item.id })
        assertTrue(tree.search("  ").isEmpty())
    }
}
