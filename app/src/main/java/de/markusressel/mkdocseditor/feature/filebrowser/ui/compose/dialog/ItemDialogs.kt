package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocsrestclient.dashboard.PinScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.android.textResource
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.BrowserMessage
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogState
import de.markusressel.mkdocseditor.feature.filebrowser.ui.ItemAction

/**
 * The dialogs of [de.markusressel.mkdocseditor.feature.filebrowser.ui.ItemActions], in the file browser and in the editor.
 * Other dialogs show nothing here.
 */
@Composable
internal fun ItemDialogs(
    dialogState: DialogState?,
    onAction: (WikiItem, ItemAction) -> Unit,
    onMovePreview: (parentId: String?, name: String?) -> Unit,
    onMoveBack: () -> Unit,
    onMoveApply: () -> Unit,
    onBacklinkClicked: (documentId: String) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDismissRequest: () -> Unit,
    onPinChanged: (PinScope, pinned: Boolean) -> Unit = { _, _ -> },
) {
    when (dialogState) {
        is DialogState.ItemActions -> ItemActionsDialog(
            item = dialogState.item,
            onAction = { onAction(dialogState.item, it) },
            onDismissRequest = onDismissRequest,
        )

        is DialogState.Move -> MoveDialog(
            state = dialogState,
            onPreview = onMovePreview,
            onBack = onMoveBack,
            onApply = onMoveApply,
            onDismissRequest = onDismissRequest,
        )

        is DialogState.Backlinks -> BacklinksDialog(
            state = dialogState,
            onOpenDocument = onBacklinkClicked,
            onDismissRequest = onDismissRequest,
        )

        is DialogState.DeleteConfirmation -> DeleteItemDialog(dialogState, onDeleteConfirmed, onDismissRequest)

        is DialogState.Pin -> PinDialog(dialogState, onPinChanged, onDismissRequest)

        else -> {}
    }
}

@Composable
private fun DeleteItemDialog(
    dialogState: DialogState.DeleteConfirmation,
    onDeleteConfirmed: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val (title, message) = when (dialogState.item) {
        is WikiItem.Document -> R.string.delete_document_confirmation_title to R.string.delete_document_confirmation_message
        is WikiItem.Section -> R.string.delete_section_confirmation_title to R.string.delete_section_confirmation_message
        is WikiItem.Resource -> R.string.delete_resource_confirmation_title to R.string.delete_resource_confirmation_message
    }
    val usedBy = dialogState.usedBy
    val usedByText = when {
        dialogState.usedByUnavailable -> null
        usedBy == null -> stringResource(R.string.delete_used_by_loading)
        usedBy == 0 -> stringResource(R.string.delete_used_by_none)
        else -> pluralStringResource(R.plurals.delete_used_by_count, usedBy, usedBy)
    }
    val openElsewhereText = if (dialogState.openElsewhere) {
        stringResource(
            if (dialogState.item is WikiItem.Section) R.string.delete_section_open_elsewhere else R.string.delete_document_open_elsewhere
        )
    } else null
    val text = textResource(message)
    DeleteConfirmationDialog(
        title = stringResource(title),
        message = buildAnnotatedString {
            append(text)
            listOfNotNull(usedByText, openElsewhereText).forEach {
                append("\n\n")
                append(it)
            }
        },
        confirmText = stringResource(if (dialogState.openElsewhere) R.string.delete_anyway else R.string.delete),
        working = dialogState.working,
        onConfirmClicked = onDeleteConfirmed,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun PinDialog(
    state: DialogState.Pin,
    onPinChanged: (PinScope, pinned: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.pin_title, state.item.shownTitle)) },
        text = {
            Column {
                state.item.secondaryName?.let {
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.pin_description))
                when {
                    state.unavailable -> Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = stringResource(R.string.pin_unavailable),
                        color = MaterialTheme.colorScheme.error,
                    )

                    state.pins == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))

                    else -> {
                        PinSwitch(R.string.pin_for_me, state.isPinned(PinScope.Personal), !state.working) {
                            onPinChanged(PinScope.Personal, it)
                        }
                        // a reader still sees whether the page is pinned for everyone
                        PinSwitch(
                            R.string.pin_for_everyone,
                            state.isPinned(PinScope.Family),
                            !state.working && state.mayPinForEveryone,
                        ) {
                            onPinChanged(PinScope.Family, it)
                        }
                        if (state.forbidden || !state.mayPinForEveryone) {
                            Text(
                                text = stringResource(R.string.pin_for_everyone_forbidden),
                                color = if (state.forbidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun PinSwitch(@StringRes label: Int, checked: Boolean?, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(modifier = Modifier.weight(1f), text = stringResource(label))
        Switch(checked = checked == true, enabled = enabled && checked != null, onCheckedChange = onCheckedChange)
    }
}

internal fun BrowserMessage.text(context: Context): String = when (this) {
    BrowserMessage.NotAvailableOffline -> context.getString(R.string.document_not_available_offline)
    BrowserMessage.RenamedWithoutLinks -> context.getString(R.string.renamed_without_links)
    is BrowserMessage.Moved -> context.resources.getQuantityString(R.plurals.moved_to, documents, newPath, documents)
    is BrowserMessage.MoveFailed -> context.getString(R.string.move_failed_later, name, context.getString(reason))
}
