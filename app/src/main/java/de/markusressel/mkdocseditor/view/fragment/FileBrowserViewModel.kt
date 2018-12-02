package de.markusressel.mkdocseditor.view.fragment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity_
import de.markusressel.mkdocseditor.view.viewmodel.EntityListViewModel
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.kotlin.query
import java.util.*

class FileBrowserViewModel : EntityListViewModel() {

    private val backstack: Stack<SectionBackstackItem> = Stack()
    private val currentSearchFilter = MutableLiveData<String>()

    var currentSectionId = "root"

    private var listLiveData: LiveData<PagedList<SectionEntity>>? = null

    /**
     * Get the LiveData object for this EntityListViewModel
     */
    fun getSectionLiveData(persistenceManager: PersistenceManagerBase<SectionEntity>): LiveData<PagedList<SectionEntity>> {
        if (listLiveData == null) {
            listLiveData = LivePagedListBuilder(ObjectBoxDataSource.Factory(
                    persistenceManager.standardOperation().query {
                        equal(SectionEntity_.id, currentSectionId)
                        // TODO: implement sorting with inhomogeneous types
                        // sort(TYPE_COMPARATOR)
                    }),
                    getPageSize()
            ).build()
        }

        return listLiveData!!
    }

    /**
     * Filters the given list by the currently active filter and sort options
     */
    private fun filterList(newData: List<IdentifiableListItem>): List<IdentifiableListItem> {
        val filteredNewData = newData
                .filter {
                    itemContainsCurrentSearchString(it)
                }
                .toList()
        return filteredNewData
    }

    private fun itemContainsCurrentSearchString(item: IdentifiableListItem): Boolean {
        return when (currentSearchFilter.value) {
            null -> true
            "" -> true
            else -> {
                // TODO: search item content
                true
            }
        }
    }

    internal fun openSection(section: SectionEntity, addToBackstack: Boolean = true) {
        Timber
                .d { "Opening Section '${section.name}'" }

        if (addToBackstack) {
            backstack
                    .push(SectionBackstackItem(section))
        }

        // set the section id on the ViewModel
        // TODO: GUI responds automatically
        currentSectionId = section.id
    }

    fun navigateUp(): Boolean {
        if (backstack.size <= 1) {
            return false
        }

        openSection(backstack.peek().section, false)
        return true
    }

    companion object {
        private val TYPE_COMPARATOR = Comparator<IdentifiableListItem> { a, b ->
            val typePrioA = when (a) {
                is SectionEntity -> 0
                is DocumentEntity -> 1
                is ResourceEntity -> 2
                else -> throw IllegalArgumentException("Cant compare object of type ${a.javaClass}!")
            }

            val typePrioB = when (b) {
                is SectionEntity -> 0
                is DocumentEntity -> 1
                is ResourceEntity -> 2
                else -> throw IllegalArgumentException("Cant compare object of type ${b.javaClass}!")
            }

            typePrioA
                    .compareTo(typePrioB)
        }
    }
}