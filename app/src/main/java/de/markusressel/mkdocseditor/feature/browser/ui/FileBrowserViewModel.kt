package de.markusressel.mkdocseditor.feature.browser.ui

import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.hadilq.liveevent.LiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import de.markusressel.mkdocseditor.feature.browser.ui.usecase.CreateNewSectionUseCase
import de.markusressel.mkdocseditor.feature.browser.ui.usecase.GetSectionContentUseCase
import de.markusressel.mkdocseditor.feature.browser.ui.usecase.ReloadFileTreeUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.ui.viewmodel.EntityListViewModel
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

internal sealed class UiEvent {
    object Refresh : UiEvent()

    data class DocumentClicked(val item: DocumentEntity) : UiEvent()
    data class ResourceClicked(val item: ResourceEntity) : UiEvent()
    data class SectionClicked(val item: SectionEntity) : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab) : UiEvent()
}

@HiltViewModel
internal class FileBrowserViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val restClient: MkDocsRestClient,
    private val reloadFileTreeUseCase: ReloadFileTreeUseCase,
    private val getSectionContentUseCase: GetSectionContentUseCase,
    private val createNewSectionUseCase: CreateNewSectionUseCase,
) : EntityListViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val backstack: Stack<SectionBackstackItem> = Stack()
        get() {
            if (field.size == 0) {
                // root is always the first element in the backstack
                field.add(SectionBackstackItem(ROOT_SECTION_ID))
            }
            return field
        }

    internal val currentSearchResults = combine(
        uiState.map { it.currentSearchFilter }.distinctUntilChanged(),
        uiState.map { it.isSearching }.distinctUntilChanged(),
    ) { currentSearchFilter, isSearching ->
        when {
            isSearching -> dataRepository.find(currentSearchFilter)
            else -> emptyList()
        }
    }

    private val currentSectionId = MutableStateFlow(ROOT_SECTION_ID)
    private val currentSection = getSectionContentUseCase(currentSectionId)

    internal val openDocumentEditorEvent = LiveEvent<String>()
    internal val events = LiveEvent<FileBrowserEvent>()

    init {
        viewModelScope.launch {
            uiState.map { it.isSearching }.collect {
                if (it.not()) {
                    showTopLevel()
                }
            }
        }

        viewModelScope.launch {
            currentSection.collectLatest { resource ->
                val section = resource.data

                if (resource is Resource.Error) {
                    // TODO: show error
                    events.value = FileBrowserEvent.ErrorEvent(resource.error?.message
                        ?: "Error fetching data")
                }

                if (resource is Resource.Loading && section == null) {
                    //showLoading()
                } else {
                    //showContent()
                }

                if (section != null) {
                    if (section.subsections.isEmpty() and section.documents.isEmpty() and section.resources.isEmpty()) {
//                        showEmpty()
                    } else {
//                        hideEmpty()
                    }
                } else {
                    // in theory this will navigate back until a section is found
                    // or otherwise show the "empty" screen
                    if (!navigateUp()) {
                        // TODO
//                        showEmpty()
                    }
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
            }
        }
    }

    internal fun onUiEvent(event: UiEvent) = when (event) {
        is UiEvent.Refresh -> reload()
        is UiEvent.DocumentClicked -> onDocumentClicked(event.item)
        is UiEvent.ResourceClicked -> onResourceClicked(event.item)
        is UiEvent.SectionClicked -> onSectionClicked(event.item)
        is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
            FAB_ID_CREATE_DOCUMENT -> onCreateDocumentFabClicked()
            FAB_ID_CREATE_SECTION -> onCreateSectionFabClicked()
            else -> TODO("Unhandled FAB: ${event.item}")
        }
    }

    /**
     * Removes all items in the backstack
     */
    internal fun clearBackstack() {
        backstack.clear()
    }

    /**
     * Open a specific section
     *
     * @param sectionId the section to open
     * @param addToBackstack true, when the section should be added to backstack, false otherwise
     */
    internal fun openSection(
        sectionId: String,
        addToBackstack: Boolean = true,
    ) = viewModelScope.launch {
        if (
            uiState.value.isSearching.not()
            && currentSectionId.value == sectionId
        ) {
            // ignore if no search is currently active and this section is already set
            return@launch
        }

        Timber.d { "Opening Section '${sectionId}'" }
        if (addToBackstack) {
            backstack.push(SectionBackstackItem(sectionId))
        }

        // set the section id on the ViewModel
        currentSectionId.value = sectionId
    }

    /**
     * Navigate up the section backstack
     *
     * @return true, when there was an item on the backstack and a navigation was done, false otherwise
     */
    fun navigateUp(): Boolean {
        if (currentSectionId.value == ROOT_SECTION_ID || backstack.size <= 1) {
            return false
        }

        backstack.pop()
        openSection(backstack.peek().sectionId, false)
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
    }

    fun createNewSection(sectionName: String) = viewModelScope.launch {
        createNewSectionUseCase(sectionName, currentSectionId.value)
    }

    fun createNewDocument(documentName: String) = viewModelScope.launch {
        val name = documentName.ifEmpty { "New Document" }
        val newDocumentId = dataRepository.createNewDocument(name, currentSectionId.value)

        reload()

        // and open the editor right away
        withContext(Dispatchers.Main) {
            openDocumentEditorEvent.value = newDocumentId
        }
    }

    /**
     * Rename a document
     */
    fun renameDocument(id: String, documentName: String) = viewModelScope.launch {
        restClient.renameDocument(id, documentName)
        reload()
    }

    fun deleteDocument(id: String) = viewModelScope.launch {
        restClient.deleteDocument(id)
        reload()
    }

    private fun reload() {
        currentSectionId.value = currentSectionId.value
    }

    private fun onCreateSectionFabClicked() {
        val currentSectionId = currentSectionId.value
        events.value = FileBrowserEvent.CreateSectionEvent(currentSectionId)
    }

    private fun onCreateDocumentFabClicked() {
        val currentSectionId = currentSectionId.value
        events.value = FileBrowserEvent.CreateDocumentEvent(currentSectionId)
    }

    fun onDocumentLongClicked(entity: DocumentEntity): Boolean {
        events.value = FileBrowserEvent.RenameDocumentEvent(entity)
        return true
    }

    private fun onDocumentClicked(entity: DocumentEntity) {
        events.value = FileBrowserEvent.OpenDocumentEditorEvent(entity)
    }

    private fun onResourceClicked(entity: ResourceEntity) {
        events.value = FileBrowserEvent.DownloadResourceEvent(entity)
    }

    private fun onSectionClicked(entity: SectionEntity) {
        openSection(
            sectionId = entity.id,
            addToBackstack = true
        )
    }


    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID = "root"
    }
}