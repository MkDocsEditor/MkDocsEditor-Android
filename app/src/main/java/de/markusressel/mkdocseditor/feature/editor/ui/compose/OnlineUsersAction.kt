package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.UserAvatar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteAgent
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

private const val MAX_VISIBLE_USERS = 3

/**
 * Shows the other client instances in this document as colored circles with the initial of their user, AI agents as a
 * robot
 *
 * @param remotePeers the other connections in this document
 */
@Composable
internal fun OnlineUsersAction(
    modifier: Modifier = Modifier,
    remotePeers: List<RemotePeer>,
    onFollowClicked: (instanceId: String) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OnlineUsersBadges(
            modifier = Modifier.clickable { menuOpen = true },
            remotePeers = remotePeers,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            remotePeers.instances().forEach { user ->
                val agent = user.agent
                if (agent != null) {
                    // an agent has no cursor or viewport to follow
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.online_agent, agent.name, user.name)) },
                        leadingIcon = { AgentAvatar(modifier = Modifier.size(24.dp), color = user.composeColor) },
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.follow_user, user.label)) },
                        onClick = {
                            menuOpen = false
                            onFollowClicked(user.instanceId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineUsersBadges(
    modifier: Modifier = Modifier,
    remotePeers: List<RemotePeer>,
) {
    val users = remotePeers.instances()

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        users.take(MAX_VISIBLE_USERS).forEach { user ->
            if (user.agent != null) {
                AgentAvatar(modifier = Modifier.badge(), color = user.composeColor)
            } else {
                UserAvatar(
                    modifier = Modifier.badge(),
                    name = user.name,
                    color = user.composeColor,
                    avatar = user.avatarModel,
                )
            }
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
            .badge()
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

/** An AI agent: a robot in the color of its person */
@Composable
private fun AgentAvatar(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SmartToy,
            contentDescription = null,
            tint = labelTextColorOn(color),
            modifier = Modifier.fillMaxSize(0.7f),
        )
    }
}

/** the ring keeps overlapping badges apart */
@Composable
private fun Modifier.badge() = this
    .size(28.dp)
    .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
    .padding(1.dp)

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
                    agent = if (index == 1) RemoteAgent(tokenId = "t1", name = "Claude Code") else null,
                )
            }
        )
    }
}
