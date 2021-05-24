package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.*
import androidx.annotation.CallSuper
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.otaliastudios.zoom.ZoomEngine
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.snack
import de.markusressel.kodeeditor.library.view.CodeEditorLayout
import de.markusressel.kodeeditor.library.view.SelectionChangedListener
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.databinding.FragmentEditorBinding
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel.CodeEditorEvent.*
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch.Patch
import io.objectbox.kotlin.query
import java.util.*
import javax.inject.Inject

/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class CodeEditorFragment : DaggerSupportFragmentBase(), SelectionChangedListener {

    lateinit var binding: FragmentEditorBinding

    @Inject
    internal lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    lateinit var chromeCustomTabManager: ChromeCustomTabManager

    private lateinit var codeEditorLayout: CodeEditorLayout

    private val safeArgs: CodeEditorFragmentArgs by lazy {
        CodeEditorFragmentArgs.fromBundle(requireArguments())
    }

    private val documentId: String
        get() = safeArgs.documentId

    private var noConnectionSnackbar: Snackbar? = null

    private val viewModel: CodeEditorViewModel by viewModels()

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(
            this,
            optionsMenuRes = R.menu.options_menu_editor,
            onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
                // set refresh icon
                menu?.findItem(R.id.refresh)?.apply {
                    val refreshIcon =
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                    icon = refreshIcon
                }

                // set "edit" icon
                viewModel.editModeActive.observe(viewLifecycleOwner) { editModeActive ->
                    menu?.findItem(R.id.edit)?.apply {
                        icon = if (!editModeActive) {
                            iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_eye)
                        } else {
                            iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_edit)
                        }
                    }
                }

                viewModel.editable.observe(viewLifecycleOwner) { editable ->
                    // set "edit" icon
                    menu?.findItem(R.id.edit)?.apply {
                        // invisible initially, until a server connection is established
                        isVisible = !viewModel.offlineModeManager.isEnabled() && editable
                    }
                }
            },
            onOptionsMenuItemClicked = {
                val webBaseUri = viewModel.preferencesHolder.webUriPreference.persistedValue

                when (it.itemId) {
                    R.id.open_in_browser -> {
                        if (webBaseUri.isBlank()) {
                            return@OptionsMenuComponent false
                        }

                        val documentEntity = documentPersistenceManager
                            .standardOperation()
                            .query {
                                equal(DocumentEntity_.id, documentId)
                            }.findUnique()

                        documentEntity?.let { document ->
                            val host = viewModel.preferencesHolder.webUriPreference.persistedValue

                            val pagePath = when (document.url) {
                                "index/" -> ""
                                else -> document.url
                                // this value is already url encoded
                            }

                            val url = "$host/$pagePath"
                            chromeCustomTabManager.openChromeCustomTab(url)
                        }
                        true
                    }
                    R.id.edit -> {
                        if (viewModel.editable.value == true) {
                            // invert state of edit mode
                            viewModel.editModeActive.value = viewModel.editModeActive.value != true
                        }
                        true
                    }
                    else -> false
                }
            },
            onPrepareOptionsMenu = { menu ->
                // set open in browser icon
                menu?.findItem(R.id.open_in_browser)?.apply {
                    val openInBrowserIcon =
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
                    icon = openInBrowserIcon
                    if (viewModel.preferencesHolder.webUriPreference.persistedValue.isBlank()) {
                        isVisible = false
                        isEnabled = false
                    }
                }
            })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.documentId.value = documentId
    }

    private fun handleTextChange(newText: String, patches: LinkedList<Patch>) {
        val oldSelectionStart = codeEditorLayout.codeEditorView.codeEditText.selectionStart
        val oldSelectionEnd = codeEditorLayout.codeEditorView.codeEditText.selectionEnd
        viewModel.currentText.value = newText

        // set new cursor position
        val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, patches)
            .coerceIn(0, newText.length)
        val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, patches)
            .coerceIn(0, newText.length)

        setEditorText(newText, newSelectionStart, newSelectionEnd)
        saveEditorState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEditorBinding.inflate(layoutInflater, container, false).apply {
            lifecycleOwner = this@CodeEditorFragment
            viewModel = this@CodeEditorFragment.viewModel
        }

        val parent = binding.root as ViewGroup
        return loadingComponent.onCreateView(inflater, parent, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        optionsMenuComponent.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep [currentText]
     * @param editable when true the editor is editable, otherwise it is not
     */
    private fun restoreEditorState(
        entity: DocumentEntity? = null,
        text: String? = null,
        editable: Boolean
    ) {
        val content = entity?.content?.target
        if (content != null) {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                content.text = text
                viewModel.updateDocumentContentInCache(
                    documentId = viewModel.documentId.value!!,
                    text = text
                )
                viewModel.currentText.value = text
            } else {
                // restore values from cache
                viewModel.currentText.value = content.text
            }
            viewModel.currentZoom = content.zoomLevel
        } else {
            viewModel.currentText.value = ""
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: whats the difference between editable and editModeActive?
        viewModel.editable.observe(viewLifecycleOwner) { editable ->
            runOnUiThread {
                codeEditorLayout.editable = editable
            }
        }

        viewModel.editModeActive.observe(viewLifecycleOwner) {
            runOnUiThread {
                codeEditorLayout.editable = it
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) {
            if (it) {
                loadingComponent.showLoading()
            } else {
                loadingComponent.showContent(animated = true)
            }
        }

        viewModel.documentEntity.observe(viewLifecycleOwner) { resource ->
            viewModel.loading.value = resource is Resource.Loading

            if (resource is Resource.Error) {
                Timber.e(resource.error)
                MaterialDialog(context()).show {
                    title(R.string.error)
                    message(text = resource.error?.localizedMessage ?: "Unknown error")
                    negativeButton(android.R.string.ok, click = {
                        it.dismiss()
                    })
                    onDismiss {
                        navController.navigateUp()
                    }
                }
                return@observe
            }

            val entity = resource.data
            val content = entity?.content?.target?.text

            if (viewModel.offlineModeManager.isEnabled() && content == null) {
                MaterialDialog(context()).show {
                    title(R.string.no_offline_version_title)
                    message(R.string.no_offline_version)
                    negativeButton(android.R.string.ok, click = {
                        it.dismiss()
                    })
                    onDismiss {
                        navController.navigateUp()
                    }
                }
            } else {
                // val documentEntity = entities.first()
                restoreEditorState(entity = entity, editable = false)
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ConnectionStatus -> {
                    noConnectionSnackbar?.dismiss()

                    if (event.connected) {
                        noConnectionSnackbar?.dismiss()

                        runOnUiThread {
                            codeEditorLayout.snack(R.string.connected, Snackbar.LENGTH_SHORT)
                        }
                    } else {
                        saveEditorState()

                        if (event.throwable != null) {
                            Timber.e(event.throwable) { "Websocket error code: ${event.errorCode}" }
                            noConnectionSnackbar = codeEditorLayout.snack(
                                text = R.string.server_unavailable,
                                duration = Snackbar.LENGTH_INDEFINITE,
                                actionTitle = R.string.retry,
                                action = {
                                    viewModel.reconnectToServer()
                                })
                        } else {
                            noConnectionSnackbar = codeEditorLayout.snack(
                                text = R.string.not_connected,
                                duration = Snackbar.LENGTH_INDEFINITE,
                                actionTitle = R.string.connect,
                                action = {
                                    viewModel.reconnectToServer()
                                })
                        }
                    }
                }
                is TextChange -> {
                    handleTextChange(event.newText, event.patches)
                }
                is Error -> {
                    noConnectionSnackbar = codeEditorLayout.snack(
                        text = event.message
                            ?: event.throwable?.localizedMessage
                            ?: getString(R.string.unknown_error),
                        duration = Snackbar.LENGTH_INDEFINITE,
                        actionTitle = getString(R.string.retry),
                        action = {
                            viewModel.reconnectToServer()
                        })
                }
            }
        }

        codeEditorLayout = view.findViewById(R.id.codeEditorLayout)
        codeEditorLayout.minimapGravity = Gravity.BOTTOM or Gravity.END
        codeEditorLayout.languageRuleBook = MarkdownRuleBook()
        codeEditorLayout.codeEditorView.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {
                saveEditorState()
            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
//                val totalWidth = codeEditorLayout.contentLayout.width
//
//                val panX = engine.panX
//                val panY = engine.panY
//
//                val offsetX = engine.computeHorizontalScrollOffset()
//                val totalRangeX = engine.computeHorizontalScrollRange()
//                val percentage = engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange()
//
//                val offsetXMaybe = totalRangeX * percentage
//                val offsetFromPan = -panX
//                val test = 1
            }
        })
        codeEditorLayout.codeEditorView.selectionChangedListener = this

        // disable user input by default, it will be enabled automatically once connected to the server
        // if not disabled in preferences
        codeEditorLayout.editable = false
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setEditorText(
        text: String,
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ) {
        // we don't listen to selection changes when the text is changed via code
        // because the selection will be restored from persistence anyway
        // and the listener would override this
        val editor = codeEditorLayout.codeEditorView
        editor.selectionChangedListener = null
        editor.text = text
        selectionStart?.let {
            setEditorSelection(text.length, it, selectionEnd)
        }
        editor.selectionChangedListener = this
    }

    private fun setEditorSelection(maxIndex: Int, selectionStart: Int, selectionEnd: Int?) {
        val endIndex = selectionEnd ?: selectionStart
        codeEditorLayout.codeEditorView.codeEditText
            .setSelection(selectionStart.coerceIn(0, maxIndex), endIndex.coerceIn(0, maxIndex))
    }

    override fun onSelectionChanged(start: Int, end: Int, hasSelection: Boolean) {
        saveEditorState()
    }

    /**
     * Calculates the positioning percentages for x and y axis
     *
     * @return a point with horizontal (x) and vertical (y) positioning percentages
     */
    private fun getCurrentPositionPercentage(): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(
            engine.computeHorizontalScrollOffset()
                .toFloat() / engine.computeHorizontalScrollRange(),
            engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange()
        )
    }

    /**
     * Takes a point with percentage values and returns a point with the actual absolute coordinates
     *
     * @return a point with horizontal (x) and vertical (y) absolute, scale-independent, positioning coordinate values
     */
    private fun computeAbsolutePosition(percentage: PointF): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(
            -1 * percentage.x * (engine.computeHorizontalScrollRange() / engine.realZoom),
            -1 * percentage.y * (engine.computeVerticalScrollRange() / engine.realZoom)
        )
    }

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
        // TODO: this will probably cause problems when deleting all characters in a document
        if (viewModel.currentText.value.isNullOrEmpty()) {
            // skip if there is no text to save
            return
        }

        viewModel.currentPosition.set(
            codeEditorLayout.codeEditorView.panX,
            codeEditorLayout.codeEditorView.panY
        )
        viewModel.currentZoom = codeEditorLayout.codeEditorView.zoom

        val positioningPercentage = getCurrentPositionPercentage()

        viewModel.saveEditorState(
            selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
            panX = positioningPercentage.x,
            panY = positioningPercentage.y
        )
    }

    private val offlineModeObserver = Observer<Boolean> { enabled ->
        if (enabled) {
            viewModel.disconnect(reason = "Offline mode was activated")
            viewModel.loadTextFromPersistence()
        } else {
            viewModel.reconnectToServer()
        }
        runOnUiThread {
            activity?.invalidateOptionsMenu()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.offlineModeManager.isEnabled.observe(viewLifecycleOwner, offlineModeObserver)
    }

    override fun onPause() {
        super.onPause()
        viewModel.offlineModeManager.isEnabled.removeObserver(offlineModeObserver)
    }

    //    @OnLifecycleEvent(Lifecycle.Event.ON_START)
//    protected fun onLifeCycleStart() {
//        offlineModeManager.isEnabled.observe(viewLifecycleOwner, offlineModeObserver)
//    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    protected fun onLifeCyclePause() {
        saveEditorState()
//        offlineModeManager.isEnabled.removeObserver(offlineModeObserver)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    protected fun onLifeCycleStop() {
        viewModel.disconnect(reason = "Editor was closed")
    }

}