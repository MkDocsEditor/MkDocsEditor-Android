package de.markusressel.mkdocseditor.view.viewmodel

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.*
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
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

    var documentEntity: ObjectBoxLiveData<DocumentEntity> = getEntity(documentPersistenceManager, documentId.value!!)

    fun getEntity(documentPersistenceManager: DocumentPersistenceManager, documentId: String): ObjectBoxLiveData<DocumentEntity> {
        return ObjectBoxLiveData(documentPersistenceManager.standardOperation().query {
            equal(DocumentEntity_.id, documentId)
        })
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

    val editable = MutableLiveData(true)

    val loading = MutableLiveData(true)
    val connectionStatus: MutableLiveData<ConnectionStatusUpdate> = MutableLiveData(null)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    // TODO: savedInstanceState in viewModel?
    var currentText: MutableLiveData<String?> = MutableLiveData(null)

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
                currentText.value.orEmpty()
//                binding.codeEditorLayout.codeEditorView.codeEditText.text?.toString() ?: ""
            },
            onConnectionStatusChanged = ::handleConnectionStatusChange,
            onInitialText = {
                runOnUiThread {
                    currentText.value = it
                    editable.value = preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue
                    loading.value = false
                }
            }, onTextChanged = ::onTextChanged)

    data class ConnectionStatusUpdate(val connected: Boolean, val errorCode: Int?, val throwable: Throwable?)

    private fun handleConnectionStatusChange(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        if (!connected) {
            editable.value = false
        }
        this.connectionStatus.value = ConnectionStatusUpdate(connected, errorCode, throwable)
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    fun loadTextFromPersistence() {
        loading.value = false
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        textChange.value = TextChangeEvent(newText, patches)
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    fun reconnectToServer() {
        loading.value = true
        if (documentSyncManager.isConnected) {
            documentSyncManager.disconnect(1000, reason = "Editor want's to refresh connection")
        }
        documentSyncManager.connect()
    }

}
