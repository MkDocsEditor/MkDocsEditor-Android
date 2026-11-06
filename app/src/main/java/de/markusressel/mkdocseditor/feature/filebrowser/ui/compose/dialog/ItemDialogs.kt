package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog

import android.content.Context
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

internal fun BrowserMessage.text(context: Context): String = when (this) {
    BrowserMessage.NotAvailableOffline -> context.getString(R.string.document_not_available_offline)
    BrowserMessage.Downloading -> context.getString(R.string.resource_downloading)
    BrowserMessage.RenamedWithoutLinks -> context.getString(R.string.renamed_without_links)
    is BrowserMessage.Moved -> context.resources.getQuantityString(R.plurals.moved_to, documents, newPath, documents)
}
