package de.markusressel.mkdocseditor.view.viewmodel

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.lifecycle.Transformations.switchMap
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.network.DataRepository
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    val dataRepository: DataRepository,
    val preferencesHolder: KutePreferencesHolder,
    val networkManager: NetworkManager,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    val documentId = savedStateHandle.getLiveData<String>("documentId")

    val documentEntity: LiveData<Resource<DocumentEntity?>> = switchMap(documentId) { documentId ->
        dataRepository.getDocument(documentId).asLiveData()
    }

    val offlineModeBannerVisibility = MediatorLiveData<Int>().apply {
        addSource(offlineModeManager.isEnabled) { value ->
            when (value) {
                true -> setValue(View.VISIBLE)
                else -> setValue(View.GONE)
            }
        }
    }

    // TODO: add offlineModeManager.isEnabled() condition
    val editable = MutableLiveData(true)
    val editModeActive =
        MutableLiveData(preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue)

    val loading = MutableLiveData(true)
    val connectionStatus: MutableLiveData<ConnectionStatusUpdate?> = MutableLiveData(null)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    // TODO: savedInstanceState in viewModel?
    var currentText: MutableLiveData<String?> = MutableLiveData(null)

    var currentPosition = PointF()
    var currentZoom = 1F

    val textChange = MutableLiveData<TextChangeEvent?>(null)

    val documentSyncManager = DocumentSyncManager(
        hostname = preferencesHolder.restConnectionHostnamePreference.persistedValue,
        port = preferencesHolder.restConnectionPortPreference.persistedValue.toInt(),
        ssl = preferencesHolder.restConnectionSslPreference.persistedValue,
        basicAuthConfig = BasicAuthConfig(
            preferencesHolder.basicAuthUserPreference.persistedValue,
            preferencesHolder.basicAuthPasswordPreference.persistedValue
        ),
        documentId = documentId.value!!,
        currentText = {
            currentText.value.orEmpty()
//                binding.codeEditorLayout.codeEditorView.codeEditText.text?.toString() ?: ""
        },
        onConnectionStatusChanged = ::handleConnectionStatusChange,
        onInitialText = {
            runOnUiThread {
                currentText.value = it
                editable.value =
                    preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue
                loading.value = false
            }
        }, onTextChanged = ::onTextChanged
    )

    data class ConnectionStatusUpdate(
        val connected: Boolean,
        val errorCode: Int?,
        val throwable: Throwable?
    )

    private fun handleConnectionStatusChange(
        connected: Boolean,
        errorCode: Int?,
        throwable: Throwable?
    ) {
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

    fun updateDocumentContentInCache(documentId: String, text: String) {
        viewModelScope.launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }
    }

    fun saveEditorState(selection: Int, panX: Float, panY: Float) {
        viewModelScope.launch {
            dataRepository.saveEditorState(
                documentId.value!!,
                currentText.value,
                selection,
                currentZoom,
                panX,
                panY
            )
        }
    }

}
