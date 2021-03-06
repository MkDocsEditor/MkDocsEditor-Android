package de.markusressel.mkdocseditor.feature.browser

import androidx.lifecycle.*
import androidx.lifecycle.Transformations.switchMap
import com.github.ajalt.timberkt.Timber
import com.hadilq.liveevent.LiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.DataRepository
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.feature.browser.FileBrowserViewModel.Event.*
import de.markusressel.mkdocseditor.ui.viewmodel.EntityListViewModel
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val restClient: MkDocsRestClient,
) : EntityListViewModel() {

    private val backstack: Stack<SectionBackstackItem> = Stack()
        get() {
            if (field.size == 0) {
                // root is always the first element in the backstack
                field.add(SectionBackstackItem(ROOT_SECTION_ID))
            }
            return field
        }

    val isSearchExpanded = MutableLiveData(false)

    // TODO: this should be a combination of searchString and all available IdentifiableListItems
    val currentSearchResults = MutableLiveData<List<IdentifiableListItem>>()

    private val currentSectionId = MutableLiveData(ROOT_SECTION_ID)

    val currentSection: LiveData<Resource<SectionEntity?>> =
        switchMap(currentSectionId) { sectionId ->
            dataRepository.getSection(sectionId).asLiveData()
        }

    val openDocumentEditorEvent = LiveEvent<String>()
    val events = LiveEvent<Event>()

    init {
        // TODO: baaaad
        currentSearchFilter.observeForever { searchString ->
            if (isSearching()) {
                doAsync {
                    val data = dataRepository.find(searchString)
                    runOnUiThread {
                        currentSearchResults.value = data
                    }
                }
            } else {
                showTopLevel()
            }
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
    internal fun openSection(sectionId: String, addToBackstack: Boolean = true) {
        if (!isSearching() && currentSectionId.value == sectionId) {
            // ignore if no search is currently active and this section is already set
            return
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
     * @return true if a search is currently open, false otherwise
     */
    private fun isSearching(): Boolean {
        return currentSearchFilter.value?.isNotBlank() ?: false
    }

    /**
     * Set the search string
     *
     * @return true if the value has changed, false otherwise
     */
    fun setSearch(text: String): Boolean {
        return if (currentSearchFilter.value != text) {
            currentSearchFilter.value = text
            true
        } else false
    }

    private fun clearSearch() {
        setSearch("")
        if (isSearchExpanded.value != false) {
            isSearchExpanded.value = false
        }
    }

    /**
     * Show the top level preferences page
     */
    private fun showTopLevel() {
        currentSectionId.value = ROOT_SECTION_ID
        openSection(ROOT_SECTION_ID)
    }

    fun createNewSection(sectionName: String) {
        viewModelScope.launch {
            dataRepository.createNewSection(sectionName, currentSectionId.value!!)
        }
    }

    fun createNewDocument(documentName: String) {
        viewModelScope.launch {
            val name = if (documentName.isEmpty()) "New Document" else documentName
            val newDocumentId = dataRepository.createNewDocument(name, currentSectionId.value!!)

            reload()

            // and open the editor right away
            withContext(Dispatchers.Main) {
                openDocumentEditorEvent.value = newDocumentId
            }
        }
    }

    /**
     * Rename a document
     */
    fun renameDocument(id: String, documentName: String) {
        viewModelScope.launch {
            restClient.renameDocument(id, documentName)
            reload()
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            restClient.deleteDocument(id)
            reload()
        }
    }

    private fun reload() {
        currentSectionId.value = currentSectionId.value
    }

    fun onCreateSectionFabClicked() {
        val currentSectionId = currentSectionId.value!!
        events.value = CreateSectionEvent(currentSectionId)
    }

    fun onCreateDocumentFabClicked() {
        val currentSectionId = currentSectionId.value!!
        events.value = CreateDocumentEvent(currentSectionId)
    }

    fun onDocumentLongClicked(entity: DocumentEntity): Boolean {
        events.value = RenameDocumentEvent(entity)
        return true
    }

    sealed class Event {
        object ReloadEvent : Event()
        data class CreateSectionEvent(val parentId: String) : Event()
        data class CreateDocumentEvent(val parentId: String) : Event()
        data class RenameDocumentEvent(val entity: DocumentEntity) : Event()
    }

    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID = "root"
    }
}