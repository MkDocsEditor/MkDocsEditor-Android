package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocsrestclient.dashboard.PinScope
import de.markusressel.mkdocseditor.feature.dashboard.domain.PinOutcome
import de.markusressel.mkdocseditor.feature.dashboard.domain.PinActions
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteOutcome

/**
 * Where a screen keeps the dialog of [ItemActions]
 */
internal class DialogSlot(
    private val read: () -> DialogState?,
    private val write: ((DialogState?) -> DialogState?) -> Unit,
) {
    val value: DialogState? get() = read()

    fun update(change: (DialogState?) -> DialogState?) = write(change)
}

/**
 * The dialogs for one item: rename, move, what links to it, delete. A late answer of the server only goes into the dialog
 * it belongs to. The file browser and the editor both use them.
 *
 * @param deleteItem deletes an item, `force` also while someone has it open
 * @param ownConnection the screen is connected to the item itself (the editor of a page), so the server would always
 * count it as open: the dialog then says whether others have it open, and deletes with force
 * @param onDeleted after the item is gone
 * @param pins pins pages to the dashboard, null where that is not offered
 */
internal class ItemActions(
    private val state: DialogSlot,
    private val moveItem: MoveItemUseCase,
    private val getBacklinks: suspend (WikiItem) -> Result<List<BacklinkModel>>,
    private val loadTree: suspend () -> WikiTree?,
    private val showMessage: suspend (BrowserMessage) -> Unit,
    private val reload: () -> Unit,
    private val deleteItem: suspend (WikiItem, force: Boolean) -> DeleteOutcome,
    private val ownConnection: Boolean = false,
    private val onDeleted: suspend (WikiItem) -> Unit = {},
    private val pins: PinActions? = null,
) {
    /** @param openElsewhere the screen knows that others have [item] open right now */
    suspend fun onActionClicked(item: WikiItem, action: ItemAction, openElsewhere: Boolean = false) {
        when (action) {
            ItemAction.Rename -> showDialog(DialogState.Move(item = item, renameOnly = true))
            ItemAction.Move -> startMove(item)
            ItemAction.UsedBy -> showBacklinks(item)
            ItemAction.Pin -> showPins(item)
            ItemAction.Delete -> confirmDelete(item, openElsewhere)
        }
    }

    private suspend fun showPins(item: WikiItem) {
        val pins = pins ?: return
        val known = pins.familyPermission.known()
        showDialog(DialogState.Pin(item, mayPinForEveryone = known != false))
        val current = pins.current()
        updatePinDialog(item) { it.copy(pins = current, unavailable = current == null) }
        // asked only once per account, later dialogs use the stored role
        if (known == null && current != null) {
            val allowed = pins.familyPermission.refresh()
            updatePinDialog(item) { it.copy(mayPinForEveryone = allowed != false) }
        }
    }

    /**
     * Pins the page of the pin dialog, or removes its pin
     *
     * @throws Exception when the server fails, the dialog stays open then
     */
    suspend fun setPinned(scope: PinScope, pinned: Boolean) {
        val pins = pins ?: return
        val dialog = state.value as? DialogState.Pin ?: return
        if (dialog.working) return
        updatePinDialog(dialog.item) { it.copy(working = true, forbidden = false) }
        val outcome = try {
            pins.set(scope, dialog.item.id, pinned)
        } catch (e: Exception) {
            updatePinDialog(dialog.item) { it.copy(working = false) }
            throw e
        }
        updatePinDialog(dialog.item) {
            when (outcome) {
                is PinOutcome.Changed -> it.copy(working = false, pins = outcome.pins)
                PinOutcome.Forbidden -> it.copy(working = false, forbidden = true)
            }
        }
        if (outcome == PinOutcome.Forbidden) {
            // the role changed since it was stored, or was not known
            val allowed = pins.familyPermission.refresh()
            updatePinDialog(dialog.item) { it.copy(mayPinForEveryone = allowed != false) }
        }
    }

    private suspend fun startMove(item: WikiItem) {
        showDialog(DialogState.Move(item = item, renameOnly = false))
        val tree = loadTree()
        updateMoveDialog {
            if (tree == null) {
                it.copy(error = MoveOutcome.Failed(null, MoveOutcome.Failed.FOLDERS_UNAVAILABLE, ""))
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

    private suspend fun confirmDelete(item: WikiItem, openElsewhere: Boolean = false) {
        showDialog(DialogState.DeleteConfirmation(item, openElsewhere = openElsewhere))
        val list = getBacklinks(item).getOrNull()
        // references from inside a section are deleted with it
        val count = list?.count { !it.internal }
        updateDeleteDialog(item) { it.copy(usedBy = count, usedByUnavailable = list == null) }
    }

    /**
     * Deletes the item of the confirmation. When someone has it open, the dialog says so first, and the next
     * confirmation deletes it anyway.
     *
     * @throws Exception when the server fails, the dialog is closed then
     */
    suspend fun applyDelete() {
        val dialog = state.value as? DialogState.DeleteConfirmation ?: return
        if (dialog.working) return
        updateDeleteDialog(dialog.item) { it.copy(working = true) }
        val outcome = try {
            deleteItem(dialog.item, ownConnection || dialog.openElsewhere)
        } catch (e: Exception) {
            showDialog(null)
            throw e
        }
        when (outcome) {
            DeleteOutcome.Deleted -> {
                showDialog(null)
                onDeleted(dialog.item)
                reload()
            }

            DeleteOutcome.InUse -> updateDeleteDialog(dialog.item) { it.copy(working = false, openElsewhere = true) }
        }
    }

    /** a dry run: [parentId] null renames in place, [name] null keeps the name */
    suspend fun previewMove(parentId: String?, name: String?) {
        val dialog = state.value as? DialogState.Move ?: return
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
            showMessage(BrowserMessage.RenamedWithoutLinks)
            reload()
        } else {
            updateMoveDialog { it.copy(working = false, error = failure) }
        }
    }

    suspend fun applyMove() {
        val dialog = state.value as? DialogState.Move ?: return
        val preview = dialog.preview ?: return
        updateMoveDialog { it.copy(working = true, error = null) }
        when (val outcome = moveItem(dialog.item, preview.parentId, preview.name, dryRun = false)) {
            is MoveOutcome.Done -> {
                showDialog(null)
                showMessage(BrowserMessage.Moved(outcome.result.newPath, outcome.result.documents.size))
                reload()
            }

            is MoveOutcome.Failed -> updateMoveDialog { it.copy(working = false, error = outcome) }
        }
    }

    fun backToTarget() = updateMoveDialog { it.copy(preview = null, error = null) }

    fun dismiss() = showDialog(null)

    private fun showDialog(dialog: DialogState?) = state.update { dialog }

    private fun updateMoveDialog(update: (DialogState.Move) -> DialogState.Move) = state.update { old ->
        if (old is DialogState.Move) update(old) else old
    }

    private fun updateBacklinksDialog(item: WikiItem, update: (DialogState.Backlinks) -> DialogState.Backlinks) =
        state.update { old ->
            if (old is DialogState.Backlinks && old.item.id == item.id) update(old) else old
        }

    private fun updatePinDialog(item: WikiItem, update: (DialogState.Pin) -> DialogState.Pin) =
        state.update { old ->
            if (old is DialogState.Pin && old.item.id == item.id) update(old) else old
        }

    private fun updateDeleteDialog(item: WikiItem, update: (DialogState.DeleteConfirmation) -> DialogState.DeleteConfirmation) =
        state.update { old ->
            if (old is DialogState.DeleteConfirmation && old.item.id == item.id) update(old) else old
        }
}
