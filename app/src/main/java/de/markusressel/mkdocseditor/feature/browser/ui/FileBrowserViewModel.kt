package de.markusressel.mkdocseditor.feature.browser.ui

import androidx.lifecycle.ViewModel
import com.dropbox.android.external.store4.StoreResponse
import com.github.ajalt.timberkt.Timber
import com.hadilq.liveevent.LiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.ROOT_SECTION
import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.CreateNewDocumentUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.CreateNewSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.GetCurrentSectionPathUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.GetSectionContentUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.RefreshSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.SearchUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.SectionItem
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import javax.inject.Inject


@HiltViewModel
internal class FileBrowserViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val restClient: IMkDocsRestClient,
    private val refreshSectionUseCase: RefreshSectionUseCase,
    private val getSectionContentUseCase: GetSectionContentUseCase,
    private val createNewSectionUseCase: CreateNewSectionUseCase,
    private val searchUseCase: SearchUseCase,
    private val getCurrentSectionPathUseCase: GetCurrentSectionPathUseCase,
    private val createNewDocumentUseCase: CreateNewDocumentUseCase,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<FileBrowserEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private val backstack: Stack<SectionBackstackItem> = Stack()
        get() {
            if (field.size == 0) {
                // root is always the first element in the backstack
                field.add(ROOT_SECTION)
            }
            return field
        }

    internal val currentSearchResults = combine(
        uiState.map { it.currentSearchFilter }.distinctUntilChanged(),
        uiState.map { it.isSearching }.distinctUntilChanged(),
    ) { currentSearchFilter, isSearching ->
        when {
            isSearching -> searchUseCase(currentSearchFilter)
            else -> emptyList()
        }
    }

    private val currentSectionId = MutableStateFlow(ROOT_SECTION_ID)

    internal val openDocumentEditorEvent = LiveEvent<String>()

    init {
        launch {
            uiState.map { it.isSearching }.distinctUntilChanged().collect { isSearching ->
                if (isSearching.not()) {
                    showTopLevel()
                }
            }
        }

        var sectionJob: Job? = null
        launch {
            currentSectionId.collectLatest { sectionId ->
                sectionJob?.cancel()
                sectionJob = launch {
                    try {
                        getSectionContentUseCase(
                            sectionId = sectionId,
                            refresh = true
                        ).collect { response ->
                            if (response is StoreResponse.Error) {
                                showError(
                                    errorMessage = response.errorMessageOrNull()
                                        ?: "Error fetching data"
                                )
                            }

                            val section = response.dataOrNull()

                            if (response is StoreResponse.Loading && section == null) {
                                _uiState.value = uiState.value.copy(
                                    isLoading = true
                                )
                            }

                            val sections = (section?.subsections
                                ?: emptyList<SectionEntity>()).sortedBy { it.name }
                            val documents = (section?.documents
                                ?: emptyList<DocumentEntity>()).sortedBy { it.name }
                            val resources = (section?.resources
                                ?: emptyList<ResourceEntity>()).sortedBy { it.name }

                            _uiState.value = uiState.value.copy(
                                listItems = (sections + documents + resources)
                            )

                            if (response is StoreResponse.Error || response is StoreResponse.NoNewData || response is StoreResponse.Data) {
                                _uiState.value = uiState.value.copy(
                                    isLoading = false
                                )

                                if (section == null) {
                                    // in theory this will navigate back until a section is found
                                    // or otherwise show the "empty" screen
                                    if (!navigateUp()) {
                                        // TODO
//                        showEmpty()
                                    }
                                }
                            }
                        }
                    } catch (ex: CancellationException) {
                        Timber.d { "sectionJob $sectionJob cancelled" }
                    }
                }
            }
        }
    }

    private suspend fun showError(errorMessage: String) {
        _uiState.value = uiState.value.copy(
            error = errorMessage
        )
        val errorEvent = FileBrowserEvent.Error(
            message = errorMessage
        )
        _events.send(errorEvent)
    }

    internal fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Refresh -> reload()
            is UiEvent.DocumentClicked -> onDocumentClicked(event.item)
            is UiEvent.ResourceClicked -> onResourceClicked(event.item)
            is UiEvent.SectionClicked -> onSectionClicked(event.item)
            is UiEvent.NavigateUpToSection -> navigateUp(event.section.id)
            is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                FAB_ID_CREATE_DOCUMENT -> onCreateDocumentFabClicked()
                FAB_ID_CREATE_SECTION -> onCreateSectionFabClicked()
                else -> TODO("Unhandled FAB: ${event.item}")
            }

            is UiEvent.CreateDocument -> {
                dismissCurrentDialog()
                createNewDocument(event.name)
            }

            is UiEvent.CreateSection -> {
                dismissCurrentDialog()
                createNewSection(event.parentSectionId, event.name)
            }

            is UiEvent.DismissDialog -> dismissCurrentDialog()
        }
    }

    private fun dismissCurrentDialog() {
        _uiState.update { old ->
            old.copy(currentDialogState = null)
        }
    }

    /**
     * Open a specific section
     *
     * @param sectionId the section to open
     * @param addToBackstack true, when the section should be added to backstack, false otherwise
     */
    internal fun openSection(
        sectionId: String,
        sectionName: String?,
        addToBackstack: Boolean = true,
    ) = launch {
        if (
            uiState.value.isSearching.not()
            && currentSectionId.value == sectionId
        ) {
            // ignore if no search is currently active and this section is already set
            return@launch
        }

        Timber.d { "Opening Section '${sectionId}'" }
        if (addToBackstack) {
            backstack.push(SectionBackstackItem(sectionId, sectionName))
        }

        // set the section id on the ViewModel
        currentSectionId.value = sectionId

        _uiState.value = uiState.value.copy(
            currentSectionPath = getCurrentSectionPathUseCase(backstack),
            canGoUp = (currentSectionId.value == ROOT_SECTION_ID || backstack.size <= 1).not()
        )
    }

    /**
     * Navigate up the section backstack
     *
     * @return true, when there was an item on the backstack and a navigation was done, false otherwise
     */
    fun navigateUp(targetSectionId: String? = null): Boolean {
        if (
            targetSectionId != null
            && (backstack.none { it.sectionId == targetSectionId } || backstack.peek().sectionId == targetSectionId)
        ) return false

        if (currentSectionId.value == ROOT_SECTION_ID || backstack.size <= 1) {
            return false
        }

        if (targetSectionId != null) {
            while (backstack.peek().sectionId != targetSectionId) {
                backstack.pop()
            }
        } else {
            backstack.pop()
        }
        openSection(backstack.peek().sectionId, backstack.peek().sectionName, false)
        return true
    }

    /**
     * Set the search string
     *
     * @return true if the value has changed, false otherwise
     */
    fun setSearch(text: String): Boolean {
        return if (uiState.value.currentSearchFilter != text) {
            _uiState.value = uiState.value.copy(
                currentSearchFilter = text
            )
            true
        } else false
    }

    private fun clearSearch() {
        setSearch("")
        if (uiState.value.isSearchExpanded) {
            _uiState.value = uiState.value.copy(
                isSearchExpanded = false
            )
        }
    }

    /**
     * Show the top level preferences page
     */
    private fun showTopLevel() {
        backstack.clear()
        currentSectionId.value = ROOT_SECTION_ID
        _uiState.value = uiState.value.copy(
            currentSectionPath = getCurrentSectionPathUseCase(backstack),
            canGoUp = false
        )
    }

    private fun createNewSection(parentSectionId: String, sectionName: String) =
        launch {
            try {
                // TODO: show loading state somehow
                createNewSectionUseCase(sectionName, parentSectionId)
            } catch (ex: Exception) {
                Timber.e(ex)
                showError(errorMessage = ex.localizedMessage ?: ex.javaClass.name)
            }
        }

    private fun createNewDocument(documentName: String) = launch {
        try {
            // TODO: show loading state somehow
            val newDocumentId = createNewDocumentUseCase(currentSectionId.value, documentName)

            reload()

            // and open the editor right away
            withContext(Dispatchers.Main) {
                openDocumentEditorEvent.value = newDocumentId
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            showError(errorMessage = ex.localizedMessage ?: ex.javaClass.name)
            return@launch
        }
    }

    /**
     * Rename a document
     */
    fun renameDocument(id: String, documentName: String) = launch {
        restClient.renameDocument(id, documentName)
        reload()
    }

    fun deleteDocument(id: String) = launch {
        restClient.deleteDocument(id)
        reload()
    }

    private fun reload() {
        _uiState.value = uiState.value.copy(
            error = "",
        )
        launch {
            try {
                refreshSectionUseCase(currentSectionId.value)
            } catch (ex: Exception) {
                Timber.e(ex)
                showError(errorMessage = ex.localizedMessage ?: ex.javaClass.name)
            }
        }
    }

    private fun onCreateSectionFabClicked() {
        val currentSectionId = currentSectionId.value

        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.CreateSection(
                    parentSectionId = currentSectionId,
                    initialSectionName = ""
                )
            )
        }
//        launch {
//            _events.send(FileBrowserEvent.CreateSectionEvent(currentSectionId))
//        }
    }

    private fun onCreateDocumentFabClicked() {
        val currentSectionId = currentSectionId.value

        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.CreateDocument(
                    sectionId = currentSectionId,
                    initialDocumentName = ""
                )
            )
        }
//        launch {
//            _events.send(FileBrowserEvent.CreateDocumentEvent(currentSectionId))
//        }
    }

    fun onDocumentLongClicked(entity: DocumentEntity): Boolean {
        launch {
            _events.send(FileBrowserEvent.RenameDocument(entity))
        }
        return true
    }

    private fun onDocumentClicked(entity: DocumentEntity) {
        launch {
            _events.send(FileBrowserEvent.OpenDocumentEditor(entity))
        }
    }

    private fun onResourceClicked(entity: ResourceEntity) {
        launch {
            _events.send(FileBrowserEvent.DownloadResource(entity))
        }
    }

    private fun onSectionClicked(entity: SectionEntity) {
        openSection(
            sectionId = entity.id,
            sectionName = entity.name,
            addToBackstack = true
        )
    }


    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID: String = "root"
    }
}

internal sealed class UiEvent {
    data object Refresh : UiEvent()

    data class DocumentClicked(val item: DocumentEntity) : UiEvent()
    data class ResourceClicked(val item: ResourceEntity) : UiEvent()
    data class SectionClicked(val item: SectionEntity) : UiEvent()

    data class NavigateUpToSection(val section: SectionItem) : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab) : UiEvent()

    data class CreateDocument(val sectionId: String, val name: String) : UiEvent()
    data class CreateSection(val parentSectionId: String, val name: String) : UiEvent()

    data object DismissDialog : UiEvent()
}

internal sealed class FileBrowserEvent {
    data class Error(val message: String) : FileBrowserEvent()

    data class OpenDocumentEditor(val entity: DocumentEntity) : FileBrowserEvent()
    data class DownloadResource(val entity: ResourceEntity) : FileBrowserEvent()
    data class CreateSection(val parentId: String) : FileBrowserEvent()
    data class CreateDocument(val parentId: String) : FileBrowserEvent()
    data class RenameDocument(val entity: DocumentEntity) : FileBrowserEvent()
}