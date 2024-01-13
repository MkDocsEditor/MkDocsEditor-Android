package de.markusressel.mkdocseditor.feature.browser.ui

import androidx.core.text.trimmedLength
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.browser.data.ROOT_SECTION
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.CreateNewDocumentUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.CreateNewSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.DeleteDocumentUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.DeleteResourceUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.DeleteSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.GetCurrentSectionPathUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.GetSectionItemsUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.RenameDocumentUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.RenameSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.SearchUseCase
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.SectionItem
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.CancellationException
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
import org.mobilenativefoundation.store.store5.StoreReadResponse
import java.util.Stack
import javax.inject.Inject


@HiltViewModel
internal class FileBrowserViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val restClient: IMkDocsRestClient,
    private val getSectionItemsUseCase: GetSectionItemsUseCase,
    private val createNewSectionUseCase: CreateNewSectionUseCase,
    private val searchUseCase: SearchUseCase,
    private val getCurrentSectionPathUseCase: GetCurrentSectionPathUseCase,
    private val createNewDocumentUseCase: CreateNewDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val renameSectionUseCase: RenameSectionUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val deleteSectionUseCase: DeleteSectionUseCase,
    private val applyCurrentBackendConfigUseCase: ApplyCurrentBackendConfigUseCase,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
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

    private val currentSearchResults = combine(
        uiState.map { it.currentSearchFilter }.distinctUntilChanged(),
        uiState.map { it.isSearchExpanded }.distinctUntilChanged(),
    ) { currentSearchFilter, isSearching ->
        when {
            isSearching && currentSearchFilter.trimmedLength() > 0 -> searchUseCase(
                currentSearchFilter
            )

            else -> emptyList()
        }
    }

    private val currentSectionId = MutableStateFlow(ROOT_SECTION_ID)

    private var sectionJob: Job? = null

    init {
        launch {
            try {
                applyCurrentBackendConfigUseCase()
            } catch (ex: Exception) {
                Timber.e(ex)
                setError(message = ex.localizedMessage ?: ex.javaClass.name)
            }
        }

        launch {
            uiState.map { it.isSearchExpanded }.distinctUntilChanged().collect { isSearching ->
                if (isSearching.not()) {
                    showTopLevel()
                }
            }
        }

        launch {
            currentSearchResults.collectLatest { results ->
                _uiState.update { old ->
                    old.copy(currentSearchResults = results)
                }
            }
        }

        launch {
            currentSectionId.collectLatest { sectionId ->
                startSectionDataJob(sectionId)
            }
        }
    }

    private suspend fun startSectionDataJob(sectionId: String) {
        sectionJob?.cancel()
        sectionJob = launch {
            try {
                getSectionItemsUseCase(sectionId).collect { response ->
                    showLoading(response is StoreReadResponse.Loading)
                    when (response) {
                        is StoreReadResponse.NoNewData,
                        is StoreReadResponse.Data -> {
                            val section = response.dataOrNull()

                            val sections =
                                (section?.subsections
                                    ?: emptyList()).sortedBy { it.name.lowercase() }
                            val documents =
                                (section?.documents ?: emptyList()).sortedBy { it.name.lowercase() }
                            val resources =
                                (section?.resources ?: emptyList()).sortedBy { it.name.lowercase() }

                            _uiState.update { old ->
                                old.copy(listItems = (sections + documents + resources))
                            }
                            if (section == null) {
                                setFabConfig(FabConfig())
                            } else {
                                setFabConfig(CreateItemsFabConfig)
                            }
                        }

                        is StoreReadResponse.Error.Exception -> {
                            Timber.e(response.error)
                            setError(
                                message = response.error.localizedMessage
                                    ?: response.error.javaClass.name
                            )
                            setFabConfig(FabConfig())
                        }

                        is StoreReadResponse.Error.Message -> {
                            setError(message = response.message)
                            setFabConfig(FabConfig())
                        }

                        else -> {
                            // handled above
                        }
                    }
                }
            } catch (ex: CancellationException) {
                Timber.d { "sectionJob $sectionJob cancelled" }
            }
        }
    }

    internal fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.Refresh -> reload()
                is UiEvent.DocumentClicked -> onDocumentClicked(event.item)
                is UiEvent.DocumentLongClicked -> onDocumentLongClicked(event.item)
                is UiEvent.ResourceClicked -> onResourceClicked(event.item)
                is UiEvent.ResourceLongClicked -> onResourceLongClicked(event.item)
                is UiEvent.SectionClicked -> onSectionClicked(event.item)
                is UiEvent.SectionLongClicked -> onSectionLongClicked(event.item)
                is UiEvent.NavigateUpToSection -> navigateUp(event.section.id)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    FAB_ID_CREATE_DOCUMENT -> onCreateDocumentFabClicked()
                    FAB_ID_CREATE_SECTION -> onCreateSectionFabClicked()
                    else -> TODO("Unhandled FAB: ${event.item}")
                }

                is UiEvent.CreateDocumentDialogSaveClicked -> {
                    dismissCurrentDialog()
                    if (isDocumentNameValid(event.name)) {
                        createNewDocument(event.name)
                    } else {
                        setError("Invalid document name")
                    }
                }

                is UiEvent.EditDocumentDialogDeleteClicked -> {
                    dismissCurrentDialog()
                    showDeleteDocumentConfirmationDialog(event.documentId)
                }

                is UiEvent.DeleteDocumentDialogConfirmClicked -> {
                    dismissCurrentDialog()
                    deleteDocument(event.documentId)
                }

                is UiEvent.EditDocumentDialogSaveClicked -> {
                    dismissCurrentDialog()
                    if (isDocumentNameValid(event.name)) {
                        renameDocumentUseCase(event.documentId, event.name)
                        reload()
                    } else {
                        setError("Invalid document name")
                    }
                }

                is UiEvent.CreateSectionDialogSaveClicked -> {
                    dismissCurrentDialog()
                    createNewSection(event.parentSectionId, event.name)
                    reload()
                }

                is UiEvent.EditSectionDialogSaveClicked -> {
                    dismissCurrentDialog()
                    if (isSectionNameValid(event.name)) {
                        renameSectionUseCase(event.sectionId, event.name)
                        reload()
                    }
                }

                is UiEvent.EditSectionDialogDeleteClicked -> {
                    dismissCurrentDialog()
                    showDeleteSectionConfirmationDialog(event.sectionId)
                }

                is UiEvent.DeleteSectionDialogConfirmClicked -> {
                    dismissCurrentDialog()
                    deleteSection(event.sectionId)
                }

                is UiEvent.DismissDialog -> dismissCurrentDialog()

                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)

                is UiEvent.SearchExpandedChanged -> onSearchExpandedChanged(event.isExpanded)
                is UiEvent.SearchInputChanged -> onSearchInputChanged(event.text)
                is UiEvent.SearchRequested -> onSearchRequested(event.query)
                is UiEvent.SearchResultClicked -> onSearchResultClicked(event.item)
            }
        }
    }

    private fun showDeleteDocumentConfirmationDialog(documentId: String) {
        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.DeleteDocumentConfirmation(
                    documentId = documentId,
                )
            )
        }
    }

    private fun showDeleteSectionConfirmationDialog(sectionId: String) {
        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.DeleteSectionConfirmation(
                    sectionId = sectionId,
                )
            )
        }
    }

    private fun setFabConfig(fabConfig: FabConfig) {
        _uiState.update { old ->
            old.copy(fabConfig = fabConfig)
        }
    }

    private fun setError(message: String?) {
        _uiState.update { old ->
            old.copy(error = message)
        }
    }

    private fun onSearchExpandedChanged(expanded: Boolean) {
        _uiState.update { old ->
            old.copy(isSearchExpanded = expanded)
        }
    }

    private fun onSearchInputChanged(text: String) {
        setSearch(text)
    }

    private fun onSearchRequested(query: String) {
        setSearch(query)
        _uiState.update { old ->
            old.copy(isSearchExpanded = true)
        }
    }

    private suspend fun onSearchResultClicked(item: Any) {
        when (item) {
            is DocumentData -> {
                clearSearch()
                onDocumentClicked(item)
            }

            is ResourceData -> onResourceClicked(item)
            is SectionData -> onSectionClicked(item)
        }
    }

    private fun onTopAppBarActionClicked(action: TopAppBarAction.FileBrowser) {
        when (action) {
            TopAppBarAction.FileBrowser.Search -> {
                _uiState.update { old ->
                    old.copy(isSearchExpanded = true)
                }
            }
        }
    }

    private suspend fun onSectionLongClicked(item: SectionData) {
        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.EditSection(
                    sectionId = item.id,
                    initialSectionName = item.name
                )
            )
        }
    }

    private suspend fun onResourceLongClicked(item: ResourceData) {

    }

    private fun isDocumentNameValid(name: String): Boolean {
        val equallyNamedSectionsExist =
            uiState.value.listItems.filterByExpectedType<SectionData>().any {
                it.name == name
            }
        val equallyNamedDocumentsExist =
            uiState.value.listItems.filterByExpectedType<DocumentData>().any {
                it.name == name
            }
        val equallyNamedResourcesExist =
            uiState.value.listItems.filterByExpectedType<ResourceData>().any {
                it.name == name
            }
        return name.isNotBlank()
            && equallyNamedSectionsExist.not()
            && equallyNamedDocumentsExist.not()
            && equallyNamedResourcesExist.not()
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
    ) {
        if (
            uiState.value.isSearchExpanded.not()
            && currentSectionId.value == sectionId
        ) {
            // ignore if no search is currently active and this section is already set
            return
        }

        Timber.d { "Opening Section '${sectionId}'" }
        if (addToBackstack) {
            backstack.push(SectionBackstackItem(sectionId, sectionName))
        }

        // set the section id on the ViewModel
        currentSectionId.value = sectionId

        _uiState.update { old ->
            old.copy(
                currentSectionPath = getCurrentSectionPathUseCase(backstack),
                canGoUp = (currentSectionId.value == ROOT_SECTION_ID || backstack.size <= 1).not()
            )
        }
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
    private fun setSearch(text: String): Boolean {
        return if (uiState.value.currentSearchFilter != text) {
            _uiState.update { old ->
                old.copy(
                    currentSearchFilter = text,
                )
            }
            true
        } else false
    }

    private fun clearSearch() {
        setSearch("")
        if (uiState.value.isSearchExpanded) {
            _uiState.update { old ->
                old.copy(
                    isSearchExpanded = false,
                )
            }
        }
    }

    /**
     * Show the top level preferences page
     */
    private fun showTopLevel() {
        backstack.clear()
        currentSectionId.value = ROOT_SECTION_ID
        _uiState.update { old ->
            old.copy(
                currentSectionPath = getCurrentSectionPathUseCase(backstack),
                canGoUp = false
            )
        }
    }

    private suspend fun createNewSection(parentSectionId: String, sectionName: String) {
        try {
            val trimmedSectionName = sectionName.trim()
            if (isSectionNameValid(trimmedSectionName).not()) {
                setError("Invalid section name")
                return
            }

            // TODO: show loading state somehow
            showLoading(true)

            createNewSectionUseCase(trimmedSectionName, parentSectionId)
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        } finally {
            showLoading(false)
        }
    }

    private fun isSectionNameValid(sectionName: String): Boolean {
        val equallyNamedSectionsExist =
            uiState.value.listItems.filterByExpectedType<SectionEntity>().any {
                it.name == sectionName
            }
        val equallyNamedDocumentsExist =
            uiState.value.listItems.filterByExpectedType<DocumentEntity>().any {
                it.name == sectionName
            }
        val equallyNamedResourcesExist =
            uiState.value.listItems.filterByExpectedType<ResourceEntity>().any {
                it.name == sectionName
            }
        return sectionName.isNotBlank()
            && equallyNamedSectionsExist.not()
            && equallyNamedDocumentsExist.not()
            && equallyNamedResourcesExist.not()
    }

    private suspend fun createNewDocument(documentName: String) {
        try {
            // TODO: show loading state somehow
            val trimmedDocumentName = documentName.trim()
            val newDocumentId =
                createNewDocumentUseCase(currentSectionId.value, trimmedDocumentName)

            reload()

            // and open the editor right away
            _events.send(FileBrowserEvent.OpenDocumentEditor(newDocumentId))
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun deleteDocument(id: String) {
        try {
            deleteDocumentUseCase(id)
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        } finally {
            reload()
        }
    }

    private suspend fun deleteResource(resourceId: String) {
        try {
            deleteResourceUseCase(resourceId)
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        } finally {
            reload()
        }
    }

    private suspend fun deleteSection(sectionId: String) {
        try {
            deleteSectionUseCase(sectionId)
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        } finally {
            reload()
        }
    }

    private suspend fun reload() {
        setError(null)
        startSectionDataJob(currentSectionId.value)
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
    }

    private fun onDocumentLongClicked(entity: DocumentData) {
        _uiState.update { old ->
            old.copy(
                currentDialogState = DialogState.EditDocument(
                    documentId = entity.id,
                    initialDocumentName = entity.name
                )
            )
        }
    }

    private suspend fun onDocumentClicked(entity: DocumentData) {
        if (isOfflineModeEnabledFlowUseCase().value && entity.isOfflineAvailable.not()) {
            setError("Document is not available offline")
            return
        }
        _events.send(FileBrowserEvent.OpenDocumentEditor(entity.id))
    }

    private suspend fun onResourceClicked(entity: ResourceData) {
        setError("Not yet supported")
    }

    private fun onSectionClicked(entity: SectionData) {
        openSection(
            sectionId = entity.id,
            sectionName = entity.name,
            addToBackstack = true
        )
    }

    private fun showLoading(isLoading: Boolean) {
        _uiState.update { old ->
            old.copy(isLoading = isLoading)
        }
    }


    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID: String = "root"
    }
}

internal sealed class UiEvent {
    data object Refresh : UiEvent()

    data class SearchRequested(val query: String) : UiEvent()
    data class SearchInputChanged(val text: String) : UiEvent()
    data class SearchExpandedChanged(val isExpanded: Boolean) : UiEvent()
    data class SearchResultClicked(val item: Any) : UiEvent()

    data class DocumentClicked(val item: DocumentData) : UiEvent()
    data class DocumentLongClicked(val item: DocumentData) : UiEvent()
    data class ResourceClicked(val item: ResourceData) : UiEvent()
    data class ResourceLongClicked(val item: ResourceData) : UiEvent()
    data class SectionClicked(val item: SectionData) : UiEvent()
    data class SectionLongClicked(val item: SectionData) : UiEvent()

    data class NavigateUpToSection(val section: SectionItem) : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab) : UiEvent()

    data class CreateDocumentDialogSaveClicked(val sectionId: String, val name: String) : UiEvent()

    data class EditDocumentDialogDeleteClicked(val documentId: String) : UiEvent()
    data class DeleteDocumentDialogConfirmClicked(val documentId: String) : UiEvent()

    data class EditDocumentDialogSaveClicked(val documentId: String, val name: String) : UiEvent()
    data class CreateSectionDialogSaveClicked(val parentSectionId: String, val name: String) :
        UiEvent()

    data class EditSectionDialogDeleteClicked(val sectionId: String) : UiEvent()
    data class DeleteSectionDialogConfirmClicked(val sectionId: String) : UiEvent()

    data class EditSectionDialogSaveClicked(val sectionId: String, val name: String) : UiEvent()
    data class TopAppBarActionClicked(val action: TopAppBarAction.FileBrowser) : UiEvent()

    data object DismissDialog : UiEvent()
}

internal sealed class FileBrowserEvent {
    data class OpenDocumentEditor(val documentId: String) : FileBrowserEvent()
}