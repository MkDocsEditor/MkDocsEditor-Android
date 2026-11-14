package de.markusressel.mkdocseditor.feature.history.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.UserAvatar
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.editor.ui.compose.fallbackPeerColor
import de.markusressel.mkdocseditor.feature.editor.ui.compose.labelTextColorOn
import de.markusressel.mkdocseditor.feature.editor.ui.compose.parsePeerColor
import de.markusressel.mkdocseditor.feature.history.domain.Highlight
import de.markusressel.mkdocseditor.feature.history.domain.agentNames
import de.markusressel.mkdocseditor.feature.history.domain.notableMessage
import de.markusressel.mkdocseditor.feature.history.domain.pairChangedLines
import de.markusressel.mkdocseditor.feature.history.domain.peopleWithAgents
import de.markusressel.mkdocseditor.feature.history.domain.shownName
import de.markusressel.mkdocseditor.feature.history.domain.wordChanges
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.Confirm
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.DiffState
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.HistoryAgentModel
import de.markusressel.mkdocsrestclient.history.HistoryAuthorModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel
import de.markusressel.mkdocsrestclient.history.HistorySummaryModel
import java.util.Date

// light enough for text on top in both themes
private val AddedLine = Color(0x332DA44E)
private val AddedWords = Color(0x772DA44E)
private val RemovedLine = Color(0x33E5534B)
private val RemovedWords = Color(0x77E5534B)

@Composable
internal fun HistoryScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.history_title),
                canGoBack = true,
                onBackClicked = { onUiEvent(UiEvent.BackClicked) },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.loading || uiState.working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val problem = uiState.problem
            when {
                problem != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(problem))
                    Button(onClick = { onUiEvent(UiEvent.RetryClicked) }) { Text(stringResource(R.string.retry)) }
                }
                !uiState.loading && uiState.entries.isEmpty() -> Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.history_empty),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.revision }) { entry ->
                        HistoryEntry(
                            entry = entry,
                            expanded = uiState.expanded == entry.revision,
                            diff = uiState.diffs[entry.revision],
                            // the newest entry is the text as it is
                            canRestore = uiState.canRestore && entry != uiState.entries.first(),
                            // the oldest one created the page, which is undone by deleting it
                            canUndo = uiState.canRevert && entry != uiState.entries.last(),
                            canRestoreSection = uiState.canRestoreSection && entry != uiState.entries.first(),
                            onUiEvent = onUiEvent,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    uiState.confirm?.let { confirm ->
        val entry = confirm.entry
        val (title, text, action) = when (confirm) {
            is Confirm.Restore -> Triple(
                stringResource(R.string.history_restore_title),
                stringResource(R.string.history_restore_text, relativeTime(entry.time), entry.author.shownName),
                stringResource(R.string.history_restore),
            )
            // the diff of the entry is open above, so the dialog names who and when
            is Confirm.Undo -> Triple(
                stringResource(R.string.history_undo_title),
                stringResource(R.string.history_undo_text, whoText(entry), relativeTime(entry.time)),
                stringResource(R.string.history_undo),
            )
            is Confirm.RestoreSection -> Triple(
                stringResource(R.string.history_restore_section_title),
                stringResource(R.string.history_restore_section_text, confirm.section.heading, relativeTime(entry.time)),
                stringResource(R.string.history_restore),
            )
        }
        AlertDialog(
            onDismissRequest = { onUiEvent(UiEvent.ConfirmDismissed) },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { onUiEvent(UiEvent.Confirmed) }) { Text(action) } },
            dismissButton = {
                TextButton(onClick = { onUiEvent(UiEvent.ConfirmDismissed) }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryEntry(
    entry: HistoryEntryModel,
    expanded: Boolean,
    diff: DiffState?,
    canRestore: Boolean,
    canUndo: Boolean,
    canRestoreSection: Boolean,
    onUiEvent: (UiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onUiEvent(UiEvent.EntryClicked(entry.revision)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryAvatar(entry)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = whoText(entry),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(relativeTime(entry.time), summaryText(entry.summary)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.notableMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.size(8.dp))
            EntryDiff(
                diff = diff,
                onHunkClicked = { onUiEvent(UiEvent.HunkClicked(it)) },
                // a hunk above the first heading has no section to restore
                onRestoreSection = if (canRestoreSection) {
                    { hunk -> onUiEvent(UiEvent.RestoreSectionClicked(entry, hunk)) }
                } else null,
            )
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUndo) {
                    OutlinedButton(onClick = { onUiEvent(UiEvent.UndoClicked(entry)) }) {
                        Text(stringResource(R.string.history_undo_action))
                    }
                }
                if (canRestore) {
                    OutlinedButton(onClick = { onUiEvent(UiEvent.RestoreClicked(entry)) }) {
                        Text(stringResource(R.string.history_restore_action))
                    }
                }
            }
        }
    }
}

/** the author, with a robot on top when an agent made the changes */
@Composable
private fun EntryAvatar(entry: HistoryEntryModel) {
    val author = entry.author
    val color = authorColor(author)
    Box {
        UserAvatar(
            modifier = Modifier.size(36.dp),
            name = author.shownName,
            color = color,
            avatar = Avatar.of(author.accountId, author.avatar),
        )
        if (entry.agentNames.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = labelTextColorOn(color),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .padding(2.dp),
            )
        }
    }
}

private fun authorColor(author: HistoryAuthorModel): Color =
    parsePeerColor(author.color) ?: fallbackPeerColor(author.username.ifEmpty { author.name })

/** "Bob via Claude Code, Anna" */
@Composable
private fun whoText(entry: HistoryEntryModel): String {
    val via = stringResource(R.string.history_via)
    return entry.peopleWithAgents.joinToString(", ") { (person, agents) ->
        if (agents.isEmpty()) person.shownName else via.format(person.shownName, agents.joinToString(", "))
    }
}

@Composable
private fun summaryText(summary: HistorySummaryModel?): String? {
    summary ?: return null
    val lines = "+${summary.linesAdded} −${summary.linesRemoved}"
    return if (summary.headings.isEmpty()) lines else "$lines · ${summary.headings.joinToString(", ")}"
}

@Composable
private fun EntryDiff(
    diff: DiffState?,
    onHunkClicked: (DiffHunkModel) -> Unit,
    onRestoreSection: ((DiffHunkModel) -> Unit)?,
) {
    when (diff) {
        null, DiffState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is DiffState.Failed -> Text(stringResource(diff.message), color = MaterialTheme.colorScheme.error)
        DiffState.FirstVersion -> Text(stringResource(R.string.history_first_version), style = MaterialTheme.typography.bodySmall)
        is DiffState.Loaded -> if (diff.hunks.isEmpty()) {
            Text(stringResource(R.string.history_no_text_changes), style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                diff.hunks.forEach { hunk ->
                    Hunk(
                        hunk = hunk,
                        onClick = { onHunkClicked(hunk) },
                        onRestoreSection = onRestoreSection?.takeIf { hunk.heading != null }?.let { { it(hunk) } },
                    )
                }
            }
        }
    }
}

/** inline, as Markdown source; a long line scrolls sideways instead of wrapping, so the columns stay */
@Composable
internal fun Hunk(hunk: DiffHunkModel, onClick: () -> Unit, onRestoreSection: (() -> Unit)? = null) {
    val lines = remember(hunk) { diffLineTexts(hunk.lines) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = hunk.heading ?: stringResource(R.string.history_page_start),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.history_open_in_editor),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            lines.forEachIndexed { index, text ->
                val op = hunk.lines[index].op
                Text(
                    modifier = Modifier
                        .background(
                            when (op) {
                                DiffOp.ADDED -> AddedLine
                                DiffOp.REMOVED -> RemovedLine
                                DiffOp.CONTEXT -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 8.dp),
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                )
            }
        }
        if (onRestoreSection != null) {
            // names the section, because the hunk can reach into the next one
            TextButton(modifier = Modifier.align(Alignment.End), onClick = onRestoreSection) {
                Text(
                    text = stringResource(R.string.history_restore_section_action, hunk.heading.orEmpty()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** each line with its sign, and in a pair of a removed and an added line the changed words marked */
private fun diffLineTexts(lines: List<DiffLineModel>): List<AnnotatedString> {
    val partners = pairChangedLines(lines)
    return lines.mapIndexed { index, line ->
        val sign = when (line.op) {
            DiffOp.ADDED -> "+ "
            DiffOp.REMOVED -> "− "
            DiffOp.CONTEXT -> "  "
        }
        val highlights: List<Highlight> = partners[index]?.let { partner ->
            when (line.op) {
                DiffOp.REMOVED -> wordChanges(line.text, lines[partner].text).removed
                DiffOp.ADDED -> wordChanges(lines[partner].text, line.text).added
                DiffOp.CONTEXT -> emptyList()
            }
        }.orEmpty()
        val color = if (line.op == DiffOp.ADDED) AddedWords else RemovedWords
        buildAnnotatedString {
            append(sign)
            append(line.text)
            highlights.forEach { addStyle(SpanStyle(background = color), sign.length + it.start, sign.length + it.end) }
        }
    }
}

@CombinedPreview
@Composable
private fun HistoryScreenContentPreview() {
    val anna = HistoryAuthorModel("anna", "anna@mkdocseditor.invalid", "anna", "a1", "Anna", "#1971c2")
    val entries = listOf(
        HistoryEntryModel(
            revision = "b", time = Date(), author = anna, message = "Update guide/setup.md", path = "guide/setup.md",
            agents = listOf(HistoryAgentModel("Claude Code", "a1")), summary = HistorySummaryModel(2, 1, listOf("Install")),
        ),
        HistoryEntryModel(
            revision = "a", time = Date(0), author = HistoryAuthorModel("Test", "test@example.com"), message = "Initial",
            path = "guide/setup.md",
        ),
    )
    val hunk = DiffHunkModel(
        heading = "Install", headingOccurrence = 1, oldStart = 3, oldLines = 2, newStart = 3, newLines = 3,
        lines = listOf(
            DiffLineModel(DiffOp.CONTEXT, "## Install"),
            DiffLineModel(DiffOp.REMOVED, "Install the app on your phone."),
            DiffLineModel(DiffOp.ADDED, "Install the new app on the phone."),
            DiffLineModel(DiffOp.ADDED, "Then sign in."),
        ),
    )
    MkDocsEditorTheme {
        HistoryScreenContent(
            uiState = UiState(
                loading = false, entries = entries, expanded = "b", diffs = mapOf("b" to DiffState.Loaded(listOf(hunk))),
                canRestore = true, canRevert = true,
            ),
            onUiEvent = {},
        )
    }
}
