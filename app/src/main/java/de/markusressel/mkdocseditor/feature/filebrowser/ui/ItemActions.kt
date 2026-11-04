package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The dialogs for one item: rename, move, what links to it, delete. A late answer of the server only goes into the dialog
 * it belongs to.
 */
internal class ItemActions(
    private val state: MutableStateFlow<UiState>,
    private val moveItem: MoveItemUseCase,
    private val getBacklinks: suspend (WikiItem) -> Result<List<BacklinkModel>>,
    private val loadTree: suspend () -> WikiTree?,
    private val showMessage: suspend (String) -> Unit,
    private val reload: () -> Unit,
) {
    suspend fun onActionClicked(item: WikiItem, action: ItemAction) {
        when (action) {
            ItemAction.Rename -> showDialog(DialogState.Move(item = item, renameOnly = true))
            ItemAction.Move -> startMove(item)
            ItemAction.UsedBy -> showBacklinks(item)
            ItemAction.Delete -> confirmDelete(item)
        }
    }

    private suspend fun startMove(item: WikiItem) {
        showDialog(DialogState.Move(item = item, renameOnly = false))
        val tree = loadTree()
        updateMoveDialog {
            if (tree == null) {
                it.copy(error = MoveOutcome.Failed(null, null, "Could not load the folders"))
            } else {
                it.copy(tree = tree)
            }
        }
    }

    private suspend fun showBacklinks(item: WikiItem) {
        showDialog(DialogState.Backlinks(item))
        getBacklinks(item).fold(
            onSuccess = { list -> updateBacklinksDialog(item) { it.copy(backlinks = list) } },
            onFailure = { ex ->
                Timber.e(ex)
                updateBacklinksDialog(item) {
                    if ((ex as? FuelError)?.isUnsupportedEndpoint() == true) {
                        it.copy(unsupported = true)
                    } else {
                        it.copy(error = ex.userMessage())
                    }
                }
            },
        )
    }

    private suspend fun confirmDelete(item: WikiItem) {
        showDialog(DialogState.DeleteConfirmation(item))
        val list = getBacklinks(item).getOrNull()
        // references from inside a section are deleted with it
        val count = list?.count { !it.internal }
        state.update { old ->
            val dialog = old.currentDialogState
            if (dialog is DialogState.DeleteConfirmation && dialog.item.id == item.id) {
                old.copy(currentDialogState = dialog.copy(usedBy = count, usedByUnavailable = list == null))
            } else old
        }
    }

    /** a dry run: [parentId] null renames in place, [name] null keeps the name */
    suspend fun previewMove(parentId: String?, name: String?) {
        val dialog = state.value.currentDialogState as? DialogState.Move ?: return
        updateMoveDialog { it.copy(working = true, error = null) }
        when (val outcome = moveItem(dialog.item, parentId, name, dryRun = true)) {
            is MoveOutcome.Done -> updateMoveDialog {
                it.copy(working = false, preview = MovePreview(parentId, name, outcome.result))
            }

            is MoveOutcome.Failed -> if (outcome.reason == MoveOutcome.Failed.UNSUPPORTED && dialog.renameOnly && name != null) {
                // an older backend without POST /move/: rename the old way, without a preview
                renameWithoutPreview(dialog.item, name)
            } else {
                updateMoveDialog { it.copy(working = false, error = outcome) }
            }
        }
    }

    private suspend fun renameWithoutPreview(item: WikiItem, name: String) {
        val failure = moveItem.renameWithoutPreview(item, name)
        if (failure == null) {
            showDialog(null)
            showMessage("Renamed. This server does not update links to it.")
            reload()
        } else {
            updateMoveDialog { it.copy(working = false, error = failure) }
        }
    }

    suspend fun applyMove() {
        val dialog = state.value.currentDialogState as? DialogState.Move ?: return
        val preview = dialog.preview ?: return
        updateMoveDialog { it.copy(working = true, error = null) }
        when (val outcome = moveItem(dialog.item, preview.parentId, preview.name, dryRun = false)) {
            is MoveOutcome.Done -> {
                showDialog(null)
                showMessage(movedMessage(outcome.result.documents.size, outcome.result.newPath))
                reload()
            }

            is MoveOutcome.Failed -> updateMoveDialog { it.copy(working = false, error = outcome) }
        }
    }

    fun backToTarget() = updateMoveDialog { it.copy(preview = null, error = null) }

    private fun movedMessage(documents: Int, newPath: String): String {
        val updated = if (documents == 1) "1 page updated" else "$documents pages updated"
        return "Now at /$newPath ($updated)"
    }

    private fun showDialog(dialog: DialogState?) = state.update { it.copy(currentDialogState = dialog) }

    private fun updateMoveDialog(update: (DialogState.Move) -> DialogState.Move) = state.update { old ->
        val dialog = old.currentDialogState
        if (dialog is DialogState.Move) old.copy(currentDialogState = update(dialog)) else old
    }

    private fun updateBacklinksDialog(item: WikiItem, update: (DialogState.Backlinks) -> DialogState.Backlinks) =
        state.update { old ->
            val dialog = old.currentDialogState
            if (dialog is DialogState.Backlinks && dialog.item.id == item.id) old.copy(currentDialogState = update(dialog)) else old
        }
}
