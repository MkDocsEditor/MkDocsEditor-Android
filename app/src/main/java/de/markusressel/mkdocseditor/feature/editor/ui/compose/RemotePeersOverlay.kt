package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.math.roundToInt

/**
 * Draws the cursors, selections and names of other users on top of the editor text.
 * Must be used within the overlay of the KodeEditor, so it moves with zoom and scroll.
 *
 * @param layout the current text layout of the editor
 * @param remotePeers the other users in this document, resolved against the text of [layout]
 * @param background the editor background; peer colors are adjusted to stay visible on it
 */
@Composable
internal fun BoxScope.RemotePeersOverlay(
    layout: KodeEditorLayout,
    remotePeers: List<RemotePeer>,
    background: Color = MaterialTheme.colorScheme.background,
) {
    val textLength = layout.text.length

    remotePeers.forEach { peer ->
        val color = peer.composeColor.readableOn(background)
        val selections = peer.selections.map {
            it.copy(
                anchor = it.anchor.coerceIn(0, textLength),
                head = it.head.coerceIn(0, textLength),
            )
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            selections.forEach { selection ->
                if (selection.start != selection.end) {
                    drawPath(
                        path = layout.getPathForRange(selection.start, selection.end),
                        color = color.copy(alpha = 0.3f),
                    )
                }

                val cursorRect = layout.getCursorRect(selection.head)
                drawLine(
                    color = color,
                    start = cursorRect.topCenter,
                    end = cursorRect.bottomCenter,
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        selections.firstOrNull()?.let { selection ->
            RemotePeerLabel(
                name = peer.name,
                color = color,
                layout = layout,
                offset = selection.head,
            )
        }
    }
}

/**
 * The name of a user, shown above its cursor (or below, on the first line)
 */
@Composable
private fun RemotePeerLabel(
    name: String,
    color: Color,
    layout: KodeEditorLayout,
    offset: Int,
) {
    val cursorRect = layout.getCursorRect(offset)

    Text(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val y = when {
                    cursorRect.top >= placeable.height -> cursorRect.top - placeable.height
                    else -> cursorRect.bottom
                }
                layout(placeable.width, placeable.height) {
                    placeable.place(
                        x = cursorRect.left.roundToInt(),
                        y = y.roundToInt(),
                    )
                }
            }
            .background(color = color, shape = RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp),
        text = name,
        color = labelTextColorOn(color),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}
