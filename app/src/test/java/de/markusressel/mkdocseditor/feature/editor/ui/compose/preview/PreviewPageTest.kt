package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewPageTest {

    @Test
    fun `the base url is the folder of the document`() {
        assertEquals("https://mkdocs-preview.invalid/", PreviewPage.baseUrl(""))
        assertEquals("https://mkdocs-preview.invalid/guide/My%20Notes/", PreviewPage.baseUrl("guide/My Notes"))
    }

    @Test
    fun `requests of the page are wiki files or assets`() {
        assertEquals("/guide/img/a%20b.png", PreviewPage.wikiPathOf(PreviewPage.HOST, "/guide/img/a%20b.png"))
        assertNull(PreviewPage.wikiPathOf("example.com", "/guide/img/a.png"))
        assertNull(PreviewPage.wikiPathOf(PreviewPage.HOST, "/__preview__/preview.js"))

        assertEquals("preview/preview.js", PreviewPage.assetOf(PreviewPage.HOST, "/__preview__/preview.js"))
        assertNull(PreviewPage.assetOf(PreviewPage.HOST, "/__preview__/../secret"))
        assertNull(PreviewPage.assetOf("example.com", "/__preview__/preview.js"))
    }

    @Test
    fun `the page uses the theme and loads every script from the assets`() {
        val html = PreviewPage.html(
            PreviewTheme(EditorPalette.Dark, onSurface = Color(0xFFE3E3DC), primary = Color.Green, error = Color.Red, dark = true)
        )
        assertTrue(html.contains("--background: #1A1C18"))
        assertTrue(html.contains("--md-on-surface-rgb: 227, 227, 220"))
        assertTrue(html.contains("color-scheme: dark"))
        assertTrue(html.contains("--md-code-keyword: #D4895A"))

        val assets = File("src/main/assets")
        "src=\"https://mkdocs-preview.invalid/__preview__/([^\"]+)\"".toRegex().findAll(html).forEach {
            val name = it.groupValues[1]
            assertTrue(File(assets, "preview/$name").isFile, "missing asset $name")
            assertEquals("preview/$name", PreviewPage.assetOf(PreviewPage.HOST, "/__preview__/$name"))
        }
        "href=\"https://mkdocs-preview.invalid/__preview__/([^\"]+)\"".toRegex().findAll(html).forEach {
            val name = it.groupValues[1]
            assertTrue(File(assets, "preview/$name").isFile, "missing asset $name")
            assertEquals("preview/$name", PreviewPage.assetOf(PreviewPage.HOST, "/__preview__/$name"))
        }
    }

    /** The stylesheet of the renderer has no default values, so an unset variable loses its color */
    @Test
    fun `the page sets every variable of the renderer stylesheet`() {
        val css = File("src/main/assets/preview/mkdocs-renderer.css").readText()
        val html = PreviewPage.html(
            PreviewTheme(EditorPalette.Light, onSurface = Color.Black, primary = Color.Green, error = Color.Red, dark = false)
        )

        val used = "var\\((--md-[a-z0-9-]+)".toRegex().findAll(css).map { it.groupValues[1] }.toSet()

        assertTrue(used.isNotEmpty())
        used.forEach { assertTrue(html.contains("$it: "), "$it is not set") }
    }

    @Test
    fun `the rendered markdown gets the class of the renderer stylesheet`() {
        val html = PreviewPage.html(
            PreviewTheme(EditorPalette.Light, onSurface = Color.Black, primary = Color.Green, error = Color.Red, dark = false)
        )

        assertTrue(html.contains("class=\"markdown-preview\""))
    }
}
