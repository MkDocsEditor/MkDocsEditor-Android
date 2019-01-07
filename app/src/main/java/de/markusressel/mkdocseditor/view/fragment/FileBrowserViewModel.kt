package de.markusressel.mkdocseditor.view.fragment

import androidx.annotation.MainThread
import androidx.arch.core.util.Function
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
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
        get() {
            if (field.size == 0) {
                field.add(SectionBackstackItem("root"))
            }
            return field
        }
    private val currentSearchFilter = MutableLiveData<String>()

    var persistenceManager: PersistenceManagerBase<SectionEntity>? = null

    val currentSectionId = MutableLiveData<String>()

    val currentSection = switchMapPaged<String, SectionEntity>(currentSectionId,
            Function { sectionId ->
                getSectionLiveData(sectionId)
            })

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
    private fun getSectionLiveData(sectionId: String = "root"): LiveData<PagedList<SectionEntity>> {
        return LivePagedListBuilder(ObjectBoxDataSource.Factory(
                persistenceManager!!.standardOperation().query {
                    equal(SectionEntity_.id, sectionId)
                    // TODO: implement sorting with inhomogeneous types
                    // sort(TYPE_COMPARATOR)
                }),
                getPageSize()
        ).build()
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
        if (currentSectionId.value == sectionId) {
            // ignore if already set
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
        if (currentSectionId.value == "root" || backstack.size <= 1) {
            return false
        }

        backstack.pop()
        openSection(backstack.peek().sectionId, false)
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