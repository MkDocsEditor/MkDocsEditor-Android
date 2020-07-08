package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.airbnb.epoxy.Typed3EpoxyController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.material.toast
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class FileBrowserFragment : MultiPersistableListFragmentBase() {

    private val fileBrowserViewModel: FileBrowserViewModel by viewModels()

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        // search
        fileBrowserViewModel.currentSearchResults.observe(this, Observer {
            if (it.isEmpty()) {
                showEmpty()
            } else {
                hideEmpty()
            }
            epoxyController.setData(it.filterByExpectedType(), it.filterByExpectedType(), it.filterByExpectedType())
        })

        // normal navigation
        fileBrowserViewModel.currentSection.observe(this, Observer {
            if (it.isNotEmpty()) {
                it.first().let { section ->
                    if (section.subsections.isEmpty() and section.documents.isEmpty() and section.resources.isEmpty()) {
                        showEmpty()
                    } else {
                        hideEmpty()
                    }
                    epoxyController.setData(section.subsections, section.documents, section.resources)
                }
            } else {
                // in theory this will navigate back until a section is found
                // or otherwise show the "empty" screen
                if (!fileBrowserViewModel.navigateUp()) {
                    showEmpty()
                }
            }
        })

        fileBrowserViewModel.currentSearchFilter.observe(this, Observer {
            searchView?.setQuery(it, false)
        })
        fileBrowserViewModel.isSearchExpanded.observe(this, Observer { isExpanded ->
            if (!isExpanded) {
                searchView?.clearFocus()
                searchMenuItem?.collapseActionView()
            }
        })

        fileBrowserViewModel.openDocumentEditorEvent.observe(this, Observer { documentId ->
            openDocumentEditor(documentId)
        })

        return super.createViewDataBinding(inflater, container, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(de.markusressel.kutepreferences.core.R.menu.kutepreferences__menu, menu)

        searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem?.apply {
            icon = ContextCompat.getDrawable(context as Context, de.markusressel.kutepreferences.core.R.drawable.ic_search_24px)
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    val oldValue = fileBrowserViewModel.isSearchExpanded.value
                    if (oldValue == null || !oldValue) {
                        fileBrowserViewModel.isSearchExpanded.value = true
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    val oldValue = fileBrowserViewModel.isSearchExpanded.value
                    if (oldValue == null || oldValue) {
                        fileBrowserViewModel.isSearchExpanded.value = false
                    }
                    return true
                }
            })
        }

        searchView = searchMenuItem?.actionView as SearchView
        searchView?.let {
            RxSearchView
                    .queryTextChanges(it)
                    .skipInitialValue()
                    .bindUntilEvent(this, Lifecycle.Event.ON_DESTROY)
                    .debounce(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onNext = { text ->
                        fileBrowserViewModel.setSearch(text.toString())
                    }, onError = { error ->
                        Timber.e(error) { "Error filtering list" }
                    })
        }
    }

    override suspend fun getLoadDataFromSourceFunction(): Result<SectionModel, FuelError> {
        return restClient.getItemTree()
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity(fileBrowserViewModel.documentContentPersistenceManager)
            else -> throw IllegalArgumentException("Cant map object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        fileBrowserViewModel.persistListData(data as SectionEntity)
    }

    override fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>> {
        return object : Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>() {
            override fun buildModels(sections: List<SectionEntity>, documents: List<DocumentEntity>, resources: List<ResourceEntity>) {
                sections.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemSection {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            fileBrowserViewModel.openSection(model.item().id)
                        }
                        onlongclick { model, parentView, clickedView, position ->
                            Timber.d { "Long clicked section list item" }
                            true
                        }
                    }
                }

                documents.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemDocument {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openDocumentEditor(model.item().id)
                        }
                        onlongclick { model, parentView, clickedView, position ->
                            openEditDocumentDialog(model.item())
                            true
                        }
                    }
                }

                resources.sortedBy {
                    it.name.toLowerCase(Locale.getDefault())
                }.forEach {
                    listItemResource {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openResourceDetailPage(model.item())
                        }
                        onlongclick { model, parentView, clickedView, position ->
                            Timber.d { "Long clicked resource list item" }
                            true
                        }
                    }
                }
            }
        }
    }

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(
                FabConfig.Fab(id = 0,
                        description = R.string.create_document,
                        icon = MaterialDesignIconic.Icon.gmi_file_add,
                        onClick = {
                            openCreateDocumentDialog()
                        }),
                FabConfig.Fab(id = 1,
                        description = R.string.create_section,
                        icon = MaterialDesignIconic.Icon.gmi_folder,
                        onClick = {
                            openCreateSectionDialog()
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

    private fun openDocumentEditor(documentId: String) {
        Timber.d { "Opening Document '$documentId'" }

        navController.navigate(
                FileBrowserFragmentDirections.actionFileBrowserPageToCodeEditorPage(documentId)
        )
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber.d { "Opening Resource '${resource.name}'" }
        context?.toast("Resources are not yet supported :(", Toast.LENGTH_LONG)
    }

    private fun openCreateSectionDialog() {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = fileBrowserViewModel.sectionPersistenceManager.findById(currentSectionId)!!
        val existingSections = parentSection.subsections.map { it.name }

        MaterialDialog(context()).show {
            title(R.string.create_section)
            input(waitForPositiveButton = false,
                    allowEmpty = false,
                    hintRes = R.string.hint_new_section,
                    inputType = InputType.TYPE_CLASS_TEXT) { dialog, text ->

                val trimmedText = text.toString().trim()

                val inputField = dialog.getInputField()
                val isValid = !existingSections.contains(trimmedText)

                inputField.error = when (isValid) {
                    true -> null
                    false -> getString(R.string.error_section_already_exists)
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
            }

            positiveButton(android.R.string.ok, click = {
                CoroutineScope(Dispatchers.IO).launch {
                    val sectionName = getInputField().text.toString().trim()
                    fileBrowserViewModel.createNewSection(sectionName)
                }
            })
            negativeButton(android.R.string.cancel)
        }
    }

    private fun openCreateDocumentDialog() {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = fileBrowserViewModel.sectionPersistenceManager.findById(currentSectionId)!!
        val existingDocuments = parentSection.documents.map { it.name }

        MaterialDialog(context()).show {
            title(R.string.create_document)
            input(waitForPositiveButton = false,
                    allowEmpty = false,
                    hintRes = R.string.hint_new_document,
                    inputType = InputType.TYPE_CLASS_TEXT) { dialog, text ->

                val trimmedText = text.toString().trim()

                val inputField = dialog.getInputField()
                val isValid = !existingDocuments.contains(trimmedText)

                inputField.error = when (isValid) {
                    true -> null
                    false -> getString(R.string.error_document_already_exists)
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
            }

            positiveButton(android.R.string.ok, click = {
                CoroutineScope(Dispatchers.IO).launch {
                    val documentName = getInputField().text.toString().trim()
                    fileBrowserViewModel.createNewDocument(documentName)
                }
            })
            negativeButton(android.R.string.cancel)
        }
    }

    private fun openEditDocumentDialog(entity: DocumentEntity) {
        val currentSectionId = fileBrowserViewModel.currentSectionId.value!!
        val parentSection = fileBrowserViewModel.sectionPersistenceManager.findById(currentSectionId)!!
        val existingDocuments = parentSection.documents.map { it.name }

        MaterialDialog(context()).show {
            title(R.string.edit_document)
            input(waitForPositiveButton = false,
                    allowEmpty = false,
                    prefill = entity.name,
                    inputType = InputType.TYPE_CLASS_TEXT) { dialog, text ->

                val trimmedText = text.toString().trim()

                val inputField = dialog.getInputField()
                val isValid = !existingDocuments.contains(trimmedText)

                inputField.error = when (isValid) {
                    true -> null
                    false -> getString(R.string.error_document_already_exists)
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
            }

            positiveButton(android.R.string.ok, click = {
                CoroutineScope(Dispatchers.IO).launch {
                    val documentName = getInputField().text.toString().trim()
                    fileBrowserViewModel.renameDocument(entity.id, documentName)
                }
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