package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.SyncStatusStore
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ObserveStoredFolderUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ObserveTreeChangesUseCase
import org.mobilenativefoundation.store.store5.StoreReadResponseOrigin
import java.util.Date
import androidx.annotation.StringRes
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocseditor.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.event.subscribe
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigFlowUseCase
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.toFolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ComputePathToSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.CreateNewDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.CreateNewSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindParentSectionOfDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindParentSectionOfResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.GetBacklinksUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.GetSectionItemsUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.LoadWikiTreeUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.UploadResourceUseCase
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import javax.inject.Inject

@HiltViewModel
internal class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSectionItemsUseCase: GetSectionItemsUseCase,
    private val createNewSectionUseCase: CreateNewSectionUseCase,
    computePathToSectionUseCase: ComputePathToSectionUseCase,
    private val createNewDocumentUseCase: CreateNewDocumentUseCase,
    moveItemUseCase: MoveItemUseCase,
    private val syncStatusStore: SyncStatusStore,
    getBacklinksUseCase: GetBacklinksUseCase,
    loadWikiTreeUseCase: LoadWikiTreeUseCase,
    private val dataRepository: DataRepository,
    private val observeTreeChangesUseCase: ObserveTreeChangesUseCase,
    private val observeStoredFolderUseCase: ObserveStoredFolderUseCase,
    deleteItemUseCase: DeleteItemUseCase,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val uploadResourceUseCase: UploadResourceUseCase,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val findParentSectionOfDocumentUseCase: FindParentSectionOfDocumentUseCase,
    private val findParentSectionOfResourceUseCase: FindParentSectionOfResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
    private val eventBusManager: EventBusManager,
    private val authRepository: AuthRepository,
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
    private val localDocumentStore: LocalDocumentStore,
) : ViewModel() {

    /** the open folder; the path to it can be computed again from the database */
    private var savedSectionId by savedStateHandle.saved { ROOT_SECTION_ID }

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<FileBrowserEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private val navigator = SectionNavigator(
        initialSectionId = savedSectionId,
        pathTo = { computePathToSectionUseCase(it) },
        sectionChanges = dataRepository::observeSectionChanges,
    )

    private val itemActions = ItemActions(
        state = DialogSlot({ _uiState.value.currentDialogState }) { change ->
            _uiState.update { it.copy(currentDialogState = change(it.currentDialogState)) }
        },
        moveItem = moveItemUseCase,
        getBacklinks = { getBacklinksUseCase(it) },
        loadTree = { loadWikiTreeUseCase() },
        showMessage = { _events.send(FileBrowserEvent.Toast(it)) },
        reload = ::reload,
        deleteItem = { item, force -> deleteItemUseCase(item, force) },
    )

    /** every new value loads the open folder again */
    private val reloadRequests = MutableStateFlow(0)

    /** the value of [reloadRequests] that the user asked for by refreshing */
    private var refreshRequestedByUser = -1

    /** waits for the file picker; the file goes to the folder that was open when the upload started */
    private var pendingUpload: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun followParentFolder() = launch {
        // the back gesture shows the parent while it is dragged, so its content has to be there already
        _uiState.map { it.currentSectionPath.getOrNull(it.currentSectionPath.lastIndex - 1)?.id }
            .distinctUntilChanged()
            .flatMapLatest { parentId -> if (parentId == null) flowOf(null) else observeStoredFolderUseCase(parentId) }
            .collect { parent -> _uiState.update { old -> old.copy(parentFolderContent = parent) } }
    }

    init {
        followParentFolder()

        launch {
            localDocumentStore.unsyncedIds.collect { ids ->
                _uiState.update { old -> old.copy(unsyncedDocumentIds = ids) }
            }
        }

        eventBusManager.observe<BusEvent.CodeEditorBusEvent>().subscribe(viewModelScope) { event ->
            reportingErrors {
                when (event) {
                    is BusEvent.CodeEditorBusEvent.RevealDocument ->
                        findParentSectionOfDocumentUseCase(event.documentId)?.let { navigator.open(it.id) }

                    is BusEvent.CodeEditorBusEvent.GoToResource ->
                        findParentSectionOfResourceUseCase(event.resourceId)?.let { parent ->
                            navigator.open(parent.id)
                            parent.resources.firstOrNull { it.id == event.resourceId }?.let { onResourceClicked(it) }
                        }

                    is BusEvent.CodeEditorBusEvent.GoToSection -> navigator.open(event.sectionId)
                }
            }
        }

        launch {
            combine(
                getCurrentBackendConfigFlowUseCase(),
                authRepository.loginRequired,
            ) { config, loginRequired ->
                config?.id?.takeIf { it in loginRequired }
            }.distinctUntilChanged().collect { backendId ->
                _uiState.update { old -> old.copy(loginRequiredBackendId = backendId) }
            }
        }

        launch {
            // reload after signing in again
            authRepository.sessionChanges.collect { backendId ->
                val current = getCurrentBackendConfigFlowUseCase().first()
                if (current?.id == backendId && authRepository.isSignedIn(backendId)) {
                    reload()
                }
            }
        }

        launch {
            getCurrentBackendConfigFlowUseCase().collect { config ->
                _uiState.update { old ->
                    old.copy(lastSync = config?.let { syncStatusStore.lastSync(it.id) }, lastSyncFailed = false)
                }
            }
        }

        launch {
            getCurrentBackendConfigFlowUseCase().map { it?.id }.switchesOfServer().collect {
                // the open folder belongs to the previous server
                navigator.open(ROOT_SECTION_ID)
                reload()
            }
        }

        launch {
            navigator.currentSectionId.collect { savedSectionId = it }
        }

        launch {
            navigator.path.collect { path ->
                _uiState.update { old -> old.copy(currentSectionPath = path) }
            }
        }

        launch {
            combine(navigator.currentSectionId, reloadRequests) { sectionId, reload -> sectionId to reload }
                .collectLatest { (sectionId, reload) ->
                    getSectionItemsUseCase(sectionId).collect { response ->
                        onSectionResponse(sectionId, userAsked = reload == refreshRequestedByUser, response)
                    }
                }
        }
    }

    private suspend fun onSectionResponse(sectionId: String, userAsked: Boolean, response: StoreReadResponse<SectionData>) {
        if (response.isAnswerFromBackend()) onBackendAnswered()
        _uiState.update { old ->
            old.copy(
                isLoading = response is StoreReadResponse.Loading,
                folderError = old.folderError.takeUnless {
                    response is StoreReadResponse.Loading || response is StoreReadResponse.Data
                },
            )
        }
        when (response) {
            is StoreReadResponse.NoNewData,
            is StoreReadResponse.Data -> _uiState.update { old ->
                old.copy(folderContent = response.dataOrNull()?.toFolderContent())
            }

            is StoreReadResponse.Error.Exception -> {
                Timber.e(response.error)
                onLoadFailed(sectionId, userAsked, response.error.userMessage())
            }

            is StoreReadResponse.Error.Message -> {
                Timber.e(IllegalStateException(response.message))
                onLoadFailed(sectionId, userAsked, R.string.unknown_error)
            }

            else -> {
                // handled above
            }
        }
    }

    internal fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.DismissError -> setError(null)
                is UiEvent.Refresh -> {
                    refreshRequestedByUser = reloadRequests.value + 1
                    reload()
                }
                is UiEvent.SignInClicked -> uiState.value.loginRequiredBackendId?.let {
                    _events.send(FileBrowserEvent.OpenLogin(it))
                }

                is UiEvent.DocumentClicked -> onDocumentClicked(event.item)
                is UiEvent.ResourceClicked -> onResourceClicked(event.item)
                is UiEvent.SectionClicked -> navigator.open(event.item.id)
                is UiEvent.DocumentLongClicked -> showItemActions(WikiItem.Document(event.item))
                is UiEvent.ResourceLongClicked -> showItemActions(WikiItem.Resource(event.item))
                is UiEvent.SectionLongClicked -> showItemActions(WikiItem.Section(event.item))
                is UiEvent.PathItemClicked -> navigator.open(event.section.id)
                is UiEvent.NavigateUp -> navigator.goUp()

                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    FileBrowserFabId.CreateDocument -> showDialog(
                        DialogState.CreateItem(NewItemKind.Document, navigator.currentSectionId.value)
                    )

                    FileBrowserFabId.CreateSection -> showDialog(
                        DialogState.CreateItem(NewItemKind.Section, navigator.currentSectionId.value)
                    )

                    FileBrowserFabId.UploadResource -> uploadResource()
                    FileBrowserFabId.FAB -> {
                        // handled internally
                    }
                }

                is UiEvent.CreateItemConfirmed -> {
                    showDialog(null)
                    createItem(event.dialog, event.name.trim())
                }

                is UiEvent.ItemActionClicked -> itemActions.onActionClicked(event.item, event.action)
                is UiEvent.MovePreviewRequested -> itemActions.previewMove(event.parentId, event.name)
                is UiEvent.MoveBackClicked -> itemActions.backToTarget()
                is UiEvent.MoveApplyClicked -> itemActions.applyMove()
                is UiEvent.BacklinkClicked -> {
                    showDialog(null)
                    _events.send(FileBrowserEvent.OpenDocumentEditor(event.documentId))
                }

                is UiEvent.DeleteConfirmed -> reportingErrors { itemActions.applyDelete() }

                is UiEvent.DismissDialog -> itemActions.dismiss()

                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)
            }
        }
    }

    private fun StoreReadResponse<SectionData>.isAnswerFromBackend() =
        this is StoreReadResponse.NoNewData || (this is StoreReadResponse.Data && origin is StoreReadResponseOrigin.Fetcher)

    private suspend fun onBackendAnswered() {
        val now = Date()
        getCurrentBackendConfigFlowUseCase().first()?.let { syncStatusStore.setLastSync(it.id, now) }
        _uiState.update { old -> old.copy(lastSync = now, lastSyncFailed = false) }
    }

    private fun onLoadFailed(sectionId: String, userAsked: Boolean, @StringRes message: Int) {
        _uiState.update { old -> old.copy(lastSyncFailed = true) }
        val contentShown = uiState.value.folderContent?.sectionId == sectionId
        when (placeOfLoadFailure(contentShown, userAsked)) {
            LoadFailurePlace.InPlaceOfContent -> _uiState.update { old ->
                // the content of the last folder must not pass as this one
                old.copy(folderContent = null, folderError = message)
            }

            LoadFailurePlace.Snackbar -> setError(message, canRetry = true)
            LoadFailurePlace.Nowhere -> Unit
        }
    }

    private fun setError(@StringRes message: Int?, canRetry: Boolean = false) {
        _uiState.update { old ->
            old.copy(isLoading = false, error = message?.let { ScreenError(it, canRetry) })
        }
    }

    /** Runs what the user asked for and shows a failure on the screen */
    private suspend fun reportingErrors(block: suspend () -> Unit) {
        try {
            block()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(ex.userMessage())
        }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.FileBrowser) {
        when (action) {
            is TopAppBarAction.FileBrowser.Search -> _events.send(FileBrowserEvent.OpenSearch)

            is TopAppBarAction.FileBrowser.Profile -> {
                // the account screen sends the user to the login when there is no session
                getCurrentBackendConfigFlowUseCase().first()?.let {
                    _events.send(FileBrowserEvent.OpenAccount(it.id))
                }
            }
        }
    }

    private fun showItemActions(item: WikiItem) = showDialog(DialogState.ItemActions(item))

    private fun showDialog(dialogState: DialogState?) {
        _uiState.update { old -> old.copy(currentDialogState = dialogState) }
    }

    private suspend fun createItem(dialog: DialogState.CreateItem, name: String) = reportingErrors {
        when (dialog.kind) {
            NewItemKind.Section -> {
                createNewSectionUseCase(name, dialog.sectionId)
                reload()
            }

            NewItemKind.Document -> {
                val newDocumentId = createNewDocumentUseCase(dialog.sectionId, name)
                reload()
                // and open the editor right away
                _events.send(FileBrowserEvent.OpenDocumentEditor(newDocumentId))
            }
        }
    }

    /**
     * Keeps the stored tree up to date until cancelled, for changes made elsewhere. It only writes to the store, so the
     * open folder follows through its stream without a loading indicator, and a failure stays quiet.
     */
    internal suspend fun followTreeChanges() {
        observeTreeChangesUseCase().conflate().collect {
            try {
                dataRepository.sectionStore.fresh(ROOT_SECTION_ID)
                onBackendAnswered()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e) { "Could not follow a change of the tree" }
            }
        }
    }

    private fun reload() {
        setError(null)
        reloadRequests.update { it + 1 }
    }

    private suspend fun uploadResource() {
        pendingUpload?.cancel()
        val sectionId = navigator.currentSectionId.value
        pendingUpload = launch {
            val uri = eventBusManager.observe<BusEvent.FeatureEvent.FilePickerEvent.FilePickerResult>().first().uri
                ?: return@launch
            reportingErrors {
                uploadResourceUseCase(sectionId, uri)
                reload()
            }
        }
        _events.send(FileBrowserEvent.OpenResourceSelection)
    }

    private suspend fun onDocumentClicked(entity: DocumentData) {
        if (isOfflineModeEnabledFlowUseCase().value && entity.isOfflineAvailable.not()) {
            _events.send(FileBrowserEvent.Toast(BrowserMessage.NotAvailableOffline))
            return
        }
        _events.send(FileBrowserEvent.OpenDocumentEditor(entity.id))
    }

    private suspend fun onResourceClicked(entity: ResourceData) {
        val result = try {
            _events.send(FileBrowserEvent.Toast(BrowserMessage.Downloading))
            downloadResourceUseCase.invoke(entity.id, entity.name)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(ex.userMessage())
            return
        }
        reportingErrors { shareFileUseCase(result) }
    }

    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID: String = "root"
    }
}

internal sealed class UiEvent {
    data object DismissError : UiEvent()
    data object Refresh : UiEvent()

    data class DocumentClicked(val item: DocumentData) : UiEvent()
    data class DocumentLongClicked(val item: DocumentData) : UiEvent()
    data class ResourceClicked(val item: ResourceData) : UiEvent()
    data class ResourceLongClicked(val item: ResourceData) : UiEvent()
    data class SectionClicked(val item: SectionData) : UiEvent()
    data class SectionLongClicked(val item: SectionData) : UiEvent()

    data class PathItemClicked(val section: SectionItem) : UiEvent()
    data object NavigateUp : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab<FileBrowserFabId>) : UiEvent()

    data class CreateItemConfirmed(val dialog: DialogState.CreateItem, val name: String) : UiEvent()

    data class ItemActionClicked(val item: WikiItem, val action: ItemAction) : UiEvent()

    /** asks for a dry run: [parentId] null renames in place, [name] null keeps the name */
    data class MovePreviewRequested(val parentId: String?, val name: String?) : UiEvent()
    data object MoveBackClicked : UiEvent()
    data object MoveApplyClicked : UiEvent()

    data class BacklinkClicked(val documentId: String) : UiEvent()

    data object DeleteConfirmed : UiEvent()

    data class TopAppBarActionClicked(val action: TopAppBarAction.FileBrowser) : UiEvent()

    data object DismissDialog : UiEvent()

    data object SignInClicked : UiEvent()
}

internal enum class ItemAction { Rename, Move, UsedBy, Delete }

internal sealed class FileBrowserEvent {
    data object OpenSearch : FileBrowserEvent()
    data class OpenDocumentEditor(val documentId: String) : FileBrowserEvent()
    data class Toast(val message: BrowserMessage) : FileBrowserEvent()

    data class OpenAccount(val backendId: Long) : FileBrowserEvent()
    data object OpenResourceSelection : FileBrowserEvent()
    data class OpenLogin(val backendId: Long) : FileBrowserEvent()
}
