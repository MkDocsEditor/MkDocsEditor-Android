package de.markusressel.mkdocsrestclient.sync.automerge

/**
 * Spreads out the presence messages of this client. A cursor goes out soon, and always with the newest viewport: a client
 * that leaves right after it moved would otherwise leave a ghost at an old viewport, and followers would stay there. A
 * viewport alone waits longer, because scrolling reports one many times a second.
 *
 * @param post runs the runnable after the delay in ms, on the thread of the caller
 * @param send sends both, null for one that did not change
 */
internal class PresenceThrottle(
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val isPosted: (Runnable) -> Boolean,
    private val send: (selection: Pair<Int?, Int?>?, viewport: Pair<Int?, Int?>?) -> Unit,
) {
    private var selection: Pair<Int?, Int?>? = null
    private var viewport: Pair<Int?, Int?>? = null

    private val selectionRunnable = Runnable {
        cancel(viewportRunnable)
        flush()
    }
    private val viewportRunnable = Runnable { flush() }

    fun onSelection(anchor: Int?, head: Int?) {
        selection = anchor to head
        if (!isPosted(selectionRunnable)) post(selectionRunnable, SELECTION_DELAY_MS)
    }

    fun onViewport(top: Int?, bottom: Int?) {
        viewport = top to bottom
        // a cursor that is about to go out takes it along
        if (!isPosted(selectionRunnable) && !isPosted(viewportRunnable)) post(viewportRunnable, VIEWPORT_DELAY_MS)
    }

    private fun flush() {
        val selection = selection
        val viewport = viewport
        this.selection = null
        this.viewport = null
        if (selection != null || viewport != null) send(selection, viewport)
    }

    companion object {
        const val SELECTION_DELAY_MS = 100L
        const val VIEWPORT_DELAY_MS = 500L
    }
}
