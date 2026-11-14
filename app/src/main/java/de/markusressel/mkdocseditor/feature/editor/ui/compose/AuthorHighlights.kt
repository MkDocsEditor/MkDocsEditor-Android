package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.markusressel.kodeeditor.library.compose.GutterCell
import de.markusressel.mkdocseditor.R
import de.markusressel.kodeeditor.library.compose.KodeEditorGutterColumn
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRun
import de.markusressel.mkdocseditor.feature.editor.domain.LineShare

/** How strong the marker on the text is; the web client uses the same */
private const val MARKER_ALPHA = 0.14f

/** How the code editor shows who wrote what */
enum class AuthorHighlightMode(val value: String, @param:StringRes val nameRes: Int) {
    Off("off", R.string.editor_author_highlight_off_name),

    /** a colored strip next to each line */
    Strip("strip", R.string.editor_author_highlight_strip_name),

    /** the strip, and a light marker on the text itself */
    StripAndMarker("marker", R.string.editor_author_highlight_marker_name);

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value } ?: StripAndMarker
    }
}

/**
 * The color the server gives this author, the same as in the web client. It stays as it is: text doesn't have to be
 * readable on it, and an adjusted color would differ between the clients.
 *
 * @param knownColors the colors of the known users, by username
 */
internal fun authorColor(author: String, knownColors: Map<String, String?>): Color =
    parsePeerColor(knownColors[author]) ?: fallbackPeerColor(author)

/**
 * A strip for each line. A line with several authors shows each one with its share, from top to bottom.
 *
 * @param lines the shares of each line
 * @param widthPx the width of the strip
 * @param onLineTapped gets the 0-based line; false places the cursor like any tap on the bar
 */
internal class AuthorStripColumn(
    private val lines: List<List<LineShare>>,
    private val widthPx: Int,
    private val colorOf: (String) -> Color,
    private val onLineTapped: (Int) -> Boolean = { false },
) : KodeEditorGutterColumn {

    override fun width(lineCount: Int) = widthPx

    override fun onTap(line: Int): Boolean = onLineTapped(line)

    override fun DrawScope.drawCell(cell: GutterCell) {
        var top = 0f
        lines.getOrNull(cell.line)?.forEach { share ->
            val height = cell.fullHeight * share.fraction
            drawRect(color = colorOf(share.author), topLeft = Offset(0f, top), size = Size(cell.width, height))
            top += height
        }
    }
}

/** [column] as it is, with [onLineTapped] for taps on it */
internal class TappableGutterColumn(
    private val column: KodeEditorGutterColumn,
    private val onLineTapped: (Int) -> Boolean,
) : KodeEditorGutterColumn by column {
    override fun onTap(line: Int): Boolean = onLineTapped(line)
}

/** A light marker on the text of each author */
@Composable
internal fun BoxScope.AuthorMarkers(
    layout: KodeEditorLayout,
    runs: List<AuthorRun>,
    colorOf: (String) -> Color,
) {
    val textLength = layout.text.length
    Canvas(modifier = Modifier.matchParentSize()) {
        runs.forEach { run ->
            val author = run.author ?: return@forEach
            if (run.end > textLength) return@forEach
            drawPath(path = layout.getPathForRange(run.start, run.end), color = colorOf(author).copy(alpha = MARKER_ALPHA))
        }
    }
}
