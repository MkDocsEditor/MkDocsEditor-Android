package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

/**
 * Decides when the text goes to the page of the preview. Used on the main thread.
 *
 * The page says itself when its scripts are there ([onPageReady]). `onPageFinished` of the WebView can't tell that: a
 * document in a folder loads a second page as soon as its folder is known, and the first page can still report that it
 * finished, with the second one not yet running.
 */
internal class PreviewRenderState {
    private var ready = false
    private var disposed = false
    private var rendered: String? = null

    /** another page starts to load, and the current one no longer counts */
    fun onPageLoading() {
        ready = false
        rendered = null
    }

    /**
     * A page has loaded its scripts. Each page starts empty, so this renders even the same text as before, unless
     * nobody sees the page: then the text waits for the next [show].
     */
    fun onPageReady(text: String, render: (String) -> Unit, visible: Boolean = true) {
        ready = true
        rendered = null
        if (visible) show(text, render)
    }

    /** Shows the text, unless no page is ready yet or the page shows it already. */
    fun show(text: String, render: (String) -> Unit) {
        if (!ready || disposed || rendered == text) return
        rendered = text
        render(text)
    }

    /**
     * Reads only the front matter of a text that nobody sees, unless no page is ready yet or the page shows that text
     * already, so that the title can follow the text without a render.
     */
    fun readMeta(text: String, read: (String) -> Unit) {
        if (!ready || disposed || rendered == text) return
        read(text)
    }

    /** the WebView is gone; a page that reports ready after this has nothing to render into */
    fun dispose() {
        disposed = true
    }
}
