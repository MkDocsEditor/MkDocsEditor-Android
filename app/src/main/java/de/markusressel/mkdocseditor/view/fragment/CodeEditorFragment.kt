package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.jakewharton.rxbinding2.widget.RxTextView
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.otaliastudios.zoom.ZoomEngine
import com.trello.rxlifecycle2.LifecycleProvider
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.snack
import de.markusressel.commons.android.material.toast
import de.markusressel.commons.logging.prettyPrint
import de.markusressel.kodeeditor.library.view.CodeEditorLayout
import de.markusressel.kodehighlighter.language.markdown.MarkdownSyntaxHighlighter
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.databinding.FragmentEditorBinding
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.websocket.DocumentSyncManager
import de.markusressel.mkdocsrestclient.websocket.diff.diff_match_patch
import io.objectbox.kotlin.query
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Created by Markus on 07.01.2018.
 */
class CodeEditorFragment : DaggerSupportFragmentBase() {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

    @Inject
    lateinit var preferencesHolder: KutePreferencesHolder

    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager

    @Inject
    lateinit var chromeCustomTabManager: ChromeCustomTabManager

    @Inject
    lateinit var networkManager: NetworkManager

    private lateinit var codeEditorLayout: CodeEditorLayout

    private val safeArgs: CodeEditorFragmentArgs by lazy {
        CodeEditorFragmentArgs.fromBundle(arguments!!)
    }

    private val documentId: String
        get() {
            return safeArgs.documentId
        }

    private var noConnectionSnackbar: Snackbar? = null

    private val viewModel: CodeEditorViewModel by lazy { ViewModelProviders.of(this).get(CodeEditorViewModel::class.java) }

    private var currentText: String? by savedInstanceState()

    private var currentPosition by savedInstanceState(PointF())
    private var currentZoom: Float by savedInstanceState(1F)

    private var initialTextLoaded = false

    private lateinit var syncManager: DocumentSyncManager

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            menu?.findItem(R.id.refresh)?.apply {
                val refreshIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                icon = refreshIcon
            }

            // set open in browser icon
            menu?.findItem(R.id.open_in_browser)?.apply {
                val openInBrowserIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
                icon = openInBrowserIcon
                if (preferencesHolder.webUriPreference.persistedValue.isBlank()) {
                    isVisible = false
                    isEnabled = false
                }

            }
        }, onOptionsMenuItemClicked = {
            val webBaseUri = preferencesHolder
                    .webUriPreference
                    .persistedValue

            when {
                it.itemId == R.id.open_in_browser -> {
                    val documentEntity = documentPersistenceManager
                            .standardOperation()
                            .query {
                                equal(DocumentEntity_.id, documentId)
                            }.findUnique()

                    documentEntity?.let { document ->
                        val host = preferencesHolder
                                .webUriPreference.persistedValue

                        val pagePath = when {
                            document.url == "index/" -> ""
                            else -> document.url // this value is already url encoded
                        }

                        val url = "$host/$pagePath"
                        chromeCustomTabManager.openChromeCustomTab(url)
                    }

                    true
                }
                webBaseUri.isBlank() -> false
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super
                .initComponents(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        val binding: FragmentEditorBinding = DataBindingUtil.inflate(layoutInflater, layoutRes, container, false)
        viewModel.getEntity(documentPersistenceManager, documentId).observe(this, Observer<List<DocumentEntity>> {
            val entity = it.first()
            viewModel.documentId.value = entity.id
        })

        binding.let {
            it.setLifecycleOwner(this)
            it.viewModel = viewModel
        }

        return binding
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val restApiHost = preferencesHolder
                .restConnectionUriPreference
                .persistedValue

        syncManager = DocumentSyncManager(
                documentId = documentId,
                url = "ws://$restApiHost/document/$documentId/ws",
                basicAuthConfig = BasicAuthConfig(
                        preferencesHolder.basicAuthUserPreference.persistedValue,
                        preferencesHolder.basicAuthPasswordPreference.persistedValue),
                onConnectionStatusChanged = ::handleConnectionStatusChange,
                onInitialText = {
                    runOnUiThread {
                        restoreEditorState(text = it, editable = true)
                        loadingComponent.showContent()
                    }
                }, onTextChanged = ::onTextChanged)
    }

    private fun handleConnectionStatusChange(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        if (connected) {
            runOnUiThread {
                codeEditorLayout.snack(R.string.connected, LENGTH_SHORT)
            }
        } else {
            throwable?.let {
                // try to load from persistence
                loadTextFromPersistence()
                noConnectionSnackbar = codeEditorLayout.snack(
                        text = R.string.server_unavailable,
                        duration = LENGTH_INDEFINITE,
                        actionTitle = R.string.retry,
                        action = {
                            reconnectToServer()
                        })
                Timber.e(throwable) { "Websocket error code: $errorCode" }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeEditorLayout = view.findViewById(R.id.codeEditorView)
        codeEditorLayout.syntaxHighlighter = MarkdownSyntaxHighlighter()
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

        // disable user input by default, it will be enabled automatically once connected to the server
        codeEditorLayout.editable = false

        networkManager.connectionStatus.observe(viewLifecycleOwner,
                Observer { type ->

                })

        RxTextView
                .textChanges(codeEditorLayout.codeEditorView.codeEditText)
                .skipInitialValue()
                .debounce(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
                .subscribeBy(onNext = {
                    sendPatchIfChanged(it)
                }, onError = {
                    context?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })
    }

    /**
     * Restores the editor state
     *
     * @param text the new text to use, or null
     * @param editable when true the editor is editable, otherwise it is not
     */
    private fun restoreEditorState(text: String? = null, editable: Boolean) {
        val entity = getCachedEditorState()
        if (entity != null) {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                entity.text = text
                documentContentPersistenceManager.insertOrUpdate(
                        documentId = documentId, text = text)
                currentText = text
            } else {
                // restore values from cache
                currentText = entity.text
            }
            currentZoom = entity.zoomLevel
        } else {
            currentText = text
        }

        codeEditorLayout.text = currentText ?: ""
        codeEditorLayout.editable = editable
        entity?.let {
            codeEditorLayout.codeEditorView.codeEditText.setSelection(entity.selection.coerceIn(0, currentText!!.length))
            codeEditorLayout.post {
                // zoom to last saved state
                val absolutePosition = computeAbsolutePosition(PointF(entity.panX, entity.panY))
                currentPosition.set(absolutePosition.x, absolutePosition.y)

                codeEditorLayout.codeEditorView.moveTo(currentZoom, absolutePosition.x, absolutePosition.y, true)
            }
        }

        initialTextLoaded = true
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    private fun loadTextFromPersistence() {
        val entity = getCachedEditorState()

        if (entity != null) {
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

        loadingComponent.showContent(animated = true)
    }

    /**
     * Returns the cache entity for this editor and document
     */
    private fun getCachedEditorState(): DocumentContentEntity? {
        return documentContentPersistenceManager.standardOperation().query {
            equal(DocumentContentEntity_.documentId, documentId)
        }.findUnique()
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    private fun reconnectToServer() {
        loadingComponent.showLoading()
        if (syncManager.isConnected()) {
            disconnect(reason = "Editor want's to refresh connection")
            syncManager.connect()
        } else {
            syncManager.connect()
        }
    }

    @Synchronized
    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        runOnUiThread {
            val oldSelection = codeEditorLayout.codeEditorView.codeEditText.selectionStart
            currentText = newText

            // set new cursor position
            val newSelection = calculateNewSelectionIndex(oldSelection, patches)
                    .coerceIn(0, currentText?.length)

            codeEditorLayout.text = currentText ?: ""
            codeEditorLayout.codeEditorView.codeEditText.setSelection(newSelection)

            saveEditorState()
        }
    }

    /**
     * Saves the current editor state in a persistent cache
     */
    private fun saveEditorState() {
        if (currentText == null) {
            // skip if there is no text to save
            return
        }

        currentPosition.set(codeEditorLayout.codeEditorView.panX, codeEditorLayout.codeEditorView.panY)
        currentZoom = codeEditorLayout.codeEditorView.zoom

        val positioningPercentage = getCurrentPositionPercentage()

        documentContentPersistenceManager.insertOrUpdate(
                documentId = documentId,
                text = currentText,
                selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
                zoomLevel = currentZoom,
                panX = positioningPercentage.x,
                panY = positioningPercentage.y
        )
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
        patches.forEach {
            currentIndex = it.start1

            it.diffs.forEach { diff ->
                when (diff.operation) {
                    diff_match_patch.Operation.DELETE -> {
                        if (currentIndex < newSelection) {
                            newSelection -= diff.text.length
                        }
                    }
                    diff_match_patch.Operation.INSERT -> {
                        if (currentIndex < newSelection) {
                            newSelection += diff.text.length
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent.onCreateView(inflater, parent, savedInstanceState)
    }

    @Synchronized
    private fun sendPatchIfChanged(text: CharSequence) {
        if (currentText!!.contentEquals(text)) {
            Timber.e { "TEXT IST GLEICH" }
            return
        }

        val newText = text.toString()

        // TODO: only send patch if the change is coming from user input
        syncManager.sendPatch(currentText!!, newText)
        currentText = newText
    }

    private val offlineModeObserver = Observer<Boolean> { enabled ->
        viewModel.offlineModeEnabled.value = enabled
        if (enabled) {
            disconnect(reason = "Offline mode was activated")
            loadTextFromPersistence()
        } else {
            reconnectToServer()
        }
    }

    override fun onStart() {
        super.onStart()

        offlineModeManager.isEnabled.observe(viewLifecycleOwner, offlineModeObserver)
    }

    override fun onPause() {
        super.onPause()

        saveEditorState()
        offlineModeManager.isEnabled.removeObserver(offlineModeObserver)
    }

    override fun onStop() {
        disconnect(reason = "Editor was closed")

        super.onStop()
    }

    private fun disconnect(reason: String) {
        noConnectionSnackbar?.dismiss()
        codeEditorLayout.editable = false
        syncManager.disconnect(1000, reason)
    }

}