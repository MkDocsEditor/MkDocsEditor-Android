package de.markusressel.mkdocseditor.feature.editor.ui

//import com.otaliastudios.zoom.ZoomEngine
//import de.markusressel.kodeeditor.library.view.CodeEditorLayout
//import de.markusressel.kodeeditor.library.view.SelectionChangedListener
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.ui.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.ui.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch.Patch
import java.util.*

/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class CodeEditorFragment : DaggerSupportFragmentBase()
//    SelectionChangedListener
{

    private val viewModel: CodeEditorViewModel by viewModels()

//    private lateinit var binding: FragmentEditorBinding

//    private lateinit var codeEditorLayout: CodeEditorLayout

    private var noConnectionSnackbar: Snackbar? = null

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(
            this,
            optionsMenuRes = R.menu.options_menu_editor,
            onCreateOptionsMenu = { menu: Menu?, _: MenuInflater? ->
                // set refresh icon
                menu?.findItem(R.id.refresh)?.apply {
                    val refreshIcon =
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                    icon = refreshIcon
                }

                // set "edit" icon
//                viewModel.editModeActive.observe(viewLifecycleOwner) { editModeActive ->
//                    menu?.findItem(R.id.edit)?.apply {
//                        icon = if (editModeActive) {
//                            iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_eye)
//                        } else {
//                            iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_edit)
//                        }
//                    }
//                    activity?.invalidateOptionsMenu()
//                }

                viewModel.editable.asLiveData().observe(viewLifecycleOwner) { editable ->
                    // set "edit" icon
                    menu?.findItem(R.id.edit)?.apply {
                        // invisible initially, until a server connection is established
//                        isVisible = viewModel.offlineModeManager.isEnabled().not() && editable
                    }
                    activity?.invalidateOptionsMenu()
                }
            },
            onOptionsMenuItemClicked = {
                when (it.itemId) {
//                    R.id.open_in_browser -> viewModel.onOpenInBrowserClicked()
                    R.id.edit -> viewModel.enableEditMode()
                    else -> false
                }
            },
            onPrepareOptionsMenu = { menu ->
                // set open in browser icon
                menu?.findItem(R.id.open_in_browser)?.apply {
                    val openInBrowserIcon =
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
                    icon = openInBrowserIcon
//                    if (viewModel.preferencesHolder.webUriPreference.persistedValue.value.isBlank()) {
//                        isVisible = false
//                        isEnabled = false
//                    }
                }
            })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        optionsMenuComponent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        binding = FragmentEditorBinding.inflate(layoutInflater, container, false).apply {
//            lifecycleOwner = this@CodeEditorFragment
//            viewModel = this@CodeEditorFragment.viewModel
//        }

//        val parent = binding.root as ViewGroup
//        return onCreateView(inflater, parent, savedInstanceState)
        return View(context)
    }

//    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
//        super.onCreateOptionsMenu(menu, inflater)
//        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
//    }
//
//    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu)
//        optionsMenuComponent.onPrepareOptionsMenu(menu)
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
//    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep the current text
     */
    private fun restoreEditorState(
        entity: DocumentEntity? = null,
        text: String? = null
    ) {
        val content = entity?.content?.target
        if (content != null) {
            if (text != null) {
//                setEditorText(text)
            } else {
                // restore values from cache
//                setEditorText(content.text, content.selection)
            }

//            val absolutePosition = computeAbsolutePosition(PointF(content.panX, content.panY))
//            codeEditorLayout.codeEditorView.moveTo(
//                content.zoomLevel,
//                absolutePosition.x,
//                absolutePosition.y,
//                animate = false
//            )
        } else {
            if (text != null) {
//                setEditorText(text)
            }
        }
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.apply {
//            loading.observe(viewLifecycleOwner) { loading ->
//                when {
//                    loading -> loadingComponent.showLoading()
//                    else -> loadingComponent.showContent(animated = true)
//                }
//            }

//            lifecycleScope.launchWhenCreated {
//                documentEntityFlow.filterNotNull().collectLatest { resource ->
//                    loading.value = resource is Resource.Loading
//
//                    if (resource is Resource.Error) {
//                        Timber.e(resource.error)
//                        if (viewModel.isCachedContentAvailable()) {
//                            // fallback to offline mode, if available
//                            events.postValue(CodeEditorEvent.Error(resource.error?.message))
//                        } else {
//                            MaterialDialog(context()).show {
//                                title(R.string.error)
//                                message(
//                                    text = getString(
//                                        R.string.error_and_no_offline_version,
//                                        resource.error?.localizedMessage
//                                    )
//                                )
//                                negativeButton(android.R.string.ok, click = {
//                                    it.dismiss()
//                                })
//                                onDismiss {
//                                    navController.navigateUp()
//                                }
//                            }
//                        }
//                        return@collectLatest
//                    }
//
//                    val entity = resource.data
//                    val content = entity?.content?.target?.text
//
//                    if (offlineModeManager.isEnabled() && content == null) {
//                        MaterialDialog(context()).show {
//                            title(R.string.no_offline_version_title)
//                            message(R.string.no_offline_version)
//                            negativeButton(android.R.string.ok, click = {
//                                it.dismiss()
//                            })
//                            onDismiss {
//                                navController.navigateUp()
//                            }
//                        }
//                    } else {
//                        restoreEditorState(entity = entity)
//                    }
//                }
//            }

            events.observe(viewLifecycleOwner) { event ->
                when (event) {
                    is CodeEditorEvent.ConnectionStatus -> {
                        noConnectionSnackbar?.dismiss()
                        if (event.connected) {
                            runOnUiThread {
//                                codeEditorLayout.snack(R.string.connected, Snackbar.LENGTH_SHORT)
                            }
                        } else {
//                            if (event.throwable != null) {
//                                Timber.e(event.throwable) { "Websocket error code: ${event.errorCode}" }
//                                noConnectionSnackbar = codeEditorLayout.snack(
//                                    text = R.string.server_unavailable,
//                                    duration = Snackbar.LENGTH_INDEFINITE,
//                                    actionTitle = R.string.retry,
//                                    action = {
//                                        viewModel.onRetryClicked()
//                                    })
//                            } else if (viewModel.offlineModeManager.isEnabled().not()) {
//                                noConnectionSnackbar = codeEditorLayout.snack(
//                                    text = R.string.not_connected,
//                                    duration = Snackbar.LENGTH_INDEFINITE,
//                                    actionTitle = R.string.connect,
//                                    action = {
//                                        viewModel.onConnectClicked()
//                                    })
//                            }
                        }
                    }

                    is CodeEditorEvent.InitialText -> {
//                        restoreEditorState(viewModel.documentEntityFlow.value?.data, event.text)
                    }

                    is CodeEditorEvent.TextChange -> {
//                        handleExternalTextChange(
//                            event.newText,
//                            event.patches
//                        )
                    }

                    is CodeEditorEvent.Error -> {
                        Timber.e(event.throwable) { "Error" }
                        noConnectionSnackbar?.dismiss()
//                        noConnectionSnackbar = codeEditorLayout.snack(
//                            text = event.message
//                                ?: event.throwable?.localizedMessage
//                                ?: getString(R.string.unknown_error),
//                            duration = Snackbar.LENGTH_INDEFINITE,
//                            actionTitle = getString(R.string.retry),
//                            action = {
//                                viewModel.onRetryClicked()
//                            })
                    }
                }
            }
        }

//        codeEditorLayout = view.findViewById(R.id.codeEditorLayout)
//        codeEditorLayout.apply {
//            minimapGravity = Gravity.BOTTOM or Gravity.END
//            languageRuleBook = MarkdownRuleBook()
//            codeEditorView.codeEditText.addTextChangedListener(
//                onTextChanged = { text, start, before, count ->
//                    viewModel.currentText.value = text.toString()
//                },
//            )
//            codeEditorView.engine.addListener(
//                object : ZoomEngine.Listener {
//                    override fun onIdle(engine: ZoomEngine) {
//                        saveEditorState()
//                    }
//
//                    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
//                        viewModel.currentPosition.set(
//                            codeEditorLayout.codeEditorView.panX,
//                            codeEditorLayout.codeEditorView.panY
//                        )
//                        viewModel.currentZoom.value = codeEditorLayout.codeEditorView.zoom
//                    }
//                })
//
//            codeEditorView.selectionChangedListener = this@CodeEditorFragment
//
//            // disable user input by default, it will be enabled automatically once connected to the server
//            // if not disabled in preferences
//            editable = false
//        }
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
//    private fun setEditorText(
//        text: String,
//        selectionStart: Int? = null,
//        selectionEnd: Int? = null
//    ) {
//        codeEditorLayout.codeEditorView.apply {
//            // we don't listen to selection changes when the text is changed via code
//            // because the selection will be restored from persistence anyway
//            // and the listener would override this
//            selectionChangedListener = null
//            this.text = text
//            selectionStart?.let {
//                setEditorSelection(text.length, it, selectionEnd)
//            }
//            selectionChangedListener = this@CodeEditorFragment
//        }
//    }

//    private fun setEditorSelection(maxIndex: Int, selectionStart: Int, selectionEnd: Int?) {
//        val endIndex = selectionEnd ?: selectionStart
//        codeEditorLayout.codeEditorView.codeEditText.setSelection(
//            selectionStart.coerceIn(0, maxIndex),
//            endIndex.coerceIn(0, maxIndex)
//        )
//    }

//    override fun onSelectionChanged(start: Int, end: Int, hasSelection: Boolean) {
//        saveEditorState()
//    }

//    private fun handleExternalTextChange(newText: String, patches: LinkedList<Patch>) {
//        val oldSelectionStart = codeEditorLayout.codeEditorView.codeEditText.selectionStart
//        val oldSelectionEnd = codeEditorLayout.codeEditorView.codeEditText.selectionEnd
//        viewModel.currentText.value = newText

    // set new cursor position
//        val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, patches)
//            .coerceIn(0, newText.length)
//        val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, patches)
//            .coerceIn(0, newText.length)
//
//        setEditorText(newText, newSelectionStart, newSelectionEnd)
//        saveEditorState()
//    }

//    /**
//     * Calculates the positioning percentages for x and y axis
//     *
//     * @return a point with horizontal (x) and vertical (y) positioning percentages
//     */
//    private fun getCurrentPositionPercentage() = codeEditorLayout.codeEditorView.engine.run {
//        PointF(
//            computeHorizontalScrollOffset().toFloat() / computeHorizontalScrollRange(),
//            computeVerticalScrollOffset().toFloat() / computeVerticalScrollRange()
//        )
//    }
//
//    /**
//     * Takes a point with percentage values and returns a point with the actual absolute coordinates
//     *
//     * @return a point with horizontal (x) and vertical (y) absolute, scale-independent, positioning coordinate values
//     */
//    private fun computeAbsolutePosition(percentage: PointF) =
//        codeEditorLayout.codeEditorView.engine.run {
//            PointF(
//                -1 * percentage.x * (computeHorizontalScrollRange() / realZoom),
//                -1 * percentage.y * (computeVerticalScrollRange() / realZoom)
//            )
//        }


    private fun calculateNewSelectionIndex(
        oldSelection: Int,
        patches: LinkedList<Patch>
    ): Int {
        var newSelection = oldSelection

        var currentIndex: Int
        // calculate how many characters have been inserted before the cursor
        patches.forEach { patch ->
            currentIndex = patch.start1

            patch.diffs.forEach { diff ->
                when (diff.operation) {
                    diff_match_patch.Operation.INSERT -> {
                        if (currentIndex < newSelection) {
                            newSelection += diff.text.length
                        }
                    }

                    diff_match_patch.Operation.DELETE -> {
                        if (currentIndex < newSelection) {
                            newSelection -= diff.text.length
                        }
                    }

                    else -> {
                        currentIndex += diff.text.length
                    }
                }
            }
        }

        return newSelection
    }

    /**
     * Saves the current editor state in a persistent cache
     */
    fun saveEditorState() {
//        // TODO: this will probably cause problems when deleting all characters in a document
//        if (viewModel.currentText.value.isNullOrEmpty()) {
//            // skip if there is no text to save
//            return
//        }
//
//        val positioningPercentage = getCurrentPositionPercentage()
//
//        if (positioningPercentage.x.isNaN() || positioningPercentage.y.isNaN()) {
//            // don't save the state if it is incomplete
//            return
//        }
//
//        viewModel.saveEditorState(
//            selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
//            panX = positioningPercentage.x,
//            panY = positioningPercentage.y
//        )
    }

    override fun onPause() {
        saveEditorState()
        super.onPause()
    }

}