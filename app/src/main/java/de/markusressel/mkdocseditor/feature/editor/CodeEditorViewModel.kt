package de.markusressel.mkdocseditor.feature.editor

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Transformations.switchMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.data.DataRepository
import de.markusressel.mkdocseditor.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.editor.CodeEditorViewModel.CodeEditorEvent.*
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    val preferencesHolder: KutePreferencesHolder,
    val networkManager: NetworkManager,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    val events = MutableLiveData<CodeEditorEvent>()

    private val documentId = savedStateHandle.getLiveData<String>("documentId")

    val documentEntity: LiveData<Resource<DocumentEntity?>> = switchMap(documentId) { documentId ->
        dataRepository.getDocument(documentId).asLiveData()
    }

    val connectionStatus = MutableStateFlow<ConnectionStatus?>(null)

    /**
     * Indicates whether the edit mode can be activated or not
     */
    val editable =
        offlineModeManager.isEnabled.combine(connectionStatus) { offlineModeEnabled, connectionStatus ->
            offlineModeEnabled.not() && (connectionStatus?.connected ?: false)
        }

    /**
     * Indicates whether the CodeEditor is in "edit" mode or not
     */
    val editModeActive = MutableLiveData(false)

    val offlineModeBannerVisibility = offlineModeManager.isEnabled.mapLatest {
        when (it) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }.asLiveData()

    val loading = MutableLiveData(true)

    // TODO: this property should not exist. only the [DocumentSyncManager] should have this.
    var currentText: MutableLiveData<String?> = MutableLiveData(null)

    val currentPosition = PointF()
    val currentZoom = MutableLiveData(1F)

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
            runOnUiThread {
                val status = ConnectionStatus(connected, errorCode, throwable)
                connectionStatus.value = status
                events.value = status
            }
        },
        onInitialText = {
            currentText.value = it
            loading.value = false

            // when an entity exists and a new text is given update the entity
            documentId.value?.let { documentId ->
                updateDocumentContentInCache(
                    documentId = documentId,
                    text = it
                )
            }

            events.value = InitialText(it)

            // launch coroutine to continuously watch for changes
            watchTextChanges()
        },
        onTextChanged = ::onTextChanged,
        readOnly = editModeActive.value?.not() ?: true
    )

    init {
        documentEntity.observeForever {
            when (it) {
                is Success -> {
                    if (offlineModeManager.isEnabled().not()) {
                        reconnectToServer()
                    }
                }
                else -> {}
            }
        }

        offlineModeManager.isEnabled.onEach { enabled ->
            when (enabled) {
                true -> disconnect("Offline mode activated")
                else -> {}
            }
        }

        editModeActive.observeForever {
            documentSyncManager.readOnly = it.not()
        }
    }

    private fun watchTextChanges() {
        val syncInterval = preferencesHolder.codeEditorSyncIntervalPreference.persistedValue

        viewModelScope.launch {
            try {
                try {
                    while (documentSyncManager.isConnected) {
                        documentSyncManager.sync()
                        delay(syncInterval)
                    }
                } catch (ex: CancellationException) {
                    Timber.d { "Stopped watching text changes" }
                    disconnect("Stopped")
                } catch (ex: Exception) {
                    Timber.e(ex)
                    disconnect(throwable = ex)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
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
    private fun reconnectToServer() {
        loading.value = true
        if (documentSyncManager.isConnected) {
            documentSyncManager.disconnect(1000, reason = "Editor want's to refresh connection")
        }
        documentSyncManager.connect()
    }

    /**
     * Disconnect from the server
     *
     * @param reason a textual description of the reasoning behind the disconnect
     * @param throwable an (optional) exception that is causing the disconnect
     */
    fun disconnect(reason: String = "None", throwable: Throwable? = null) {
        editModeActive.value = false

        documentSyncManager.disconnect(1000, reason)
        events.value = ConnectionStatus(connected = false, throwable = throwable)
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) =
        viewModelScope.launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }


    fun saveEditorState(selection: Int, panX: Float, panY: Float) = viewModelScope.launch {
        dataRepository.saveEditorState(
            documentId.value!!,
            currentText.value,
            selection,
            currentZoom.value!!,
            panX,
            panY
        )
    }

    fun onOpenInBrowserClicked(): Boolean {
        val webBaseUri = preferencesHolder.webUriPreference.persistedValue
        if (webBaseUri.isBlank()) {
            return false
        }

        documentEntity.value?.data?.let { document ->
            val pagePath = when (document.url) {
                "index/" -> ""
                else -> document.url
                // this value is already url encoded
            }

            val url = "$webBaseUri/$pagePath"
            events.value = OpenWebView(url)
        }

        return true
    }

    /**
     * Called when the user activates the edit mode
     */
    fun onEditClicked(): Boolean {
        // invert state of edit mode
        editModeActive.value = editModeActive.value != true
        return true
    }

    /**
     * Called when the user wants to connect to the server
     */
    fun onConnectClicked() {
        reconnectToServer()
    }

    /**
     * Called when the user wants to reconnect to the server
     * after a previous connection (attempt) has failed
     */
    fun onRetryClicked() {
        reconnectToServer()
    }

    fun isCachedContentAvailable(): Boolean {
        return documentEntity.value?.data?.content?.target?.text != null
    }

    sealed class CodeEditorEvent {
        data class ConnectionStatus(
            val connected: Boolean,
            val errorCode: Int? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()

        data class InitialText(val text: String) : CodeEditorEvent()

        data class TextChange(
            val newText: String,
            val patches: LinkedList<diff_match_patch.Patch>
        ) : CodeEditorEvent()

        data class OpenWebView(
            val url: String
        ) : CodeEditorEvent()

        data class Error(
            val message: String? = null,
            val throwable: Throwable? = null
        ) : CodeEditorEvent()
    }

}
