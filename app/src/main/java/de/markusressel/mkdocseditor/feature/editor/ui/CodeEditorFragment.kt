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
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.ui.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.ui.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch.Patch
import java.util.LinkedList

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
        return View(context)
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

//    override fun onSelectionChanged(start: Int, end: Int, hasSelection: Boolean) {
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