package de.markusressel.mkdocseditor.feature.editor.ui

import android.graphics.PointF
import android.view.View
import androidx.annotation.UiThread
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.*
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    val preferencesHolder: KutePreferencesHolder,
    val networkManager: NetworkManager,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal val events = MutableLiveData<CodeEditorEvent>()

    val documentId =
        MutableStateFlow<String?>(null) //  savedStateHandle.getStateFlow<String?>("documentId", null)

    val documentEntity = documentId
        .filterNotNull()
        .mapLatest { documentId ->
            dataRepository.getDocument(documentId)
        }.flattenConcat()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val connectionStatus = MutableStateFlow<ConnectionStatus?>(null)

    /**
     * Indicates whether the edit mode can be activated or not
     */
    val editable =
        offlineModeManager.isEnabled.combine(connectionStatus) { offlineModeEnabled, connectionStatus ->
            offlineModeEnabled.not() && (connectionStatus?.connected ?: false)
        }

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
        hostname = preferencesHolder.restConnectionHostnamePreference.persistedValue.value,
        port = preferencesHolder.restConnectionPortPreference.persistedValue.value.toInt(),
        ssl = preferencesHolder.restConnectionSslPreference.persistedValue.value,
        basicAuthConfig = BasicAuthConfig(
            preferencesHolder.basicAuthUserPreference.persistedValue.value,
            preferencesHolder.basicAuthPasswordPreference.persistedValue.value
        ),
        documentId = documentId.value ?: "",
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
            _uiState.value = uiState.value.copy(
                text = AnnotatedString(it),
                selection = TextRange.Zero,
            )

            // launch coroutine to continuously watch for changes
            watchTextChanges()
        },
        onTextChanged = ::onTextChanged,
        readOnly = uiState.value.editModeActive.not()
    )

    init {
        viewModelScope.launch {
            editable.collect { editable ->
                if (editable.not()) {
                    // automatically disable edit mode, if (for whatever reason)
                    // editing is currently not possible
                    if (uiState.value.editModeActive) {
                        _uiState.value = uiState.value.copy(
                            editModeActive = false
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                connectionStatus,
                editable,
                offlineModeManager.isEnabled
            ) { status, editable, offlineModeEnabled ->
                (status?.connected ?: false)
                        && editable
                        && offlineModeEnabled.not()
                        && preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value
            }.collect {
                _uiState.value = uiState.value.copy(
                    editModeActive = it
                )
            }
        }

        viewModelScope.launch {
            offlineModeManager.isEnabled.collect { enabled ->
                when {
                    enabled -> disconnect("Offline mode activated")
                    else -> {
                        if (preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value.not()) {
                            events.value = ConnectionStatus(
                                connected = connectionStatus.value?.connected ?: false
                            )
                        } else {
                            reconnectToServer()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            documentEntity.collectLatest { resource ->
                when (resource) {
                    is Success -> {
                        if (offlineModeManager.isEnabled().not()) {
                            reconnectToServer()
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            uiState.map { it.editModeActive }.distinctUntilChanged().collectLatest {
                documentSyncManager.readOnly = it.not()
            }
        }
    }

    private fun watchTextChanges() {
        val syncInterval = preferencesHolder.codeEditorSyncIntervalPreference.persistedValue.value

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

    internal fun onUserTextInput(text: AnnotatedString, selection: TextRange) {
        _uiState.value = uiState.value.copy(
            text = text,
            selection = selection,
        )
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
        _uiState.value = uiState.value.copy(
            editModeActive = false
        )

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
        val webBaseUri = preferencesHolder.webUriPreference.persistedValue.value
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
        _uiState.value = uiState.value.copy(
            editModeActive = uiState.value.editModeActive.not()
        )
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

    fun onClose() {
        documentId.value = null
    }

}
