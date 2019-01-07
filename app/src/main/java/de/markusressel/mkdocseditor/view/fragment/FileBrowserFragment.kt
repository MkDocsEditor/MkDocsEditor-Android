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
import de.markusressel.mkdocseditor.data.persistence.entity.*
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.listItemDocument
import de.markusressel.mkdocseditor.listItemResource
import de.markusressel.mkdocseditor.listItemSection
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.kotlin.query
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
        addOrUpdate(rootSection)

        // remove data that is not on the server anymore
        deleteMissing(rootSection)
    }

    private fun addOrUpdate(rootSection: SectionEntity) {
        var rootEntity = sectionPersistenceManager.standardOperation().query {
            equal(SectionEntity_.id, "root")
        }.findUnique()

        if (rootEntity != null) {
            addOrUpdateEntityFields(rootEntity, rootSection)
        } else {
            rootEntity = rootSection
        }

        sectionPersistenceManager.standardOperation().put(rootEntity)
    }

    private fun addOrUpdateEntityFields(existingData: SectionEntity, newData: SectionEntity) {
        // TODO: do we have to update the section itself? Currently I don't think so.

        newData.documents.forEach { newDocument ->
            val existingDocument = existingData.documents.firstOrNull { it.id == newDocument.id }

            if (existingDocument != null) {
                existingDocument.filesize = newDocument.filesize
                existingDocument.modtime = newDocument.modtime
            } else {
                documentPersistenceManager.standardOperation().put(newDocument)
            }
        }

        newData.resources.forEach { newResource ->
            val existingResource = existingData.resources.firstOrNull { it.id == newResource.id }

            if (existingResource != null) {
                existingResource.filesize = newResource.filesize
                existingResource.modtime = newResource.modtime
            } else {
                resourcePersistenceManager.standardOperation().put(newResource)
            }
        }

        newData.subsections.forEach { newSection ->
            val existingSection = existingData.subsections.firstOrNull { it.id == newSection.id }

            if (existingSection != null) {
                addOrUpdateEntityFields(existingSection, newSection)
            } else {
                sectionPersistenceManager.standardOperation().put(newSection)
            }
        }
    }

    private fun deleteMissing(newData: SectionEntity) {
        val sectionIds = mutableSetOf<String>()
        val documentIds = mutableSetOf<String>()
        val resourceIds = mutableSetOf<String>()

        findIds(newData, sectionIds, documentIds, resourceIds)

        // remove stale sections
        val existingSectionIds = sectionPersistenceManager.standardOperation().query {
            `in`(SectionEntity_.id, sectionIds.toTypedArray())
        }.findIds()

        // find others
        val missingSectionIds = sectionPersistenceManager.standardOperation().query {
            notIn(SectionEntity_.entityId, existingSectionIds)
        }.findIds()

        sectionPersistenceManager.standardOperation().removeByKeys(missingSectionIds.toList())

        // remove stale documents
        val existingDocumentIds = documentPersistenceManager.standardOperation().query {
            `in`(DocumentEntity_.id, documentIds.toTypedArray())
        }.findIds()

        // find others
        val missingDocumentIds = documentPersistenceManager.standardOperation().query {
            notIn(DocumentEntity_.entityId, existingDocumentIds)
        }.findIds()

        documentPersistenceManager.standardOperation().removeByKeys(missingDocumentIds.toList())

        // remove stale resources
        val existingResourceIds = resourcePersistenceManager.standardOperation().query {
            `in`(ResourceEntity_.id, resourceIds.toTypedArray())
        }.findIds()

        // find others
        val missingResourceIds = resourcePersistenceManager.standardOperation().query {
            notIn(ResourceEntity_.entityId, existingResourceIds)
        }.findIds()

        resourcePersistenceManager.standardOperation().removeByKeys(missingResourceIds.toList())
    }

    /**
     * Recursive method to find all section, document and resource ids
     */
    private fun findIds(section: SectionEntity, sectionIds: MutableSet<String>, documentIds: MutableSet<String>, resourceIds: MutableSet<String>) {
        sectionIds.add(section.id)
        documentIds.addAll(section.documents.map { it.id })
        resourceIds.addAll(section.resources.map { it.id })
        section.subsections.forEach {
            findIds(it, sectionIds, documentIds, resourceIds)
        }
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