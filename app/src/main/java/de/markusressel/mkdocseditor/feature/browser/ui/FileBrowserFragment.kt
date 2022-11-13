package de.markusressel.mkdocseditor.feature.browser.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.SearchView
import android.widget.Toast
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.widget.RxSearchView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.material.toast
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.ui.fragment.base.DaggerSupportFragmentBase
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit


/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class FileBrowserFragment : DaggerSupportFragmentBase() {

    private val viewModel by activityViewModels<FileBrowserViewModel>()

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
    ): View {
        lifecycleScope.launchWhenCreated {
            viewModel.uiState.collectLatest {
//                searchView?.setQuery(it.currentSearchFilter, false)

                if (!it.isSearchExpanded) {
//                    searchView?.clearFocus()
                    searchMenuItem?.collapseActionView()
                }
            }
        }

        viewModel.openDocumentEditorEvent.observe(viewLifecycleOwner) { documentId ->
            openDocumentEditor(documentId)
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                is FileBrowserEvent.OpenDocumentEditorEvent -> {
                    openDocumentEditor(event.entity.id)
                }
                is FileBrowserEvent.DownloadResourceEvent -> {
                    // TODO: download resource
                    Toast.makeText(requireContext(), "Not implemented :(", Toast.LENGTH_SHORT).show()
                }
                is FileBrowserEvent.ReloadEvent -> {
                    // showEmpty()
                }
                is FileBrowserEvent.CreateDocumentEvent -> {
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
                is FileBrowserEvent.CreateSectionEvent -> {
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
                is FileBrowserEvent.RenameDocumentEvent -> {

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

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    FileBrowserScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.options_menu_list, menu)

        searchMenuItem = menu.findItem(R.id.search)
        searchMenuItem?.apply {
            icon = IconicsDrawable(requireContext(), MaterialDesignIconic.Icon.gmi_search).apply {
                colorInt = Color.RED
                sizeDp = 24
            }
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    // TODO: what is this for?
                    val oldValue = viewModel.uiState.value.isSearchExpanded
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    val oldValue = viewModel.uiState.value.isSearchExpanded
                    return true
                }
            })
        }

        searchView = searchMenuItem?.actionView as SearchView
        searchView?.let {
            RxSearchView
                .queryTextChanges(it)
                .skipInitialValue()
                .bindUntilEvent(viewLifecycleOwner, Lifecycle.Event.ON_DESTROY)
                .debounce(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = { text ->
                    viewModel.setSearch(text.toString())
                }, onError = { error ->
                    Timber.e(error) { "Error filtering list" }
                })
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