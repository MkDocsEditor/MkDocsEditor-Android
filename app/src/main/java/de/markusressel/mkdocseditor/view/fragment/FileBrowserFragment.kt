package de.markusressel.mkdocseditor.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.airbnb.epoxy.Typed3EpoxyController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.commons.android.material.toast
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.listItemDocument
import de.markusressel.mkdocseditor.listItemResource
import de.markusressel.mkdocseditor.listItemSection
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocseditor.view.viewmodel.FileBrowserViewModel
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.reactivex.Single
import javax.inject.Inject


/**
 * Created by Markus on 07.01.2018.
 */
class FileBrowserFragment : MultiPersistableListFragmentBase() {

    @Inject
    lateinit var sectionPersistenceManager: SectionPersistenceManager
    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager
    @Inject
    lateinit var resourcePersistenceManager: ResourcePersistenceManager

    var currentSectionId: String by savedInstanceState("root")

    private val fileBrowserViewModel: FileBrowserViewModel by lazy {
        ViewModelProviders.of(this).get(FileBrowserViewModel::class.java)
    }

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        fileBrowserViewModel.persistenceManager = sectionPersistenceManager
        fileBrowserViewModel.currentSectionId.value = currentSectionId
        fileBrowserViewModel.currentSection.observe(this, Observer {
            if (it.isNotEmpty()) {
                it.first().let {
                    if (it.subsections.isEmpty() and it.documents.isEmpty() and it.resources.isEmpty()) {
                        showEmpty()
                    } else {
                        hideEmpty()
                    }
                    epoxyController.setData(it.subsections, it.documents, it.resources)
                }
            } else {
                // in theory this will navigate back until a section is found
                // or otherwise show the "empty" screen
                if (!fileBrowserViewModel.navigateUp()) {
                    showEmpty()
                }
            }
        })

        return super.createViewDataBinding(inflater, container, savedInstanceState)
    }

    override fun getLoadDataFromSourceFunction(): Single<Any> {
        return restClient.getItemTree() as Single<Any>
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity()
            else -> throw IllegalArgumentException("Cant map object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        // update existing entities
        val rootSection = data as SectionEntity

        sectionPersistenceManager.insertOrUpdateRoot(rootSection)
        fileBrowserViewModel.currentSectionId.postValue(fileBrowserViewModel.currentSectionId.value)
    }

    override fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>> {
        return object : Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>() {
            override fun buildModels(sections: List<SectionEntity>, documents: List<DocumentEntity>, resources: List<ResourceEntity>) {
                sections.forEach {
                    listItemSection {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            currentSectionId = model.item().id
                            fileBrowserViewModel.openSection(currentSectionId)
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

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(FabConfig.Fab(description = R.string.add, icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
            openAddDialog()
        }))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Bus.observe<OfflineModeChangedEvent>()
                .subscribe {
                    //                    fileBrowserViewModel.setOfflineMode(it.enabled)
                }
                .registerInBus(this)
    }

    private fun openDocumentEditor(document: DocumentEntity) {
        Timber.d { "Opening Document '${document.name}'" }

        navController.navigate(
                FileBrowserFragmentDirections.actionFileBrowserPageToCodeEditorPage(document.id)
        )
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber.d { "Opening Resource '${resource.name}'" }
        context?.toast("Resources are not yet supported :(", Toast.LENGTH_LONG)
    }

    private fun openAddDialog() {
        MaterialDialog(context()).show {
            customView(R.layout.dialog__add_document)
            positiveButton(android.R.string.ok, click = {
                context.toast("Sorry, this not yet supported")
            })
            negativeButton(android.R.string.cancel)
        }
    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        return fileBrowserViewModel.navigateUp()
    }

}