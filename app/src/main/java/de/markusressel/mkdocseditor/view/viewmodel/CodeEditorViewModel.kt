package de.markusressel.mkdocseditor.view.viewmodel

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.lifecycle.Transformations.switchMap
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.network.DataRepository
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel.CodeEditorEvent.*
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
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

    val events = MutableLiveData<CodeEditorEvent>()

    val documentId = savedStateHandle.getLiveData<String>("documentId")

    val documentEntity: LiveData<Resource<DocumentEntity?>> = switchMap(documentId) { documentId ->
        dataRepository.getDocument(documentId).asLiveData()
    }

    val editable = MediatorLiveData<Boolean>().apply {
        addSource(offlineModeManager.isEnabled) { value ->
            // TODO: what about the syncManager connection status changes?

            setValue(documentSyncManager.isConnected && value)
        }
    }

    val offlineModeBannerVisibility = MediatorLiveData<Int>().apply {
        addSource(offlineModeManager.isEnabled) { value ->
            when (value) {
                true -> setValue(View.VISIBLE)
                else -> setValue(View.GONE)
            }
        }
    }

    val editModeActive = MutableLiveData(
        preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue
    )

    val loading = MutableLiveData(true)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    var currentText: MutableLiveData<String?> = MutableLiveData(null)

    var currentPosition = PointF()
    var currentZoom = 1F

    private val documentSyncManager = DocumentSyncManager(
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
        },
        onConnectionStatusChanged = { connected, errorCode, throwable ->
            events.value = ConnectionStatus(connected, errorCode, throwable)
        },
        onInitialText = {
            runOnUiThread {
                currentText.value = it
                loading.value = false
            }
        }, onTextChanged = ::onTextChanged
    )

    init {
        events.observeForever { event ->
            when (event) {
                is ConnectionStatus -> {
                    if (event.connected) {
                        watchTextChanges()
                    }
                }
                is Error -> {
                }
                is TextChange -> {
                }
            }
        }
    }

    private fun watchTextChanges() {
        val syncInterval = preferencesHolder.codeEditorSyncIntervalPreference.persistedValue

        val syncFlow = flow {
            while (documentSyncManager.isConnected) {
                emit(false)
                kotlinx.coroutines.delay(syncInterval)
            }
        }

        viewModelScope.launch {
            syncFlow.onEach {
                documentSyncManager.sync()
            }.catch { ex ->
                Timber.e(ex)
                disconnect("Error in client sync code")
                events.postValue(Error(throwable = ex))
            }
        }
    }

    /**
     * Loads the last offline version of this document from persistence
     */
    @UiThread
    fun loadTextFromPersistence() {
        loading.value = false
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        events.value = TextChange(newText, patches)
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

    fun disconnect(reason: String = "None") {

        editModeActive.value = false

        documentSyncManager.disconnect(1000, reason)
        events.value = ConnectionStatus(connected = false)
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

    sealed class CodeEditorEvent {
        data class ConnectionStatus(
            val connected: Boolean,
            val errorCode: Int? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()

        data class TextChange(
            val newText: String,
            val patches: LinkedList<diff_match_patch.Patch>
        ) : CodeEditorEvent()

        data class Error(
            val message: String? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()
    }

}
