package de.markusressel.mkdocseditor.feature.filebrowser.ui

import androidx.core.text.trimmedLength
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eightbitlab.rxbus.Bus
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.extensions.common.delayUntil
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ROOT_SECTION
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionBackstackItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ComputePathToSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.CreateNewDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.CreateNewSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindParentSectionOfDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindParentSectionOfResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.GetCurrentSectionPathUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.GetSectionItemsUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.RenameDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.RenameResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.RenameSectionUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.UploadResourceUseCase
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.SearchUseCase
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    private val getSectionItemsUseCase: GetSectionItemsUseCase,
    private val createNewSectionUseCase: CreateNewSectionUseCase,
    private val searchUseCase: SearchUseCase,
    private val computePathToSectionUseCase: ComputePathToSectionUseCase,
    private val getCurrentSectionPathUseCase: GetCurrentSectionPathUseCase,
    private val createNewDocumentUseCase: CreateNewDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val renameSectionUseCase: RenameSectionUseCase,
    private val renameResourceUseCase: RenameResourceUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val deleteSectionUseCase: DeleteSectionUseCase,
    private val applyCurrentBackendConfigUseCase: ApplyCurrentBackendConfigUseCase,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val uploadResourceUseCase: UploadResourceUseCase,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val findSectionUseCase: FindSectionUseCase,
    private val findParentSectionOfDocumentUseCase: FindParentSectionOfDocumentUseCase,
    private val findParentSectionOfResourceUseCase: FindParentSectionOfResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
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

    private val currentSearchResults: Flow<List<SearchResultItem>> = combine(
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
        Bus.observe<BusEvent.CodeEditorBusEvent>().subscribe { event ->
            launch {
                when (event) {
                    is BusEvent.CodeEditorBusEvent.GoToDocument -> {
                        val parentSectionId = findParentSectionOfDocumentUseCase(event.documentId)
                        parentSectionId?.let {
                            delay(200)
                            openSection(parentSectionId.id)
                            delayUntil { uiState.value.isLoading.not() }
                            _events.send(FileBrowserEvent.OpenDocumentEditor(event.documentId))
                        }
                    }

                    is BusEvent.CodeEditorBusEvent.GoToResource -> {
                        val parentSection = findParentSectionOfResourceUseCase(event.resourceId)
                        parentSection?.let {
                            delay(200)
                            openSection(parentSection.id)
                            parentSection.resources.firstOrNull { it.id == event.resourceId }?.let {
                                delayUntil { uiState.value.isLoading.not() }
                                onResourceClicked(it)
                            }
                        }
                    }

                    is BusEvent.CodeEditorBusEvent.GoToSection -> {
                        delay(200)
                        findSectionUseCase(event.sectionId).let {
                            openSection(it.id)
                        }
                    }
                }
            }
        }

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
                is UiEvent.DismissError -> clearError()
                is UiEvent.Refresh -> reload()
                is UiEvent.DocumentClicked -> onDocumentClicked(event.item)
                is UiEvent.DocumentLongClicked -> onDocumentLongClicked(event.item)
                is UiEvent.ResourceClicked -> onResourceClicked(event.item)
                is UiEvent.ResourceLongClicked -> onResourceLongClicked(event.item)
                is UiEvent.SectionClicked -> onSectionClicked(event.item)
                is UiEvent.SectionLongClicked -> onSectionLongClicked(event.item)
                is UiEvent.NavigateUpToSection -> navigateUp(event.section.id)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    FileBrowserFabId.CreateDocument -> onCreateDocumentFabClicked()
                    FileBrowserFabId.CreateSection -> onCreateSectionFabClicked()
                    FileBrowserFabId.UploadResource -> onUploadResourceFabClicked()
                    FileBrowserFabId.FAB -> {
                        // handled internally
                    }
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
                }

                is UiEvent.EditSectionDialogSaveClicked -> {
                    dismissCurrentDialog()
                    renameSection(event.sectionId, event.name)
                }

                is UiEvent.EditSectionDialogDeleteClicked -> {
                    dismissCurrentDialog()
                    showDeleteSectionConfirmationDialog(event.sectionId)
                }

                is UiEvent.EditResourceDialogSaveClicked -> {
                    dismissCurrentDialog()
                    renameResource(event.resourceId, event.name)
                }

                is UiEvent.EditResourceDialogDeleteClicked -> {
                    dismissCurrentDialog()
                    showDeleteResourceConfirmationDialog(event.resourceId)
                }

                is UiEvent.DeleteSectionDialogConfirmClicked -> {
                    dismissCurrentDialog()
                    deleteSection(event.sectionId)
                }

                is UiEvent.DeleteResourceDialogConfirmClicked -> {
                    dismissCurrentDialog()
                    deleteResource(event.resourceId)
                }

                is UiEvent.DismissDialog -> dismissCurrentDialog()

                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)

                is UiEvent.SearchExpandedChanged -> onSearchExpandedChanged(event.isExpanded)
            }
        }
    }

    private suspend fun renameResource(resourceId: String, name: String) {
        if (isDocumentNameValid(name)) {
            try {
                showLoading(true)
                renameResourceUseCase(resourceId, name)
                reload()
            } catch (ex: Exception) {
                Timber.e(ex)
                setError(message = ex.localizedMessage ?: ex.javaClass.name)
            }
        }
    }


    private fun showDeleteDocumentConfirmationDialog(documentId: String) {
        showDialog(
            DialogState.DeleteDocumentConfirmation(
                documentId = documentId,
            )
        )
    }

    private fun showDeleteSectionConfirmationDialog(sectionId: String) {
        showDialog(
            DialogState.DeleteSectionConfirmation(
                sectionId = sectionId,
            )
        )
    }

    private fun showDeleteResourceConfirmationDialog(resourceId: String) {
        showDialog(
            DialogState.DeleteResourceConfirmation(
                resourceId = resourceId,
            )
        )
    }

    private fun setFabConfig(fabConfig: FabConfig<FileBrowserFabId>) {
        _uiState.update { old ->
            old.copy(fabConfig = fabConfig)
        }
    }

    private fun clearError() = setError(null)

    private fun setError(message: String?) {
        _uiState.update { old ->
            old.copy(
                isLoading = false,
                error = message
            )
        }
    }

    private suspend fun onSearchExpandedChanged(expanded: Boolean) {
        if (expanded) {
            _events.send(FileBrowserEvent.OpenSearch)
        }
        _uiState.update { old ->
            old.copy(isSearchExpanded = expanded)
        }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.FileBrowser) {
        when (action) {
            is TopAppBarAction.FileBrowser.Search -> {
                onSearchExpandedChanged(true)
            }

            is TopAppBarAction.FileBrowser.Profile -> {
                _events.send(FileBrowserEvent.OpenProfileScreen)
            }
        }
    }

    private suspend fun onSectionLongClicked(item: SectionData) {
        showDialog(
            DialogState.EditSection(
                sectionId = item.id,
                initialSectionName = item.name
            )
        )
    }

    private suspend fun onResourceLongClicked(item: ResourceData) {
        showDialog(
            DialogState.EditResource(
                resourceId = item.id,
                initialResourceName = item.name
            )
        )
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

    private fun showDialog(dialogState: DialogState?) {
        _uiState.update { old ->
            old.copy(currentDialogState = dialogState)
        }
    }

    private fun dismissCurrentDialog() = showDialog(null)

    /**
     * Open a specific section
     *
     * @param sectionId the section to open
     * @param addToBackstack true, when the section should be added to backstack, false otherwise
     */
    internal suspend fun openSection(
        sectionId: String,
        addToBackstack: Boolean = true,
    ) {
        Timber.d { "Opening Section '${sectionId}'" }

        // determine the path to the given section and create a backstack for it
        computePathToSectionUseCase(sectionId).let { path ->
            if (addToBackstack) {
                backstack.clear()
                path.filter {
                    it.id != ROOT_SECTION_ID
                }.map {
                    SectionBackstackItem(it.id, it.name)
                }.let {
                    backstack.addAll(it)
                }
            }
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
    suspend fun navigateUp(targetSectionId: String? = null): Boolean {
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
        openSection(backstack.peek().sectionId, false)
        return true
    }

    /**
     * Show the top level preferences page
     */
    private suspend fun showTopLevel() {
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
            reload()
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun renameSection(sectionId: String, name: String) {
        if (isSectionNameValid(name)) {
            try {
                showLoading(true)
                renameSectionUseCase(sectionId, name)
                reload()
            } catch (ex: Exception) {
                Timber.e(ex)
                setError(message = ex.localizedMessage ?: ex.javaClass.name)
            }
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
            reload()
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun deleteResource(resourceId: String) {
        try {
            deleteResourceUseCase(resourceId)
            reload()
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun deleteSection(sectionId: String) {
        try {
            deleteSectionUseCase(sectionId)
            reload()
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun reload() {
        clearError()
        startSectionDataJob(currentSectionId.value)
    }

    private fun onCreateSectionFabClicked() {
        val currentSectionId = currentSectionId.value

        showDialog(
            DialogState.CreateSection(
                parentSectionId = currentSectionId,
                initialSectionName = ""
            )
        )
    }

    private fun onCreateDocumentFabClicked() {
        val currentSectionId = currentSectionId.value

        showDialog(
            DialogState.CreateDocument(
                sectionId = currentSectionId,
                initialDocumentName = ""
            )
        )
    }

    private suspend fun onUploadResourceFabClicked() {
        _events.send(FileBrowserEvent.OpenResourceSelection)
        val obs = Bus.observe<BusEvent.FilePickerResult>()
        obs.subscribe { event ->
            val uri = event.uri
            if (uri != null) {
                launch {
                    try {
                        uploadResourceUseCase(currentSectionId.value, uri)
                        reload()
                    } catch (ex: Exception) {
                        Timber.e(ex)
                        setError(message = ex.localizedMessage ?: ex.javaClass.name)
                    }
                }
            }
            Bus.unregister(obs)
        }
    }

    private fun onDocumentLongClicked(entity: DocumentData) {
        showDialog(
            DialogState.EditDocument(
                documentId = entity.id,
                initialDocumentName = entity.name
            )
        )
    }

    private suspend fun onDocumentClicked(entity: DocumentData) {
        if (isOfflineModeEnabledFlowUseCase().value && entity.isOfflineAvailable.not()) {
            setError("Document is not available offline")
            return
        }
        _events.send(FileBrowserEvent.OpenDocumentEditor(entity.id))
    }

    private suspend fun onResourceClicked(entity: ResourceData) {
        try {
            val result = try {
                _events.send(FileBrowserEvent.Toast("Downloading resource..."))
                downloadResourceUseCase.invoke(entity.id, entity.name)
            } catch (ex: Exception) {
                Timber.e(ex)
                setError(message = "Download failed: ${ex.localizedMessage ?: ex.javaClass.name}")
                return
            }

            shareFileUseCase(result)
        } catch (ex: Exception) {
            Timber.e(ex)
            setError(message = ex.localizedMessage ?: ex.javaClass.name)
        }
    }

    private suspend fun onSectionClicked(entity: SectionData) {
        openSection(
            sectionId = entity.id,
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
    data object DismissError : UiEvent()
    data object Refresh : UiEvent()

    data class SearchExpandedChanged(val isExpanded: Boolean) : UiEvent()

    data class DocumentClicked(val item: DocumentData) : UiEvent()
    data class DocumentLongClicked(val item: DocumentData) : UiEvent()
    data class ResourceClicked(val item: ResourceData) : UiEvent()
    data class ResourceLongClicked(val item: ResourceData) : UiEvent()
    data class SectionClicked(val item: SectionData) : UiEvent()
    data class SectionLongClicked(val item: SectionData) : UiEvent()

    data class NavigateUpToSection(val section: SectionItem) : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab<FileBrowserFabId>) : UiEvent()

    data class CreateDocumentDialogSaveClicked(val sectionId: String, val name: String) : UiEvent()

    data class EditDocumentDialogDeleteClicked(val documentId: String) : UiEvent()
    data class DeleteDocumentDialogConfirmClicked(val documentId: String) : UiEvent()

    data class EditDocumentDialogSaveClicked(val documentId: String, val name: String) : UiEvent()
    data class CreateSectionDialogSaveClicked(val parentSectionId: String, val name: String) :
        UiEvent()

    data class EditSectionDialogDeleteClicked(val sectionId: String) : UiEvent()
    data class DeleteSectionDialogConfirmClicked(val sectionId: String) : UiEvent()

    data class EditSectionDialogSaveClicked(val sectionId: String, val name: String) : UiEvent()


    data class EditResourceDialogDeleteClicked(val resourceId: String) : UiEvent()
    data class EditResourceDialogSaveClicked(val resourceId: String, val name: String) : UiEvent()
    data class DeleteResourceDialogConfirmClicked(val resourceId: String) : UiEvent()


    data class TopAppBarActionClicked(val action: TopAppBarAction.FileBrowser) : UiEvent()

    data object DismissDialog : UiEvent()
}

internal sealed class FileBrowserEvent {
    data object OpenSearch : FileBrowserEvent()
    data class OpenDocumentEditor(val documentId: String) : FileBrowserEvent()
    data class Toast(val message: String) : FileBrowserEvent()

    data object OpenProfileScreen : FileBrowserEvent()
    data object OpenResourceSelection : FileBrowserEvent()
}