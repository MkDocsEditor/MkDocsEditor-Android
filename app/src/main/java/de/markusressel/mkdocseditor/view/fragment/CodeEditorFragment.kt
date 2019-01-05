package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.CallSuper
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.jakewharton.rxbinding2.widget.RxTextView
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.trello.rxlifecycle2.LifecycleProvider
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.snack
import de.markusressel.commons.android.material.toast
import de.markusressel.commons.logging.prettyPrint
import de.markusressel.kodeeditor.library.markdown.MarkdownSyntaxHighlighter
import de.markusressel.kodeeditor.library.view.CodeEditorView
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.event.OfflineModeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.context
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.websocket.DocumentSyncManager
import de.markusressel.mkdocsrestclient.websocket.EditRequestEntity
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

    private lateinit var codeEditorView: CodeEditorView

    private val documentId: String
        get() {
            return arguments?.getString(KEY_ID)!!
        }

    private var currentText: String? by savedInstanceState()

    private var currentXPosition by savedInstanceState(0F)
    private var currentYPosition by savedInstanceState(0F)
    private var currentZoom: Float by savedInstanceState(1F)

    private var initialTextLoaded = false

    private lateinit var syncManager: DocumentSyncManager

    private var previouslySentPatches: MutableMap<String, String> = mutableMapOf()

    @Inject
    lateinit var diffMatchPatch: diff_match_patch

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            val refreshIcon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
            menu
                    ?.findItem(R.id.refresh)
                    ?.icon = refreshIcon

            // set open in browser icon
            val openInBrowserIcon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
            menu
                    ?.findItem(R.id.open_in_browser)
                    ?.icon = openInBrowserIcon
        }, onOptionsMenuItemClicked = {
            when {
                it.itemId == R.id.open_in_browser -> {
                    val documentEntity = documentPersistenceManager
                            .standardOperation()
                            .query {
                                equal(DocumentEntity_.id, documentId)
                            }
                            .findUnique()

                    documentEntity
                            ?.let { document ->
                                val host = preferencesHolder
                                        .connectionUriPreference
                                        .persistedValue

                                chromeCustomTabManager
                                        .openChromeCustomTab("http://$host/${document.url}")
                            }


                    true
                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = preferencesHolder
                .connectionUriPreference
                .persistedValue

        syncManager = DocumentSyncManager(documentId = documentId, url = "ws://$host/document/$documentId/ws", basicAuthConfig = BasicAuthConfig(preferencesHolder.basicAuthUserPreference.persistedValue, preferencesHolder.basicAuthPasswordPreference.persistedValue), onInitialText = {
            runOnUiThread {
                codeEditorView.snack("Connected :)", LENGTH_SHORT)
                loadingComponent.showContent()
                restoreEditorFromCache(getCachedEditorState(), text = it, editable = true)
            }
        }, onPatchReceived = { editRequest ->
            processEditRequest(editRequest)
        }, onError = { code, throwable ->
            throwable?.let {
                // try to load from persistence
                loadTextFromPersistence()

                runOnUiThread {
                    codeEditorView.snack(
                            text = "No connection :(",
                            duration = LENGTH_INDEFINITE,
                            actionTitle = "Reconnect",
                            action = {
                                reconnectToServer()
                            })
                }
                Timber.e(throwable) { "Websocket error code: $code" }
            }
        })
    }

    private fun restoreEditorFromCache(entity: DocumentContentEntity? = null, text: String? = null, editable: Boolean) {
        currentText = text
        entity?.let {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                it.text = text
            }

            // restore values from cache
            if (text == null) {
                currentText = entity.text
            }
            currentZoom = entity.zoomLevel
            currentXPosition = entity.panX
            currentYPosition = entity.panY

            codeEditorView.post {
                // zoom to last saved state
                codeEditorView.moveTo(currentZoom, currentXPosition, currentYPosition, true)
            }
        }

        codeEditorView.setText(currentText ?: "")
        entity?.let { codeEditorView.editTextView.setSelection(entity.selection) }
        codeEditorView.setEditable(editable)

        initialTextLoaded = true
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super
                .onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent
                .onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        return optionsMenuComponent
                .onOptionsItemSelected(item)
    }

    /**
     * Loads the last offline version of this page from persistence
     */
    private fun loadTextFromPersistence() {
        val entity = getCachedEditorState()

        if (entity != null) {
            runOnUiThread {
                restoreEditorFromCache(entity, entity.text, editable = false)
            }
        } else {
            MaterialDialog(context()).show {
                title(R.string.no_offline_version)
                negativeButton(android.R.string.ok, click = {
                    it.dismiss()
                })
                onDismiss {
                    // TODO: somehow do this with navigation library
                    requireActivity().finish()
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
            syncManager.disconnect(1000, "Editor want's to refresh connection")
            syncManager.connect()
        } else {
            syncManager.connect()
        }
    }

    @Synchronized
    private fun processEditRequest(editRequest: EditRequestEntity) {
        if (documentId != this.documentId) {
            return
        }

        if (previouslySentPatches.containsKey(editRequest.requestId)) {
            previouslySentPatches.remove(editRequest.requestId)
            return
        }

        runOnUiThread {
            val oldSelection = codeEditorView.editTextView.selectionStart

            // parse and apply patches
            val patches: LinkedList<diff_match_patch.Patch> = diffMatchPatch.patch_fromText(editRequest.patches) as LinkedList<diff_match_patch.Patch>

            currentText = diffMatchPatch.patch_apply(patches, currentText)[0] as String

            // set new cursor position
            val newSelection = calculateNewSelectionIndex(oldSelection, patches)
                    .coerceIn(0, currentText?.length)

            codeEditorView.setText(currentText ?: "")
            codeEditorView.editTextView.setSelection(newSelection)

            doAsync { updateEditorStateCache(text = currentText) }
        }
    }

    /**
     * Saves the current text in a persistent cache
     */
    private fun updateEditorStateCache(text: String? = null) {
        val documentContentEntity = getCachedEditorState() ?: DocumentContentEntity(0, documentId)

        if (getCachedEditorState() == null && text == null) {
            // skip if there is no text to save
            return
        }

        // attach parent if necessary
        if (documentContentEntity.documentEntity.isNull) {
            val documentEntity = documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId)
            }.findUnique()
            documentContentEntity.documentEntity.target = documentEntity
        }

        documentContentEntity.text = text!!

        currentXPosition = codeEditorView.panX
        currentYPosition = codeEditorView.panY
        currentZoom = codeEditorView.zoom

        documentContentEntity.zoomLevel = currentZoom
        documentContentEntity.selection = codeEditorView.editTextView.selectionStart
        documentContentEntity.panX = currentXPosition
        documentContentEntity.panY = currentYPosition

        documentContentPersistenceManager.standardOperation().put(documentContentEntity)
    }

    private fun calculateNewSelectionIndex(oldSelection: Int, patches: LinkedList<diff_match_patch.Patch>): Int {
        var newSelection = oldSelection

        var currentIndex: Int
        // calculate how many characters have been inserted before the cursor
        patches.forEach {
            val patch = it
            currentIndex = patch.start1

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

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Bus.observe<OfflineModeChangedEvent>()
                .subscribe {
                    if (!it.enabled) {
                        reconnectToServer()
                    } else {
                        codeEditorView.setEditable(false)
                        syncManager.disconnect(1000, "Offline mode was activated")
                    }
                }
                .registerInBus(this)

        codeEditorView = view.findViewById(R.id.codeEditorView)
        codeEditorView.setSyntaxHighlighter(MarkdownSyntaxHighlighter())
        codeEditorView.mMoveWithCursorEnabled = false

        // disable user input in offline mode
        codeEditorView.setEditable(offlineModeManager.isEnabled())

        RxTextView
                .textChanges(codeEditorView.editTextView)
                .skipInitialValue()
                .debounce(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
                .subscribeBy(onNext = {
                    sendPatchIfChanged(it)
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })
    }

    @Synchronized
    private fun sendPatchIfChanged(it: CharSequence) {
        if (currentText!!.contentEquals(it)) {
            Timber.e { "TEXT IST GLEICH" }
            return
        }

        val newText = it.toString()

        // TODO: only send patch if the change is coming from user input
        val requestId = (syncManager.sendPatch(currentText!!, newText))
        previouslySentPatches[requestId] = "sent"
        currentText = newText
    }

    override fun onStart() {
        super.onStart()

        if (offlineModeManager.isEnabled()) {
            loadTextFromPersistence()
        } else {
            reconnectToServer()
        }
    }

    override fun onPause() {
        super.onPause()

        updateEditorStateCache(text = currentText)
    }

    override fun onStop() {
        syncManager.disconnect(1000, "Editor was closed")
        super.onStop()
    }

    companion object {

        private const val KEY_ID = "KEY_ID"

        fun newInstance(id: String): CodeEditorFragment {
            val fragment = CodeEditorFragment()
            val bundle = Bundle()
            bundle.putString(KEY_ID, id)
            fragment.arguments = bundle

            return fragment
        }
    }
}