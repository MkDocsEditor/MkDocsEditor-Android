package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import com.github.ajalt.timberkt.Timber
import com.github.nitrico.lastadapter.LastAdapter
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.BR
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.databinding.ListItemDocumentBinding
import de.markusressel.mkdocseditor.databinding.ListItemResourceBinding
import de.markusressel.mkdocseditor.databinding.ListItemSectionBinding
import de.markusressel.mkdocseditor.extensions.isWifiEnabled
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.ListFragmentBase
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
class DocumentsFragment : ListFragmentBase() {

    @Inject
    lateinit var restClient: MkDocsRestClient

    override fun createAdapter(): LastAdapter {
        return LastAdapter(listValues, BR.item)
                .map<SectionModel, ListItemSectionBinding>(R.layout.list_item_section) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@DocumentsFragment
                    }
                    onClick {
                        openSection(it.binding.item!!)
                    }
                }
                .map<DocumentModel, ListItemDocumentBinding>(R.layout.list_item_document) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@DocumentsFragment
                    }
                    onClick {
                        openDocumentEditor(it.binding.item!!)
                    }
                }
                .map<ResourceModel, ListItemResourceBinding>(R.layout.list_item_resource) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@DocumentsFragment
                    }
                    onClick {
                        openResourceDetailPage(it.binding.item!!)
                    }
                }
    }

    override fun loadListDataFromSource(): Single<SectionModel> {
        val dummyDocument1 = DocumentModel("document", "2358329473448408384", "Automatic updates.md", 456, Date())
        val dummyDocument2 = DocumentModel("document", "2", "Android Studio", 50, Date())
        val dummySubsectionSoftware = SectionModel("1", "Software", subsections = emptyList(), documents = listOf(dummyDocument1, dummyDocument2), resources = emptyList())

        val dummyDocument3 = DocumentModel("document", "3", "CPU", 50, Date())
        val dummySubsectionHardware = SectionModel("2", "Hardware", subsections = emptyList(), documents = listOf(dummyDocument3), resources = emptyList())
        val dummySection = SectionModel("0", "root", subsections = listOf(dummySubsectionSoftware, dummySubsectionHardware), documents = listOf(), resources = emptyList())

        return when {
            context!!.isWifiEnabled() -> restClient.getItemTree()
            else -> Single.just(dummySection)
        }
    }

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(FabConfig.Fab(description = "Add", icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
            openAddDialog()
        }))
    }

    private fun openDocumentEditor(document: DocumentModel) {
        Timber
                .d { "Opening Document '${document.name}'" }

        val intent = EditorActivity
                .getNewInstanceIntent(context as Context, document.id, document.name)
        startActivity(intent)
    }

    private fun openResourceDetailPage(resource: ResourceModel) {
        Timber
                .d { "Opening Resource '${resource.name}'" }

    }

    private fun openAddDialog() {

    }

    override fun onStart() {
        super
                .onStart()
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