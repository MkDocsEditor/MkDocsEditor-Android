package de.markusressel.mkdocseditor.view.viewmodel

import androidx.annotation.MainThread
import androidx.arch.core.util.Function
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.github.ajalt.timberkt.Timber
import com.hadilq.liveevent.LiveEvent
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.persistence.*
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.view.fragment.SectionBackstackItem
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FileBrowserViewModel @ViewModelInject constructor(
        val restClient: MkDocsRestClient,
        val sectionPersistenceManager: SectionPersistenceManager,
        val documentPersistenceManager: DocumentPersistenceManager,
        val documentContentPersistenceManager: DocumentContentPersistenceManager,
        private val resourcePersistenceManager: ResourcePersistenceManager,
        @Assisted private val savedStateHandle: SavedStateHandle
) : EntityListViewModel() {

    private val backstack: Stack<SectionBackstackItem> = Stack()
        get() {
            if (field.size == 0) {
                // root is always the first element in the backstack
                field.add(SectionBackstackItem(ROOT_SECTION_ID))
            }
            return field
        }

    val isSearchExpanded = MutableLiveData<Boolean>(false)
    val currentSearchFilter = MutableLiveData<String>()

    val currentSearchResults = MutableLiveData<List<IdentifiableListItem>>()

    val currentSectionId = MutableLiveData<String>(ROOT_SECTION_ID)
    val currentSection = switchMapPaged<String, SectionEntity>(
            currentSectionId,
            Function { sectionId ->
                getSectionLiveData(sectionId)
            }
    )

    val openDocumentEditorEvent = LiveEvent<String>()
    val reloadEvent = LiveEvent<Boolean>()

    init {
        currentSearchFilter.observeForever { searchString ->
            if (isSearching()) {
                // TODO:  this is pretty ugly and time/performance consuming
                doAsync {
                    val searchRegex = searchString.toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.LITERAL))

                    val sections = sectionPersistenceManager.standardOperation().query {
                        filter { section -> searchRegex.containsMatchIn(section.name) }
                    }.find()

                    val documents = documentPersistenceManager.standardOperation().query {
                        filter { document -> searchRegex.containsMatchIn(document.name) }
                    }.find()

                    val resources = resourcePersistenceManager.standardOperation().query {
                        filter { resource -> searchRegex.containsMatchIn(resource.name) }
                    }.find()

                    runOnUiThread {
                        currentSearchResults.value = sections + documents + resources
                    }
                }
            } else {
                showTopLevel()
            }
        }
    }

    /**
     * Helper function to use switchMap with a PagedList
     */
    @MainThread
    fun <X, Y> switchMapPaged(
            source: LiveData<X>,
            switchMapFunction: Function<X, LiveData<PagedList<Y>>>): MediatorLiveData<PagedList<Y>> {
        val result = MediatorLiveData<PagedList<Y>>()
        result.addSource(source, object : Observer<X> {
            var mSource: LiveData<PagedList<Y>>? = null

            override fun onChanged(x: X?) {
                val newLiveData = switchMapFunction.apply(x)
                if (mSource === newLiveData) {
                    return
                }
                if (mSource != null) {
                    result.removeSource(mSource!!)
                }
                mSource = newLiveData
                if (mSource != null) {
                    result.addSource(mSource!!) { y -> result.setValue(y) }
                }
            }
        })
        return result
    }

    /**
     * Get the LiveData object for this EntityListViewModel
     */
    private fun getSectionLiveData(sectionId: String = ROOT_SECTION_ID): LiveData<PagedList<SectionEntity>> {
        return LivePagedListBuilder(ObjectBoxDataSource.Factory(
                sectionPersistenceManager.standardOperation().query {
                    equal(SectionEntity_.id, sectionId)
                    // TODO: implement sorting with inhomogeneous types
                    // sort(TYPE_COMPARATOR)
                }),
//                documentPersistenceManager!!.standardOperation().query {
//
//                }),
                getPageSize()
        ).build()
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
     * @param section the section to open
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
    fun isSearching(): Boolean {
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
    fun showTopLevel() {
        currentSectionId.value = ROOT_SECTION_ID
        openSection(ROOT_SECTION_ID)
    }

    /**
     * Persist the given data
     */
    fun persistListData(data: SectionEntity) {
        sectionPersistenceManager.insertOrUpdateRoot(data)
        currentSectionId.postValue(currentSectionId.value)
    }

    suspend fun createNewSection(sectionName: String) {
        val currentSectionId = currentSectionId.value!!
        val parentSection = sectionPersistenceManager.findById(currentSectionId)
        if (parentSection == null) {
            Timber.e { "Parent section could not be found in persistence while trying to create a new section in it" }
            return
        }

        restClient.createSection(currentSectionId, sectionName).fold(success = {
            val createdSection = it.asEntity(documentContentPersistenceManager)
            parentSection.subsections.add(createdSection)
            // insert it into persistence
            sectionPersistenceManager.standardOperation().put(parentSection)
            reloadEvent.value = true
        }, failure = {
            Timber.e(it) { "Error creating section" }
//            toast("There was an error :(")
        })
    }

    fun createNewDocument(documentName: String) {
        viewModelScope.launch {
            val name = if (documentName.isEmpty()) "New Document" else documentName
            val currentSectionId = currentSectionId.value!!
            val parentSection = sectionPersistenceManager.findById(currentSectionId)
            if (parentSection == null) {
                Timber.e { "Parent section could not be found in persistence while trying to create a new document in it" }
                return@launch
            }

            restClient.createDocument(currentSectionId, name).fold(
                    success = {
                        // insert it into persistence
                        documentPersistenceManager.standardOperation().put(
                                it.asEntity(parentSection = parentSection))
                        // and open the editor right away
                        withContext(Dispatchers.Main) {
                            openDocumentEditorEvent.value = it.id
                        }
                        reloadEvent.value = true
                    }, failure = {
                Timber.e(it) { "Error creating document" }
//            context().toast("There was an error :(")
            })
        }
    }

    /**
     * Rename a document
     */
    fun renameDocument(id: String, documentName: String) {
        viewModelScope.launch {
            restClient.renameDocument(id, documentName)
            reloadEvent.value = true
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            restClient.deleteDocument(id)
            reloadEvent.value = true
        }
    }

    companion object {
        /** ID of the tree root section */
        const val ROOT_SECTION_ID = "root"
    }
}