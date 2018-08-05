package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.github.ajalt.timberkt.Timber
import com.github.nitrico.lastadapter.LastAdapter
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.BR
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.databinding.ListItemDocumentBinding
import de.markusressel.mkdocseditor.databinding.ListItemResourceBinding
import de.markusressel.mkdocseditor.databinding.ListItemSectionBinding
import de.markusressel.mkdocseditor.extensions.isWifiEnabled
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import de.markusressel.mkdocsrestclient.section.SectionModel
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
    lateinit var persistenceManager: SectionPersistenceManager

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
        val dummyDocument1 = DocumentModel("document", "2358329473448408384", "Automatic updates.md", 456, Date())
        val dummyDocument2 = DocumentModel("document", "2", "Android Studio", 50, Date())
        val dummySubsectionSoftware = SectionModel("1", "Software", subsections = emptyList(), documents = listOf(dummyDocument1, dummyDocument2), resources = emptyList())

        val dummyDocument3 = DocumentModel("document", "3", "CPU", 50, Date())
        val dummySubsectionHardware = SectionModel("2", "Hardware", subsections = emptyList(), documents = listOf(dummyDocument3), resources = emptyList())
        val dummySection = SectionModel("0", "root", subsections = listOf(dummySubsectionSoftware, dummySubsectionHardware), documents = listOf(), resources = emptyList())

        return when {
            context!!.isWifiEnabled() -> restClient.getItemTree() as Single<Any>
            else -> Single.just(dummySection)
        }
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity()
            is DocumentModel -> it.asEntity()
            is ResourceModel -> it.asEntity()
            else -> throw IllegalArgumentException("Cant compare object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        persistenceManager
                .standardOperation()
                .removeAll()

        val rootSection = data as SectionEntity

        val allSections = rootSection
                .getAllSubsections()

        persistenceManager
                .standardOperation()
                .put(allSections)
    }

    override fun loadListDataFromPersistence(): List<IdentifiableListItem> {
        return persistenceManager
                .standardOperation()
                .all
    }

    @Inject
    lateinit var restClient: MkDocsRestClient

    override fun createAdapter(): LastAdapter {
        return LastAdapter(listValues, BR.item)
                .map<SectionEntity, ListItemSectionBinding>(R.layout.list_item_section) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@FileBrowserFragment
                    }
                    onClick {
                        openSection(it.binding.item!!)
                    }
                }
                .map<DocumentEntity, ListItemDocumentBinding>(R.layout.list_item_document) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@FileBrowserFragment
                    }
                    onClick {
                        openDocumentEditor(it.binding.item!!)
                    }
                }
                .map<ResourceEntity, ListItemResourceBinding>(R.layout.list_item_resource) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@FileBrowserFragment
                    }
                    onClick {
                        openResourceDetailPage(it.binding.item!!)
                    }
                }
    }

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(FabConfig.Fab(description = R.string.add, icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
            openAddDialog()
        }))
    }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super
                .onViewCreated(view, savedInstanceState)
        reloadDataFromSource()
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