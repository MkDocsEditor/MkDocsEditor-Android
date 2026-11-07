package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageMetaTest {

    @Test
    fun `every field of the renderer is read`() {
        val meta = parsePageMeta(
            """
            {
              "title": "Getting started",
              "description": "The first steps",
              "icon": "material/emoticon-happy",
              "status": "new",
              "tags": ["setup", "intro"],
              "hide": ["toc", "navigation"],
              "searchExclude": true,
              "extra": {"author": "anna", "order": 2, "draft": false, "list": [1, 2]}
            }
            """.trimIndent()
        )

        assertEquals(
            PageMeta(
                title = "Getting started",
                description = "The first steps",
                icon = "material/emoticon-happy",
                status = "new",
                tags = listOf("setup", "intro"),
                hide = listOf("toc", "navigation"),
                searchExclude = true,
                extra = mapOf("author" to "anna", "order" to 2.0, "draft" to false, "list" to listOf(1.0, 2.0)),
            ),
            meta,
        )
    }

    @Test
    fun `missing fields are empty`() {
        assertEquals(PageMeta(), parsePageMeta("{}"))
        assertEquals(PageMeta(title = "Only a title"), parsePageMeta("""{"title": "Only a title"}"""))
    }

    @Test
    fun `unknown keys are ignored`() {
        val meta = parsePageMeta("""{"title": "Page", "comments": true, "template": {"name": "x"}}""")

        assertEquals(PageMeta(title = "Page"), meta)
    }

    @Test
    fun `broken JSON gives no meta`() {
        assertNull(parsePageMeta("""{"title": """))
        assertNull(parsePageMeta("not json"))
        assertNull(parsePageMeta("""{"tags": "not a list"}"""))
    }

    @Test
    fun `the editor shows the title of the front matter`() {
        assertEquals("Getting started", editorTitle(PageMeta(title = "Getting started"), "setup.md"))
        assertEquals("Page", editorTitle(PageMeta(title = " Page "), "page.md"))
    }

    @Test
    fun `the editor shows the file name without a title`() {
        assertEquals("setup.md", editorTitle(null, "setup.md"))
        assertEquals("setup.md", editorTitle(PageMeta(), "setup.md"))
        assertEquals("setup.md", editorTitle(PageMeta(title = "  "), "setup.md"))
    }
}
