package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.*
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
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
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.databinding.FragmentEditorBinding
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.kotlin.query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class CodeEditorFragment : DaggerSupportFragmentBase(), SelectionChangedListener {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

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
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            menu?.findItem(R.id.refresh)?.apply {
                val refreshIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
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
                    isVisible = !viewModel.offlineModeEnabled.value!! && editable
                }
            }
        }, onOptionsMenuItemClicked = {
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
        }, onPrepareOptionsMenu = { menu ->
            // set open in browser icon
            menu?.findItem(R.id.open_in_browser)?.apply {
                val openInBrowserIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
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

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        val binding: FragmentEditorBinding = DataBindingUtil.inflate(layoutInflater, layoutRes, container, false)
        viewModel.documentId.value = documentId

        binding.let {
            it.lifecycleOwner = this
            it.viewModel = viewModel
        }

        return binding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.editModeActive.observe(this) {
            runOnUiThread {
                codeEditorLayout.editable = it
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            noConnectionSnackbar?.dismiss()

            if (status.connected) {
                noConnectionSnackbar?.dismiss()

                watchTextChanges()

                runOnUiThread {
                    codeEditorLayout.snack(R.string.connected, Snackbar.LENGTH_SHORT)
                }
            } else {
                saveEditorState()

                if (status.throwable != null) {
                    Timber.e(status.throwable) { "Websocket error code: ${status.errorCode}" }
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

                viewModel.editModeActive.value = false
            }

            runOnUiThread {
                viewModel.offlineModeEnabled.value = !status.connected || viewModel.offlineModeManager.isEnabled.value!!
            }
        }

        viewModel.loading.observe(this) {
            if (it) {
                loadingComponent.showLoading()
            } else {
                loadingComponent.showContent(animated = true)
            }
        }

        viewModel.documentEntity.observe(this) { entities ->

            if (entities.isNullOrEmpty()) {

            } else {
                val documentEntity = entities.first()

                if (documentEntity != null) {
                    restoreEditorState(editable = false)
                } else {
                    MaterialDialog(context()).show {
                        title(R.string.no_offline_version)
                        negativeButton(android.R.string.ok, click = {
                            it.dismiss()
                        })
                        onDismiss {
                            navController.navigateUp()
                        }
                    }
                }
            }
        }

        viewModel.currentText.observe(this) { currentText ->
        }

        viewModel.textChange.observe(this) {
            val oldSelectionStart = codeEditorLayout.codeEditorView.codeEditText.selectionStart
            val oldSelectionEnd = codeEditorLayout.codeEditorView.codeEditText.selectionEnd
            viewModel.currentText.value = it.newText

            // set new cursor position
            val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, it.patches)
                    .coerceIn(0, viewModel.currentText.value?.length)
            val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, it.patches)
                    .coerceIn(0, viewModel.currentText.value?.length)

            setEditorText(viewModel.currentText.value ?: "", newSelectionStart, newSelectionEnd)
            saveEditorState()
        }
    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep [currentText]
     * @param editable when true the editor is editable, otherwise it is not
     */
    private fun restoreEditorState(text: String? = null, editable: Boolean) {
        val entity = viewModel.documentEntity.value?.first()
        val content = entity?.content?.target
        if (content != null) {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                content.text = text
                viewModel.documentContentPersistenceManager.insertOrUpdate(documentId = viewModel.documentId.value!!, text = text)
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

    private fun watchTextChanges() {
        val syncInterval = viewModel.preferencesHolder.codeEditorSyncIntervalPreference.persistedValue

        val syncFlow = flow {
            while (viewModel.connectionStatus.value?.connected == true) {
                emit(false)
                delay(syncInterval)
            }
        }

        // TODO: manage threads
        // .subscribeOn(Schedulers.io())
        // .observeOn(AndroidSchedulers.mainThread())
        lifecycleScope.launch {
            syncFlow.onEach {
                viewModel.documentSyncManager.sync()
            }.catch { ex ->
                Timber.e(ex)
                disconnect("Error in client sync code")
                runOnUiThread {
                    codeEditorLayout.snack(R.string.sync_error, LENGTH_SHORT)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
    private fun setEditorText(text: String, selectionStart: Int? = null, selectionEnd: Int? = null) {
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

//    /**
//     * Loads the last offline version of this document from persistence
//     */
//    @UiThread
//    private fun loadTextFromPersistence() {
//        val entity = getCachedEditorState()
//
//        if (entity != null) {
//            restoreEditorState(editable = false)
//        } else {
//            MaterialDialog(context()).show {
//                lifecycleOwner(this@CodeEditorFragment)
//                title(R.string.no_offline_version)
//                negativeButton(android.R.string.ok, click = {
//                    it.dismiss()
//                })
//                onDismiss {
//                    navController.navigateUp()
//                }
//            }
//        }
//
//        loadingComponent.showContent(animated = true)
//    }

    /**
     * Returns the cache entity for this editor and document
     */
    private fun getCachedEditorState(): DocumentContentEntity? {
        return viewModel.documentContentPersistenceManager.standardOperation().query {
            equal(DocumentContentEntity_.documentId, documentId)
        }.findUnique()
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
        return PointF(engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange(),
                engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange())
    }

    /**
     * Takes a point with percentage values and returns a point with the actual absolute coordinates
     *
     * @return a point with horizontal (x) and vertical (y) absolute, scale-independent, positioning coordinate values
     */
    private fun computeAbsolutePosition(percentage: PointF): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(-1 * percentage.x * (engine.computeHorizontalScrollRange() / engine.realZoom),
                -1 * percentage.y * (engine.computeVerticalScrollRange() / engine.realZoom)
        )
    }

    private fun calculateNewSelectionIndex(oldSelection: Int, patches: LinkedList<diff_match_patch.Patch>): Int {
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

        viewModel.currentPosition.set(codeEditorLayout.codeEditorView.panX, codeEditorLayout.codeEditorView.panY)
        viewModel.currentZoom = codeEditorLayout.codeEditorView.zoom

        val positioningPercentage = getCurrentPositionPercentage()

        viewModel.documentContentPersistenceManager.insertOrUpdate(
                documentId = viewModel.documentId.value!!,
                text = viewModel.currentText.value,
                selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
                zoomLevel = viewModel.currentZoom,
                panX = positioningPercentage.x,
                panY = positioningPercentage.y
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent.onCreateView(inflater, parent, savedInstanceState)
    }

    private val offlineModeObserver = Observer<Boolean> { enabled ->
        updateOfflineBanner()
        if (enabled) {
            disconnect(reason = "Offline mode was activated")
            viewModel.loadTextFromPersistence()
        } else {
            viewModel.reconnectToServer()
            activity?.invalidateOptionsMenu()
        }
    }

    private fun updateOfflineBanner() {
        runOnUiThread {
            viewModel.offlineModeEnabled.value = !viewModel.documentSyncManager.isConnected || viewModel.offlineModeManager.isEnabled()
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
        disconnect(reason = "Editor was closed")
    }

    private fun disconnect(reason: String = "None") {
        codeEditorLayout.editable = false
        noConnectionSnackbar?.dismiss()
        updateOfflineBanner()
        viewModel.documentSyncManager.disconnect(1000, reason)
    }

}