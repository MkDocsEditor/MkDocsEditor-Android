package de.markusressel.mkdocseditor.feature.history.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.UserAvatar
import de.markusressel.mkdocseditor.feature.editor.ui.compose.fallbackPeerColor
import de.markusressel.mkdocseditor.feature.editor.ui.compose.parsePeerColor
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.history.BlameBlockModel
import java.util.Date

/** the backend's message for an edit without one of its own, which tells nothing */
private val STANDARD_MESSAGE = Regex("Update .+\\.md")

/** Who last changed the lines around a tapped line of the author strip, and when */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineAuthorSheet(
    block: BlameBlockModel,
    onOpenHistory: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LineAuthorContent(block, onOpenHistory)
    }
}

/** @param onOpenHistory null on a server without the history */
@Composable
internal fun LineAuthorContent(
    block: BlameBlockModel,
    onOpenHistory: (() -> Unit)?,
) {
    val author = block.author
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (block.start == block.end) stringResource(R.string.line_author_title_line, block.start)
            else stringResource(R.string.line_author_title_lines, block.start, block.end),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (author != null) {
                UserAvatar(
                    modifier = Modifier.size(36.dp),
                    name = author.displayName,
                    color = parsePeerColor(author.color) ?: fallbackPeerColor(author.username ?: author.displayName),
                    avatar = Avatar.of(author.accountId, author.avatar),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                val name = author?.displayName ?: stringResource(R.string.line_author_unknown)
                Text(
                    text = if (block.agents.isEmpty()) name
                    else stringResource(R.string.history_via, name, block.agents.joinToString(", ")),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = block.time
                Text(
                    // pending lines are newer than the last commit, so they have no time yet
                    text = if (block.pending || time == null) stringResource(R.string.line_author_pending) else relativeTime(time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                block.message?.takeUnless { it.isBlank() || STANDARD_MESSAGE.matches(it) }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (onOpenHistory != null) {
            TextButton(modifier = Modifier.align(Alignment.End), onClick = onOpenHistory) {
                Text(stringResource(R.string.history_action))
            }
        }
    }
}

@CombinedPreview
@Composable
private fun LineAuthorPreview() {
    MkDocsEditorTheme {
        LineAuthorContent(
            block = BlameBlockModel(
                start = 7, end = 9, revision = "415bd17be867d776b13a5d71b8d38132aab1a171", time = Date(),
                message = "Restore section \"Install\" of guide/setup.md to bde4a3c",
                author = FeedAuthorModel(accountId = "a2", username = "bob", displayName = "Bob", color = "#2f9e44"),
                agents = listOf("Claude Code"),
            ),
            onOpenHistory = {},
        )
    }
}
