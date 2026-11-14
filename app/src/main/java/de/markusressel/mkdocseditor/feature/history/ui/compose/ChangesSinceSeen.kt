package de.markusressel.mkdocseditor.feature.history.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.history.ChangeAuthorModel
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.DocumentChangesModel
import de.markusressel.mkdocsrestclient.history.HistorySummaryModel
import de.markusressel.mkdocsrestclient.history.SeenMarkModel
import java.util.Date

/** "Anna, Bob via Claude Code": each person, with the agents they changed the page with */
@Composable
private fun authorsText(authors: List<ChangeAuthorModel>): String {
    val via = stringResource(R.string.history_via)
    return authors.joinToString(", ") { author ->
        if (author.agents.isEmpty()) author.displayName else via.format(author.displayName, author.agents.joinToString(", "))
    }
}

/**
 * Tells on opening a page who changed it since the last visit, and where. Tapping it shows the changes.
 */
@Composable
internal fun ChangesSinceSeenBanner(
    changes: DocumentChangesModel,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val headings = changes.summary.headings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.changes_since_seen_banner, authorsText(changes.authors)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (headings.isNotEmpty()) {
                Text(
                    text = headings.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.changes_since_seen_dismiss),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The changes since the last visit, hunk by hunk; a hunk goes to its place in the page */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangesSinceSeenSheet(
    changes: DocumentChangesModel,
    onHunkClicked: (DiffHunkModel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ChangesSinceSeenContent(changes, onHunkClicked)
    }
}

@Composable
internal fun ChangesSinceSeenContent(
    changes: DocumentChangesModel,
    onHunkClicked: (DiffHunkModel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.changes_since_seen_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = authorsText(changes.authors) + " · +${changes.summary.linesAdded} −${changes.summary.linesRemoved}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        changes.hunks.forEach { hunk -> Hunk(hunk, onClick = { onHunkClicked(hunk) }) }
    }
}

@CombinedPreview
@Composable
private fun ChangesSinceSeenPreview() {
    val changes = DocumentChangesModel(
        since = SeenMarkModel(Date(0), "a"),
        seenToken = "b",
        authors = listOf(
            ChangeAuthorModel(displayName = "Alice", color = "#e8590c"),
            ChangeAuthorModel(displayName = "Bob", color = "#2f9e44", agents = listOf("Claude Code")),
        ),
        hunks = listOf(
            DiffHunkModel(
                heading = "Setup", headingOccurrence = 1, oldStart = 1, oldLines = 2, newStart = 1, newLines = 3,
                lines = listOf(
                    DiffLineModel(DiffOp.CONTEXT, "# Setup"),
                    DiffLineModel(DiffOp.REMOVED, "Some text."),
                    DiffLineModel(DiffOp.ADDED, "Some better text."),
                    DiffLineModel(DiffOp.ADDED, "## Install"),
                ),
            ),
        ),
        summary = HistorySummaryModel(2, 1, listOf("Setup", "Install")),
    )
    MkDocsEditorTheme {
        Column {
            ChangesSinceSeenBanner(changes, onClick = {}, onDismiss = {})
            ChangesSinceSeenContent(changes, onHunkClicked = {})
        }
    }
}
