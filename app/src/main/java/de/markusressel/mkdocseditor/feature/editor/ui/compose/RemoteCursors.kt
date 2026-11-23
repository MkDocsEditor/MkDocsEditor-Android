package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.KodeEditorRemoteCursor
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.UserAvatar
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

/**
 * The cursors of other users for the editor, which draws them and lets them glide. One cursor per client instance: a
 * user who leaves stays as a faint ghost for a while, and keeps the cursor, so it only turns faint.
 *
 * @param cursors what the editor draws
 * @param peers who each cursor belongs to, by its id, for the label
 */
internal class RemoteCursors(
    val cursors: List<KodeEditorRemoteCursor>,
    private val peers: Map<String, Pair<RemotePeer, PeerCursorColors>>,
) {
    fun peerOf(cursor: KodeEditorRemoteCursor) = peers[cursor.id]
}

/**
 * @param remotePeers the other users in this document, resolved against the text of the editor
 * @param ghosts users who just left, drawn faint
 * @param background the editor background; carets are adjusted to stay visible on it
 * @param foreground the editor text color, for the outline of a name flag that is too close to [background]
 */
internal fun remoteCursorsOf(
    remotePeers: List<RemotePeer>,
    ghosts: List<RemotePeer>,
    background: Color,
    foreground: Color,
): RemoteCursors {
    val peers = LinkedHashMap<String, Pair<RemotePeer, PeerCursorColors>>()
    remotePeers.forEach { peers.putIfAbsent(it.instanceId, it to peerCursorColors(it.composeColor, background, foreground)) }
    // a live connection of the same instance wins over its ghost
    ghosts.forEach { peers.putIfAbsent(it.instanceId, it to peerCursorColors(it.composeColor, background, foreground, GHOST_ALPHA)) }
    val cursors = peers.mapNotNull { (id, entry) ->
        val (peer, colors) = entry
        if (peer.selections.isEmpty()) return@mapNotNull null
        KodeEditorRemoteCursor(
            id = id,
            // the end of a range is where the cursor is
            selections = peer.selections.map { TextRange(it.anchor, it.head) },
            caretColor = colors.caret,
            // as in the web client, and a ghost is faint here too
            selectionColor = colors.flag.copy(alpha = SELECTION_ALPHA * colors.flag.alpha),
        )
    }
    return RemoteCursors(cursors, peers)
}

/**
 * The name of a user, which the editor shows above its cursor (or below, on the first line), with its profile
 * picture in front of it if it has one
 */
@Composable
internal fun RemotePeerLabel(
    name: String,
    avatar: Avatar?,
    colors: PeerCursorColors,
) {
    val style = MaterialTheme.typography.labelSmall
    val color = colors.flag

    Row(
        modifier = Modifier
            .then(
                colors.flagOutline?.let { Modifier.border(1.dp, it, FLAG_SHAPE) } ?: Modifier
            )
            .background(color = color, shape = FLAG_SHAPE)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            UserAvatar(
                // as high as the text, so the label keeps its height
                modifier = Modifier
                    .padding(vertical = 1.dp)
                    .size(with(LocalDensity.current) { style.lineHeight.toDp() } - 2.dp)
                    // faint like the rest of the label, for ghosts
                    .alpha(color.alpha),
                name = name,
                color = color.copy(alpha = 1f),
                avatar = avatar,
                textStyle = style,
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = name,
            color = colors.flagText,
            style = style,
            maxLines = 1,
        )
    }
}

private val FLAG_SHAPE = RoundedCornerShape(2.dp)

/** how much the selection of another user shows */
private const val SELECTION_ALPHA = 0.25f

/** how much a ghost of a user who left shows */
private const val GHOST_ALPHA = 0.4f
