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
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.ConnectionStatus
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.Error
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.InitialText
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.OpenWebView
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.TextChange
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    val preferencesHolder: KutePreferencesHolder,
    val networkManager: NetworkManager,
    val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal val events = MutableLiveData<CodeEditorEvent>()

    //val documentId = savedStateHandle.getStateFlow<String?>("documentId", null)
    val documentId = MutableStateFlow<String?>(null)

    private val documentEntityFlow = documentId
        .filterNotNull()
        .mapLatest { documentId ->
            _uiState.update { old ->
                old.copy(
                    loading = true
                )
            }
            val result = dataRepository.getDocument(documentId)
            _uiState.update { old ->
                old.copy(
                    loading = false
                )
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentResource: MutableStateFlow<Resource<DocumentEntity?>?> =
        MutableStateFlow(null)

    private val connectionStatus = MutableStateFlow<ConnectionStatus?>(null)

    /**
     * Indicates whether the edit mode can be activated or not
     */
    val editable = combine(
        offlineModeManager.isEnabled,
        connectionStatus
    ) { offlineModeEnabled, connectionStatus ->
        offlineModeEnabled.not() && (connectionStatus?.connected ?: false)
    }

    val offlineModeBannerVisibility = offlineModeManager.isEnabled.mapLatest {
        when (it) {
            true -> View.VISIBLE
            else -> View.GONE
        }
    }.asLiveData()

    val currentPosition = PointF()
    val currentZoom = MutableLiveData(1F)

    private var documentSyncManager: DocumentSyncManager? = null

    init {
        launch {
            documentId.collect { documentId ->
                _uiState.update { old ->
                    old.copy(documentId = documentId)
                }
                documentSyncManager?.disconnect(
                    code = 1000,
                    reason = when (documentId) {
                        null -> "Document closed."
                        else -> "Document ID changed."
                    }
                )

                if (documentId == null) {
                    documentSyncManager = null
                } else {
                    val currentBackend =
                        requireNotNull(getCurrentBackendConfigUseCase().value)

                    documentSyncManager =
                        DocumentSyncManager(
                            hostname = currentBackend.serverConfig.domain,
                            port = currentBackend.serverConfig.port,
                            ssl = currentBackend.serverConfig.useSsl,
                            basicAuthConfig = BasicAuthConfig(
                                username = currentBackend.authConfig.username,
                                password = currentBackend.authConfig.password
                            ),
                            documentId = documentId,
                            currentText = {
                                uiState.value.text?.toString() ?: ""
                            },
                            onConnectionStatusChanged = { connected, errorCode, throwable ->
                                runOnUiThread {
                                    val status = ConnectionStatus(connected, errorCode, throwable)
                                    connectionStatus.value = status
                                    events.value = status
                                }
                            },
                            onInitialText = { initialText ->
                                _uiState.update { old ->
                                    old.copy(
                                        loading = false,
                                        text = AnnotatedString(initialText),
                                        selection = TextRange.Zero,
                                    )
                                }

                                // when an entity exists and a new text is given update the entity
                                this@CodeEditorViewModel.documentId.value?.let { documentId ->
                                    updateDocumentContentInCache(
                                        documentId = documentId,
                                        text = initialText
                                    )
                                }

                                events.value = InitialText(initialText)

                                // launch coroutine to continuously watch for changes
                                watchTextChanges()
                            },
                            onTextChanged = ::onTextChanged,
                            readOnly = uiState.value.editModeActive.not()
                        )
                }
            }
        }


        launch {
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

        launch {
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

        launch {
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

        launch {
            var job: Job? = null

            documentEntityFlow.collectLatest { entityFlow ->
                job?.cancel()
                job = launch {
                    entityFlow?.collectLatest { resource ->
                        currentResource.value = resource
                        when (resource) {
                            is Success -> {
                                //if (offlineModeManager.isEnabled().not()) {
                                reconnectToServer()
                                //}
                            }

                            is Resource.Loading -> {}
                            is Resource.Error -> Timber.e(resource.error)
                            else -> {
                                Timber.d { "$resource" }
                            }
                        }
                    }
                }
            }
        }

        launch {
            uiState.map { it.editModeActive }.distinctUntilChanged().collectLatest {
                documentSyncManager?.readOnly = it.not()
            }
        }

        events.observeForever { event ->
            when (event) {
                is ConnectionStatus -> {
                    _uiState.update { old ->
                        old.copy(loading = false)
                    }
                }

                is Error -> _uiState.update { old ->
                    old.copy(loading = false)
                }

                is InitialText -> _uiState.update { old ->
                    old.copy(loading = false)
                }

                else -> {}
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.BackPressed -> onClose()
            }
        }
    }

    /**
     *
     */
    fun loadDocument(documentId: String?) {
        this.documentId.value = documentId
    }

    private fun watchTextChanges() {
        val syncInterval = preferencesHolder.codeEditorSyncIntervalPreference.persistedValue.value

        launch {
            try {
                try {
                    while (documentSyncManager?.isConnected == true) {
                        documentSyncManager?.sync()
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
        _uiState.update { old ->
            old.copy(loading = false)
        }
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
        _uiState.update { old ->
            old.copy(loading = true)
        }
        if (documentSyncManager?.isConnected == true) {
            documentSyncManager?.disconnect(1000, reason = "Editor want's to refresh connection")
        }
        documentSyncManager?.connect()
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

        documentSyncManager?.disconnect(1000, reason)
        events.value = ConnectionStatus(connected = false, throwable = throwable)
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) =
        launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }


    fun saveEditorState(selection: Int, panX: Float, panY: Float) = launch {
        dataRepository.saveEditorState(
            documentId.value!!,
            uiState.value.text?.toString(),
            selection,
            currentZoom.value!!,
            panX,
            panY
        )
    }

    private suspend fun onOpenInBrowserClicked(): Boolean {
        val backendConfig = requireNotNull(getCurrentBackendConfigUseCase().value)
        val webBaseUri = backendConfig.serverConfig.webBaseUri
        if (webBaseUri.isBlank()) {
            return false
        }

        currentResource.value?.data?.let { document ->
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
        return currentResource.value?.data?.content?.target?.text != null
    }

    fun onClose() {
        documentSyncManager?.disconnect(1000, "Closed")
    }

}

sealed class UiEvent {
    data object BackPressed : UiEvent()
}