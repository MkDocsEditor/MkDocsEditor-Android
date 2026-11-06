package de.markusressel.mkdocseditor.feature.editor.ui.compose

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.markusressel.mkdocseditor.feature.editor.domain.EditorScrollView
import de.markusressel.mkdocseditor.feature.editor.domain.EditorTarget
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLayout
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewScroll
import de.markusressel.mkdocseditor.feature.editor.domain.SourceHeightMap
import de.markusressel.mkdocseditor.feature.editor.domain.SyncAnchor
import de.markusressel.mkdocseditor.feature.editor.domain.editorCaretAnchor
import de.markusressel.mkdocseditor.feature.editor.domain.editorReadingAnchor
import de.markusressel.mkdocseditor.feature.editor.domain.editorTargetOf
import de.markusressel.mkdocseditor.feature.editor.domain.lineCountOf
import de.markusressel.mkdocseditor.feature.editor.domain.previewReadingAnchor

/**
 * Keeps the code editor and the rendered page of a split at the same source line, like other markdown editors with a
 * split do. Only the side the user moved last drives the other one, so a scroll that this causes never comes back:
 * the editor drives until a finger moves the page, the page until the user moves the editor, types or following
 * moves it.
 *
 * Both sides scroll by progress: the driving side takes the source line at the height of its view that is as far
 * down as it is scrolled, and the other side puts that line at the same height of its view. So both start and end
 * together, also when they show very different amounts of text. While typing, the caret is the anchor instead, at
 * its height in the editor, so that the page keeps the block being typed in view. Same in the web client.
 *
 * @param text the text of the editor
 * @param now the clock for the time after an edit
 */
@Stable
internal class ScrollSync(
    private val text: () -> String,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {

    enum class Side { Editor, Preview }

    /** Where the page should show a source line, smoothly for the caret, which moves in steps */
    data class PreviewTarget(val anchor: SyncAnchor, val smooth: Boolean)

    /** The anchor of the editor, and whether it is the caret */
    data class EditorAnchor(val anchor: SyncAnchor, val caret: Boolean)

    var driver: Side = Side.Editor
        private set

    /** whether the editor and the page are both shown */
    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // the page catches up with the editor when it comes in
            if (value) publishPreviewTarget()
        }

    /** where the page should scroll to, null as long as the editor did not move or while the page drives */
    var previewTarget by mutableStateOf<PreviewTarget?>(null)
        private set

    /** where the editor should scroll to, while the page drives */
    var editorTarget by mutableStateOf<EditorTarget?>(null)
        private set

    /** counts how often the user moved the page while it drives, for following to pause like for a move of the editor */
    var previewMovedByUser by mutableIntStateOf(0)
        private set

    /** a state, so that the anchor of the editor is read again after an edit */
    private var editedAt by mutableStateOf<Long?>(null)

    /** the last anchor of the editor, which the page shows again when it comes in */
    private var editorAnchor: EditorAnchor? = null

    private var map: SourceHeightMap? = null

    /** the user scrolled the editor, or following moved it */
    fun onEditorDriving() {
        driver = Side.Editor
    }

    /** the user typed in the editor */
    fun onLocalEdit() {
        editedAt = now()
        onEditorDriving()
    }

    /**
     * The anchor of the editor, read in a snapshot: the caret for a moment after an edit, so that the scrolls with
     * which the editor follows its caret keep it, else the reading line. Also the reading line while the caret is out
     * of view.
     *
     * @param caretLine the source line of the caret, with the part of the way through it
     */
    fun editorAnchorOf(view: EditorScrollView, caretLine: () -> Double): EditorAnchor? {
        val edited = editedAt
        if (edited != null && now() - edited < CARET_ANCHOR_MS) {
            editorCaretAnchor(view, caretLine())?.let { return EditorAnchor(it, caret = true) }
        }
        return editorReadingAnchor(view)?.let { EditorAnchor(it, caret = false) }
    }

    fun onEditorAnchor(anchor: EditorAnchor?) {
        if (anchor == null || driver != Side.Editor) return
        editorAnchor = anchor
        publishPreviewTarget()
    }

    /** the page rendered or changed its size */
    fun onPreviewLayout(layout: PreviewLayout) {
        map = SourceHeightMap(layout, lineCountOf(text()))
    }

    fun onPreviewScrolled(scroll: PreviewScroll) {
        // the page also reports the scrolls that the app makes, and those must not drive
        if (!scroll.byUser || !active) return
        if (driver != Side.Preview) {
            driver = Side.Preview
            // or a page that comes in again, or renders, would go back to it
            editorAnchor = null
            previewTarget = null
        }
        previewMovedByUser++
        val map = map ?: return
        editorTarget = editorTargetOf(previewReadingAnchor(map, scroll), map.lineCount)
    }

    private fun publishPreviewTarget() {
        if (!active || driver != Side.Editor) return
        val anchor = editorAnchor ?: return
        previewTarget = PreviewTarget(anchor.anchor, smooth = anchor.caret)
    }

    companion object {
        /** how long after an edit the caret is the anchor */
        const val CARET_ANCHOR_MS = 300L
    }
}
