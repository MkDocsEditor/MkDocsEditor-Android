package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.widget.Toast
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.Typed3EpoxyController
import com.github.ajalt.timberkt.Timber
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.*
import de.markusressel.mkdocseditor.listItemDocument
import de.markusressel.mkdocseditor.listItemResource
import de.markusressel.mkdocseditor.listItemSection
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.kotlin.query
import io.reactivex.Single
import java.util.*
import javax.inject.Inject


/**
 * Server Status fragment
 *
 * Created by Markus on 07.01.2018.
 */
class FileBrowserFragment : MultiPersistableListFragmentBase() {

    @Inject
    lateinit var sectionPersistenceManager: SectionPersistenceManager
    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager
    @Inject
    lateinit var resourcePersistenceManager: ResourcePersistenceManager

    override fun itemContainsCurrentSearchString(item: IdentifiableListItem): Boolean {
        return false
    }

    override fun sortListData(listData: List<IdentifiableListItem>): List<IdentifiableListItem> {
        val typeComparator = Comparator<IdentifiableListItem> { a, b ->
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

        return listData
                .sortedWith(typeComparator)
    }

    override fun getLoadDataFromSourceFunction(): Single<Any> {
        return restClient.getItemTree() as Single<Any>
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity()
            else -> throw IllegalArgumentException("Cant compare object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        resourcePersistenceManager
                .standardOperation()
                .removeAll()
        documentPersistenceManager
                .standardOperation()
                .removeAll()
        sectionPersistenceManager
                .standardOperation()
                .removeAll()

        val rootSection = data as SectionEntity

        sectionPersistenceManager
                .standardOperation()
                .put(rootSection)
    }

    override fun loadListDataFromPersistence(): IdentifiableListItem? {
        return sectionPersistenceManager
                .standardOperation()
                .query {
                    equal(SectionEntity_.name, "docs")
                }
                .findFirst()
    }

    override fun createEpoxyController(): EpoxyController {
        return object : Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>() {
            override fun buildModels(sections: List<SectionEntity>, documents: List<DocumentEntity>, resources: List<ResourceEntity>) {
                sections.forEach {
                    listItemSection {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openSection(model.item())
                        }
                    }
                }

                documents.forEach {
                    listItemDocument {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openDocumentEditor(model.item())
                        }
                    }
                }

                resources.forEach {
                    listItemResource {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openResourceDetailPage(model.item())
                        }
                    }
                }
            }
        }
    }

    override fun updateControllerData(newData: List<IdentifiableListItem>) {
        val typed3EpoxyController = epoxyController as Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>
        typed3EpoxyController.setData(
                newData.filterByExpectedType(),
                newData.filterByExpectedType(),
                newData.filterByExpectedType()
        )
    }

    //    override fun getRightFabs(): List<FabConfig.Fab> {
    //        return listOf(FabConfig.Fab(description = R.string.add, icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
    //            openAddDialog()
    //        }))
    //    }

    private fun openDocumentEditor(document: DocumentEntity) {
        Timber
                .d { "Opening Document '${document.name}'" }

        val intent = EditorActivity
                .getNewInstanceIntent(context as Context, document.id, document.name)
        startActivity(intent)
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber
                .d { "Opening Resource '${resource.name}'" }
        Toast
                .makeText(context as Context, "Resources are not yet supported :(", Toast.LENGTH_LONG)
                .show()
    }

    private fun openAddDialog() {

    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        if (backstack.size > 1) {
            backstack
                    .pop()
            openSection(backstack.peek().section, false)
            return true
        }

        return false
    }

}