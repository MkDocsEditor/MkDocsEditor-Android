package de.markusressel.mkdocseditor.view.fragment

import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity_
import de.markusressel.mkdocseditor.view.viewmodel.EntityListViewModel
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.kotlin.query
import io.objectbox.query.Query

class SectionListViewModel : EntityListViewModel<SectionEntity>() {

    var currentSectionId = 0L

    override fun createDbQuery(persistenceManager: PersistenceManagerBase<SectionEntity>): Query<SectionEntity> {
        return persistenceManager.standardOperation().query {
            equal(SectionEntity_.id, currentSectionId)

            // TODO: implement sorting with inhomogeneous types
//            sort(TYPE_COMPARATOR)
        }
    }

    private var listLiveData2: LiveData<PagedList<IdentifiableListItem>>? = null

    /**
     * Get the LiveData object for this EntityListViewModel
     */
    fun getListLiveData2(persistenceManager: PersistenceManagerBase<SectionEntity>): LiveData<PagedList<IdentifiableListItem>> {
        if (listLiveData2 == null) {


            listLiveData2 = LivePagedListBuilder(ObjectBoxDataSource.Factory(createDbQuery(persistenceManager)), getPageSize()).build() as LiveData<PagedList<IdentifiableListItem>>
        }

        return listLiveData2!!
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