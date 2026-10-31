package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

private const val MAX_VISIBLE_USERS = 3

/**
 * Shows the other users in this document as colored circles with their initial
 *
 * @param remotePeers the other users in this document
 */
@Composable
internal fun OnlineUsersAction(
    modifier: Modifier = Modifier,
    remotePeers: List<RemotePeer>,
) {
    // the same user can be connected with multiple clients
    val users = remotePeers.distinctBy { it.username }

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        users.take(MAX_VISIBLE_USERS).forEach { user ->
            val color = user.composeColor
            OnlineUserBadge(
                text = user.name.take(1).uppercase(),
                color = color,
                textColor = if (color.luminance() > 0.5f) Color.Black else Color.White,
            )
        }

        if (users.size > MAX_VISIBLE_USERS) {
            OnlineUserBadge(
                text = "+${users.size - MAX_VISIBLE_USERS}",
                color = MaterialTheme.colorScheme.secondary,
                textColor = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

@Composable
private fun OnlineUserBadge(
    text: String,
    color: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(1.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@CombinedPreview
@Composable
private fun OnlineUsersActionPreview() {
    MkDocsEditorTheme {
        OnlineUsersAction(
            remotePeers = listOf("alice", "bob", "carol", "dave", "alice").mapIndexed { index, username ->
                RemotePeer(
                    clientId = "$index",
                    username = username,
                    displayName = null,
                    color = null,
                    selections = emptyList(),
                )
            }
        )
    }
}
