package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreviewRenderStateTest {
    private val state = PreviewRenderState()
    private val rendered = mutableListOf<String>()
    private val render: (String) -> Unit = { rendered += it }

    @Test
    fun `nothing renders before a page is ready`() {
        state.show("text", render)

        assertEquals(emptyList<String>(), rendered)
    }

    @Test
    fun `the text renders when the page is ready`() {
        state.onPageReady("text", render)

        assertEquals(listOf("text"), rendered)
    }

    @Test
    fun `a page nobody sees waits with the text until it is shown`() {
        state.onPageReady("text", render, visible = false)
        assertEquals(emptyList<String>(), rendered)

        state.show("text", render)
        assertEquals(listOf("text"), rendered)
    }

    @Test
    fun `the same text renders once on the same page`() {
        state.onPageReady("text", render)
        state.show("text", render)

        assertEquals(listOf("text"), rendered)
    }

    @Test
    fun `changed text renders`() {
        state.onPageReady("one", render)
        state.show("two", render)

        assertEquals(listOf("one", "two"), rendered)
    }

    @Test
    fun `nothing renders while the next page loads`() {
        state.onPageReady("one", render)
        state.onPageLoading()
        state.show("two", render)

        assertEquals(listOf("one"), rendered)
    }

    @Test
    fun `the next page gets the same text again`() {
        state.onPageReady("text", render)
        state.onPageLoading()
        state.onPageReady("text", render)

        assertEquals(listOf("text", "text"), rendered)
    }

    /**
     * The empty preview: the first page reports in after the second one started to load, so its render goes nowhere.
     * The second page must still get the text.
     */
    @Test
    fun `a late report of the previous page does not stop the current page from rendering`() {
        state.onPageLoading()
        state.onPageLoading()
        state.onPageReady("text", render)
        state.show("text", render)
        state.onPageReady("text", render)

        assertEquals(listOf("text", "text"), rendered)
    }

    @Test
    fun `nothing renders after the WebView is gone`() {
        state.dispose()
        state.onPageReady("text", render)

        assertEquals(emptyList<String>(), rendered)
    }

    @Test
    fun `the front matter of a text nobody sees is read without a render`() {
        val read = mutableListOf<String>()
        state.onPageReady("one", render, visible = false)
        state.readMeta("two", read::add)

        assertEquals(emptyList<String>(), rendered)
        assertEquals(listOf("two"), read)
    }

    @Test
    fun `no front matter is read before a page is ready`() {
        val read = mutableListOf<String>()
        state.readMeta("text", read::add)

        assertEquals(emptyList<String>(), read)
    }

    @Test
    fun `the front matter of the text the page shows is not read again`() {
        val read = mutableListOf<String>()
        state.onPageReady("text", render)
        state.readMeta("text", read::add)

        assertEquals(emptyList<String>(), read)
    }
}
