package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.markusressel.kodeeditor.library.compose.GutterCell
import de.markusressel.kodeeditor.library.compose.KodeEditorGutterColumn
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRun
import de.markusressel.mkdocseditor.feature.editor.domain.LineShare
import kotlin.math.absoluteValue

/** How strong the marker on the text is; the web client uses the same */
private const val MARKER_ALPHA = 0.14f

/** How the code editor shows who wrote what */
internal enum class AuthorHighlightMode(val value: String) {
    Off("off"),

    /** a colored strip next to each line */
    Strip("strip"),

    /** the strip, and a light marker on the text itself */
    StripAndMarker("marker");

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value } ?: StripAndMarker
    }
}

/**
 * The same color as the cursor of this author, if the server told us one
 *
 * @param knownColors the colors of the users seen so far, by username
 */
internal fun authorColor(author: String, knownColors: Map<String, String?>, background: Color): Color =
    (parsePeerColor(knownColors[author]) ?: fallbackPeerColors[author.hashCode().absoluteValue % fallbackPeerColors.size])
        .readableOn(background)

/**
 * A strip for each line. A line with several authors shows each one with its share, from top to bottom.
 *
 * @param lines the shares of each line
 * @param widthPx the width of the strip
 */
internal class AuthorStripColumn(
    private val lines: List<List<LineShare>>,
    private val widthPx: Int,
    private val colorOf: (String) -> Color,
) : KodeEditorGutterColumn {

    override fun width(lineCount: Int) = widthPx

    override fun DrawScope.drawCell(cell: GutterCell) {
        var top = 0f
        lines.getOrNull(cell.line)?.forEach { share ->
            val height = cell.fullHeight * share.fraction
            drawRect(color = colorOf(share.author), topLeft = Offset(0f, top), size = Size(cell.width, height))
            top += height
        }
    }
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
