package de.markusressel.mkdocseditor.view.viewmodel

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.*
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.query
import java.util.*
import javax.inject.Inject


class CodeEditorViewModel @Inject constructor(
        private val state: SavedStateHandle,
        val preferencesHolder: KutePreferencesHolder) : ViewModel(), LifecycleObserver {

    val documentId = MutableLiveData<String>()

    var documentEntity: ObjectBoxLiveData<DocumentEntity>? = getEntity(documentPersistenceManager, documentId.value)

    fun getEntity(documentPersistenceManager: DocumentPersistenceManager, documentId: String): ObjectBoxLiveData<DocumentEntity> {
        if (documentEntity == null) {
            documentEntity = ObjectBoxLiveData(documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId)
            })
        }
        return documentEntity!!
    }

    val offlineModeEnabled = MutableLiveData<Boolean>()

    val offlineModeBannerVisibility = MediatorLiveData<Int>().apply {
        addSource(offlineModeEnabled) { value ->
            when (value) {
                true -> setValue(View.VISIBLE)
                else -> setValue(View.GONE)
            }
        }
    }

    val editable = MutableLiveData<Boolean>(true)

    val loading = MutableLiveData<Boolean>(true)
    val connected = MutableLiveData<Boolean>(false)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    // TODO: savedInstanceState in viewModel?
    var currentText: String? = null

    var currentPosition = PointF()
    var currentZoom = 1F

    val textChange = MutableLiveData<TextChangeEvent>(null)

    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager

    val documentSyncManager = DocumentSyncManager(
            hostname = preferencesHolder.restConnectionHostnamePreference.persistedValue,
            port = preferencesHolder.restConnectionPortPreference.persistedValue.toInt(),
            ssl = preferencesHolder.restConnectionSslPreference.persistedValue,
            basicAuthConfig = BasicAuthConfig(
                    preferencesHolder.basicAuthUserPreference.persistedValue,
                    preferencesHolder.basicAuthPasswordPreference.persistedValue),
            documentId = documentId.value!!,
            currentText = {
                codeEditorLayout.codeEditorView.codeEditText.text?.toString() ?: ""
            },
            onConnectionStatusChanged = ::handleConnectionStatusChange,
            onInitialText = {
                runOnUiThread {
                    restoreEditorState(text = it, editable = preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue)
                    loading.value = false
                }
            }, onTextChanged = ::onTextChanged)

    private fun handleConnectionStatusChange(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        this.connected.value = connected
        if (connected) {


        } else {
            editable.value = false

            saveEditorState()

            if (throwable != null) {
                Timber.e(throwable) { "Websocket error code: $errorCode" }
                noConnectionSnackbar = codeEditorLayout.snack(
                        text = R.string.server_unavailable,
                        duration = Snackbar.LENGTH_INDEFINITE,
                        actionTitle = R.string.retry,
                        action = {
                            reconnectToServer()
                        })
            } else {
                noConnectionSnackbar = codeEditorLayout.snack(
                        text = R.string.not_connected,
                        duration = Snackbar.LENGTH_INDEFINITE,
                        actionTitle = R.string.connect,
                        action = {
                            reconnectToServer()
                        })
            }
        }
    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep [currentText]
     * @param editable when true the editor is editable, otherwise it is not
     */
    private fun restoreEditorState(text: String? = null, editable: Boolean) {
        val entity = getCachedEditorState()
        if (entity != null) {
            if (text != null) {
                // when an entity exists and a new text is given update the entity
                entity.text = text
                documentContentPersistenceManager.insertOrUpdate(documentId = documentId.value!!, text = text)
                currentText = text
            } else {
                // restore values from cache
                currentText = entity.text
            }
            currentZoom = entity.zoomLevel
        } else {
            currentText = text
        }

        setEditorText(currentText ?: "")
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
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    fun loadTextFromPersistence() {
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

        loading.value = false
    }

    /**
     * Returns the cache entity for this editor and document
     */
    private fun getCachedEditorState(): DocumentContentEntity? {
        return documentContentPersistenceManager.standardOperation().query {
            equal(DocumentContentEntity_.documentId, documentId.value!!)
        }.findUnique()
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        textChange.value = TextChangeEvent(newText, patches)
        saveEditorState()
    }

    /**
     * Saves the current editor state in a persistent cache
     */
    fun saveEditorState() {
        if (currentText == null) {
            // skip if there is no text to save
            return
        }

        currentPosition.set(codeEditorLayout.codeEditorView.panX, codeEditorLayout.codeEditorView.panY)
        currentZoom = codeEditorLayout.codeEditorView.zoom

        val positioningPercentage = getCurrentPositionPercentage()

        documentContentPersistenceManager.insertOrUpdate(
                documentId = documentId.value!!,
                text = currentText,
                selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
                zoomLevel = currentZoom,
                panX = positioningPercentage.x,
                panY = positioningPercentage.y
        )
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    fun reconnectToServer() {
        loading.value = true
        if (documentSyncManager.isConnected) {
            disconnect(reason = "Editor want's to refresh connection")
            documentSyncManager.connect()
        } else {
            documentSyncManager.connect()
        }
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

    fun disconnect(reason: String = "None") {
        // TODO: update no connection snackbar using livedata
        noConnectionSnackbar?.dismiss()
        documentSyncManager.disconnect(1000, reason)
        textDisposable?.dispose()
    }

}
