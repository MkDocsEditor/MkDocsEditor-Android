package de.markusressel.mkdocseditor.feature.browser

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.airbnb.epoxy.Typed3EpoxyController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.material.toast
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.feature.browser.FileBrowserViewModel.Event.*
import de.markusressel.mkdocseditor.listItemDocument
import de.markusressel.mkdocseditor.listItemResource
import de.markusressel.mkdocseditor.listItemSection
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.ui.fragment.base.ListFragmentBase
import de.markusressel.mkdocseditor.util.Resource
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.flow.collect
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class FileBrowserFragment : ListFragmentBase() {

    val viewModel by activityViewModels<FileBrowserViewModel>()

    override fun getFabConfig() = FabConfig(
        right = listOf(
            FabConfig.Fab(id = 0,
                description = R.string.create_document,
                icon = MaterialDesignIconic.Icon.gmi_file_add,
                onClick = {
                    viewModel.onCreateDocumentFabClicked()
                }),
            FabConfig.Fab(id = 1,
                description = R.string.create_section,
                icon = MaterialDesignIconic.Icon.gmi_folder,
                onClick = {
                    viewModel.onCreateSectionFabClicked()
                })
        )
    )

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // make sure the viewModel is instantiated on the UI thread
        viewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // search
        lifecycleScope.launchWhenStarted {
            viewModel.currentSearchResults.collect {
                if (it.isEmpty()) {
                    showEmpty()
                } else {
                    hideEmpty()
                }
                epoxyController.setData(
                    it.filterByExpectedType(),
                    it.filterByExpectedType(),
                    it.filterByExpectedType()
                )
            }
        }

        // normal navigation
        viewModel.currentSection.observe(viewLifecycleOwner) { resource ->
            val section = resource.data

            if (resource is Resource.Error) {
                context?.toast("Error: ${resource.error?.message}", Toast.LENGTH_LONG)
            }

            if (resource is Resource.Loading && section == null) {
                //showLoading()
            } else {
                //showContent()
            }

            if (section != null) {
                if (section.subsections.isEmpty() and section.documents.isEmpty() and section.resources.isEmpty()) {
                    showEmpty()
                } else {
                    hideEmpty()
                }
                epoxyController.setData(
                    section.subsections,
                    section.documents,
                    section.resources
                )
            } else {
                // in theory this will navigate back until a section is found
                // or otherwise show the "empty" screen
                if (!viewModel.navigateUp()) {
                    showEmpty()
                }
            }
        }

        viewModel.currentSearchFilter.asLiveData().observe(viewLifecycleOwner) {
            searchView?.setQuery(it, false)
        }
        viewModel.isSearchExpanded.observe(viewLifecycleOwner) { isExpanded ->
            if (!isExpanded) {
                searchView?.clearFocus()
                searchMenuItem?.collapseActionView()
            }
        }

        viewModel.openDocumentEditorEvent.observe(viewLifecycleOwner) { documentId ->
            openDocumentEditor(documentId)
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ReloadEvent -> {
                    // showEmpty()
                }
                is CreateDocumentEvent -> {
                    val existingSections = emptyList<String>()

                    MaterialDialog(context()).show {
                        lifecycleOwner(this@FileBrowserFragment)
                        title(R.string.create_document)
                        input(
                            waitForPositiveButton = false,
                            allowEmpty = false,
                            hintRes = R.string.hint_new_section,
                            inputType = InputType.TYPE_CLASS_TEXT
                        ) { dialog, text ->

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
                            val documentName = getInputField().text.toString().trim()
                            viewModel.createNewDocument(documentName)
                        })
                        negativeButton(android.R.string.cancel)
                    }
                }
                is CreateSectionEvent -> {
                    val existingSections = emptyList<String>()

                    MaterialDialog(context()).show {
                        lifecycleOwner(this@FileBrowserFragment)
                        title(R.string.create_section)
                        input(
                            waitForPositiveButton = false,
                            allowEmpty = false,
                            hintRes = R.string.hint_new_section,
                            inputType = InputType.TYPE_CLASS_TEXT
                        ) { dialog, text ->

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
                            val sectionName = getInputField().text.toString().trim()
                            viewModel.createNewSection(sectionName)
                        })
                        negativeButton(android.R.string.cancel)
                    }
                }
                is RenameDocumentEvent -> {

                    val existingDocuments = emptyList<String>()

                    MaterialDialog(context()).show {
                        lifecycleOwner(this@FileBrowserFragment)
                        title(R.string.edit_document)
                        input(
                            waitForPositiveButton = false,
                            allowEmpty = false,
                            prefill = event.entity.name,
                            inputType = InputType.TYPE_CLASS_TEXT
                        ) { dialog, text ->

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
                            val documentName = getInputField().text.toString().trim()
                            viewModel.renameDocument(event.entity.id, documentName)
                        })
                        neutralButton(R.string.delete, click = {
                            viewModel.deleteDocument(event.entity.id)
                        })
                        negativeButton(android.R.string.cancel)
                    }
                }
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.options_menu_list, menu)

        searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem?.apply {
            icon = ContextCompat.getDrawable(
                context as Context,
                R.drawable.ic_search_24px
            )
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    val oldValue = viewModel.isSearchExpanded.value
                    if (oldValue == null || !oldValue) {
                        viewModel.isSearchExpanded.value = true
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    val oldValue = viewModel.isSearchExpanded.value
                    if (oldValue == null || oldValue) {
                        viewModel.isSearchExpanded.value = false
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
                    viewModel.setSearch(text.toString())
                }, onError = { error ->
                    Timber.e(error) { "Error filtering list" }
                })
        }
    }

    override fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>> {
        return object :
            Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>() {
            override fun buildModels(
                sections: List<SectionEntity>,
                documents: List<DocumentEntity>,
                resources: List<ResourceEntity>
            ) {
                sections.sortedBy {
                    it.name.lowercase(Locale.getDefault())
                }.forEach {
                    listItemSection {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            viewModel.openSection(model.item().id)
                        }
                        onlongclick { model, parentView, clickedView, position ->
                            Timber.d { "Long clicked section list item" }
                            true
                        }
                    }
                }

                documents.sortedBy {
                    it.name.lowercase(Locale.getDefault())
                }.forEach {
                    listItemDocument {
                        id(it.id)
                        item(it)
                        onclick { model, parentView, clickedView, position ->
                            openDocumentEditor(model.item().id)
                        }
                        onlongclick { model, parentView, clickedView, position ->
                            viewModel.onDocumentLongClicked(model.item())
                        }
                    }
                }

                resources.sortedBy {
                    it.name.lowercase(Locale.getDefault())
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

        // TODO: safeArgs are currently broken :(
        // navController.navigate(
        // FileBrowserFragmentDirections.actionFileBrowserPageToCodeEditorPage(
        //     documentId
        // )
        // )

        navController.navigate(
            R.id.codeEditorPage, bundleOf(
                "documentId" to documentId
            )
        )
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber.d { "Opening Resource '${resource.name}'" }
        context?.toast("Resources are not yet supported :(", Toast.LENGTH_LONG)
    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        return viewModel.navigateUp()
    }

}