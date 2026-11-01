package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PickerScaffold
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogState
import de.markusressel.mkdocseditor.feature.filebrowser.ui.ItemAction
import de.markusressel.mkdocseditor.feature.filebrowser.ui.MovePreview
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.WikiTreeBrowser

/** the file name of an item: documents with `.md` */
internal val WikiItem.displayName: String
    get() = if (this is WikiItem.Document) "$name.md" else name

/**
 * The actions for an item of the file browser (long press)
 */
@Composable
internal fun ItemActionsDialog(
    item: WikiItem,
    onAction: (ItemAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ActionEntry(Icons.Outlined.DriveFileRenameOutline, R.string.item_action_rename, "action-rename") { onAction(ItemAction.Rename) }
                ActionEntry(Icons.AutoMirrored.Outlined.DriveFileMove, R.string.item_action_move, "action-move") { onAction(ItemAction.Move) }
                ActionEntry(Icons.Outlined.Link, R.string.item_action_used_by, "action-used-by") { onAction(ItemAction.UsedBy) }
                ActionEntry(Icons.Outlined.Delete, R.string.item_action_delete, "action-delete") { onAction(ItemAction.Delete) }
            }
        }
    }
}

@Composable
private fun ActionEntry(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
    )
}

/** the ids of a section and every section below it */
internal fun sectionSubtreeIds(section: SectionData): Set<String> =
    setOf(section.id) + section.subsections.flatMap { sectionSubtreeIds(it) }

/**
 * Moves or renames an item: pick the target folder (or type the new name), look at the dry
 * run of the backend, then apply it
 *
 * @param onPreview asks for a dry run; parent null renames in place, name null keeps the name
 */
@Composable
internal fun MoveDialog(
    state: DialogState.Move,
    onPreview: (parentId: String?, name: String?) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val item = state.item
    val title = stringResource(if (state.renameOnly) R.string.rename_title else R.string.move_title, item.displayName)
    PickerScaffold(
        title = title,
        tabs = emptyList(),
        selectedTab = "",
        onTabSelected = {},
        onDismiss = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("move-working"))
            }
            val preview = state.preview
            if (preview == null) {
                MoveTargetContent(state, onPreview)
            } else {
                MovePreviewContent(preview, state.working, state.error, onBack, onApply)
            }
        }
    }
}

@Composable
private fun MoveTargetContent(
    state: DialogState.Move,
    onPreview: (parentId: String?, name: String?) -> Unit,
) {
    val item = state.item
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    val newName = name.trim().takeIf { it != item.name }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.move_name_label)) },
            supportingText = if (item is WikiItem.Document) {
                { Text(stringResource(R.string.move_name_document_hint)) }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("move-name"),
        )
        state.error?.let { MoveErrorText(it) }
    }

    if (state.renameOnly) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onPreview(null, newName) },
                enabled = !state.working && name.isNotBlank() && newName != null,
                modifier = Modifier.testTag("move-preview"),
            ) {
                Text(stringResource(R.string.move_preview_button))
            }
        }
        return
    }

    val tree = state.tree
    if (tree == null) {
        if (state.error == null) {
            Text(
                text = stringResource(R.string.move_loading_tree),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val parentId = tree.parentOf(item)?.id
    var browsing by rememberSaveable(item.id) { mutableStateOf(parentId ?: tree.root.id) }
    val disabled = if (item is WikiItem.Section) tree.section(item.id)?.let(::sectionSubtreeIds).orEmpty() else emptySet()
    val unpickable = if (newName == null) setOfNotNull(parentId) else emptySet()
    WikiTreeBrowser(
        tree = tree,
        sectionId = browsing,
        onSectionChange = { browsing = it },
        onPick = { target ->
            if (!state.working && name.isNotBlank()) onPreview(target.id, newName)
        },
        modifier = Modifier.padding(horizontal = 16.dp),
        showDocuments = false,
        showResources = false,
        pickSections = true,
        pickCurrentLabel = stringResource(R.string.move_here),
        disabledIds = disabled,
        unpickableIds = unpickable,
        pickFolderIcon = Icons.AutoMirrored.Outlined.DriveFileMove,
        pickFolderDescription = R.string.move_to_folder,
    )
}

@Composable
private fun MoveErrorText(error: MoveOutcome.Failed) {
    val text = when (error.reason) {
        "exists" -> stringResource(R.string.move_error_exists)
        "invalid-name" -> stringResource(R.string.move_error_invalid_name)
        "blacklisted" -> stringResource(R.string.move_error_blacklisted)
        "into-itself" -> stringResource(R.string.move_error_into_itself)
        "root" -> stringResource(R.string.move_error_root)
        "no-change" -> stringResource(R.string.move_error_no_change)
        MoveOutcome.Failed.UNSUPPORTED -> stringResource(R.string.move_error_unsupported)
        else -> error.message
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .testTag("move-error"),
    )
}

@Composable
private fun MovePreviewContent(
    preview: MovePreview,
    working: Boolean,
    error: MoveOutcome.Failed?,
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
    val result = preview.result
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("move-preview-list"),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.move_preview_from_to, "/" + result.oldPath, "/" + result.newPath),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (result.moved.size > 1) {
                        Text(pluralStringResource(R.plurals.move_preview_moved_items, result.moved.size, result.moved.size))
                    }
                    if (result.documents.isEmpty() && result.config.isEmpty()) {
                        Text(stringResource(R.string.move_preview_no_edits), modifier = Modifier.testTag("move-no-edits"))
                    } else if (result.documents.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.move_preview_documents, result.documents.size, result.documents.size))
                    }
                    error?.let { MoveErrorText(it) }
                }
            }
            items(result.documents, key = { "d-" + it.documentId }) { document ->
                val path = if (document.newPath != document.path) {
                    stringResource(R.string.move_preview_from_to, document.path, document.newPath)
                } else {
                    document.path
                }
                EditGroup(title = path, edits = document.edits.map { Triple(it.line, it.before, it.after) })
            }
            if (result.config.isNotEmpty()) {
                item(key = "config") {
                    EditGroup(
                        title = stringResource(R.string.move_preview_config),
                        edits = result.config.map { Triple(it.line, it.before, it.after) },
                    )
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack, enabled = !working, modifier = Modifier.testTag("move-back")) {
                Text(stringResource(R.string.move_back))
            }
            Button(onClick = onApply, enabled = !working, modifier = Modifier.testTag("move-apply")) {
                Text(stringResource(R.string.move_apply))
            }
        }
    }
}

@Composable
private fun EditGroup(title: String, edits: List<Triple<Int, String, String>>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("move-edit-group"))
        edits.forEach { (line, before, after) ->
            Text(
                text = stringResource(R.string.move_preview_line, line),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "- $before",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "+ $after",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The documents that link to an item; a tap opens the document
 */
@Composable
internal fun BacklinksDialog(
    state: DialogState.Backlinks,
    onOpenDocument: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.backlinks_title, state.item.displayName),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val backlinks = state.backlinks
                when {
                    state.unsupported -> Text(
                        text = stringResource(R.string.backlinks_unsupported),
                        modifier = Modifier.testTag("backlinks-unsupported"),
                    )
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    backlinks == null -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                    backlinks.isEmpty() -> Text(
                        text = stringResource(R.string.backlinks_none),
                        modifier = Modifier.testTag("backlinks-none"),
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                        items(backlinks) { backlink ->
                            ListItem(
                                modifier = Modifier
                                    .testTag("backlink")
                                    .clickable { onOpenDocument(backlink.source.id) },
                                headlineContent = { Text(backlink.source.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        text = stringResource(R.string.backlinks_line, backlink.line, backlink.destination),
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = if (backlink.internal) {
                                    { Text(stringResource(R.string.backlinks_internal), style = MaterialTheme.typography.labelSmall) }
                                } else null,
                            )
                        }
                    }
                }
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
