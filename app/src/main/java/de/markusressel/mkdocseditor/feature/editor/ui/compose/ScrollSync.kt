package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewScroll
import de.markusressel.mkdocseditor.feature.editor.domain.lineCountOf
import de.markusressel.mkdocseditor.feature.editor.domain.lineOfPosition
import de.markusressel.mkdocseditor.feature.editor.domain.positionOfLine
import de.markusressel.mkdocseditor.feature.editor.domain.topSourceLine

/**
 * Keeps the code editor and the rendered page of a split at the same source line, like other markdown editors with a
 * split do. Only the side the user moved last drives the other one, so a scroll that this causes never comes back:
 * the editor drives until a finger moves the page, the page until the user moves the editor, types or following
 * moves it. Both sides show their top line at the top.
 *
 * @param text the text of the editor
 */
@Stable
internal class ScrollSync(private val text: () -> String) {

    enum class Side { Editor, Preview }

    var driver: Side = Side.Editor
        private set

    /** whether the editor and the page are both shown */
    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // the page catches up with the editor when it comes in
            if (value && driver == Side.Editor) editorRange?.let { previewLine = lineOfPosition(text(), it.start) }
        }

    /** the source line the page should show at its top, with a fraction, null as long as the editor did not move */
    var previewLine by mutableStateOf<Double?>(null)
        private set

    /** the text the editor should center, so that its top matches the top of the page */
    var editorTarget by mutableStateOf<IntRange?>(null)
        private set

    /** counts how often the user moved the page while it drives, for following to pause like for a move of the editor */
    var previewMovedByUser by mutableIntStateOf(0)
        private set

    private var editorRange: TextRange? = null

    /** the user scrolled or typed in the editor, or following moved it */
    fun onEditorDriving() {
        driver = Side.Editor
    }

    fun onEditorVisibleRange(range: TextRange?) {
        editorRange = range
        if (range == null || !active || driver != Side.Editor) return
        previewLine = lineOfPosition(text(), range.start)
    }

    fun onPreviewScrolled(scroll: PreviewScroll) {
        // the page also reports the scrolls that the app makes, and those must not drive
        if (!scroll.byUser || !active) return
        driver = Side.Preview
        previewMovedByUser++
        val text = text()
        val length = editorRange?.length ?: 0
        editorTarget = when {
            scroll.atBottom -> text.length..text.length
            else -> {
                val top = positionOfLine(text, topSourceLine(scroll, lineCountOf(text)))
                // the editor centers what it is asked for: as much as it shows now, so that its top lands there
                top..(top + length).coerceAtMost(text.length)
            }
        }
    }
}
