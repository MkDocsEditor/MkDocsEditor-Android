package de.markusressel.mkdocseditor.feature.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.ConnectionStatus
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.Error
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.InitialText
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.DocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.LinkedList
import javax.inject.Inject

@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val preferencesHolder: KutePreferencesHolder,
    private val networkManager: NetworkManager,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val openDocumentInBrowserUseCase: OpenDocumentInBrowserUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal val events = MutableLiveData<CodeEditorEvent>()

    //val documentId = savedStateHandle.getStateFlow<String?>("documentId", null)
    val documentId = MutableStateFlow<String?>(null)

    private val currentResource: MutableStateFlow<Resource<DocumentEntity?>?> =
        MutableStateFlow(null)

    private val connectionStatus = MutableStateFlow<ConnectionStatus?>(null)

    /**
     * Indicates whether the edit mode can be activated or not
     */
    val editable = combine(
        isOfflineModeEnabledFlowUseCase(),
        connectionStatus
    ) { offlineModeEnabled, connectionStatus ->
        offlineModeEnabled.not() && (connectionStatus?.connected ?: false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private var documentSyncManager: DocumentSyncManager? = null

    init {
        launch {
            documentId.collect { documentId ->
                initializeEditorWithDocument(documentId)
            }
        }


        launch {
            editable.collect { editable ->
                if (editable.not()) {
                    // automatically disable edit mode, if (for whatever reason)
                    // editing is currently not possible
                    if (uiState.value.editModeActive) {
                        disableEditMode()
                    }
                }
            }
        }

        launch {
            combine(
                connectionStatus,
                editable,
                isOfflineModeEnabledFlowUseCase()
            ) { status, editable, offlineModeEnabled ->
                (status?.connected ?: false)
                    && editable
                    && offlineModeEnabled.not()
                    && preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value
            }.collect {
                when (it) {
                    true -> enableEditMode()
                    else -> disableEditMode()
                }
            }
        }

        launch {
            connectionStatus.filterNotNull().collectLatest {
                showSnackbar(
                    when {
                        it.connected -> null
                        it.errorCode != null -> SnackbarData.ConnectionFailed
                        else -> SnackbarData.Disconnected
                    }
                )
            }
        }

        launch {
            isOfflineModeEnabledFlowUseCase().collect { enabled ->
                _uiState.update { old ->
                    old.copy(isOfflineModeBannerVisible = enabled)
                }

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

    private suspend fun initializeEditorWithDocument(documentId: String?) {
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

        when (documentId) {
            null -> documentSyncManager = null
            else -> {
                val currentBackend =
                    requireNotNull(getCurrentBackendConfigUseCase().value)
                val serverConfig = requireNotNull(currentBackend.serverConfig)
                val authConfig = requireNotNull(currentBackend.authConfig)

                documentSyncManager =
                    createDocumentSyncManager(documentId, serverConfig, authConfig)
                loadDocumentResource(documentId)
            }
        }
    }

    private fun createDocumentSyncManager(
        documentId: String,
        serverConfig: BackendServerConfig,
        authConfig: AuthConfig
    ): DocumentSyncManager {
        return DocumentSyncManager(
            hostname = serverConfig.domain,
            port = serverConfig.port,
            ssl = serverConfig.useSsl,
            basicAuthConfig = BasicAuthConfig(
                username = authConfig.username,
                password = authConfig.password
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

    private suspend fun loadDocumentResource(documentId: String) {
        val resource = getDocumentUseCase(documentId)
        currentResource.value = resource
        when (resource) {
            is Success -> {
                _uiState.update { old ->
                    old.copy(title = resource.data?.name ?: "")
                }
                if (isOfflineModeEnabledFlowUseCase().value) {
                    showOfflineVersionIfPossible()
                } else {
                    reconnectToServer()
                }
            }

            is Resource.Error -> {
                Timber.e(resource.error) { "Error loading document resource" }
                showOfflineVersionIfPossible()
            }

            else -> {
                Timber.d { "$resource" }
            }
        }
    }

    private fun showOfflineVersionIfPossible() {
        val cachedContent = getCachedContent()
        if (cachedContent != null) {
            initializeInOfflineMode(cachedContent)
        } else {
            showNoOfflineVersionAvailableDialog()
        }
    }

    private fun getCachedContent(): String? {
        return currentResource.value?.data?.content?.target?.text
    }

    private fun initializeInOfflineMode(cachedContent: String) {
        disableEditMode()
        _uiState.update { old ->
            old.copy(
                text = AnnotatedString(cachedContent),
            )
        }
    }

    private fun showNoOfflineVersionAvailableDialog() {

    }

    private fun showSnackbar(snackbar: SnackbarData?) {
        _uiState.update { old ->
            old.copy(snackbar = snackbar)
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    FAB_ID_ENABLE_EDIT_MODE -> enableEditMode()
                    FAB_ID_DISABLE_EDIT_MODE -> disableEditMode()
                    else -> {}
                }

                is UiEvent.BackPressed -> onClose()
                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
            }
        }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.CodeEditor) {
        when (action) {
            is TopAppBarAction.CodeEditor.ShowInBrowserAction -> onOpenInBrowserClicked()
        }
    }

    private fun onSnackbarAction(snackbar: SnackbarData) {
        when (snackbar) {
            SnackbarData.ConnectionFailed -> onRetryClicked()

            SnackbarData.Disconnected -> {
                dismissSnackbar()
                reconnectToServer()
            }
        }
    }

    private fun dismissSnackbar() = showSnackbar(null)

    /**
     * Sets the current document id for this CodeEditor instance
     */
    fun setCurrentDocumentId(documentId: String?) {
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

    internal fun onUserTextInput(text: AnnotatedString, selection: TextRange) {
        _uiState.update { old ->
            old.copy(
                text = text,
                selection = selection,
            )
        }
    }

    private fun onTextChanged(newText: String, patches: LinkedList<diff_match_patch.Patch>) {
        val oldSelectionStart = uiState.value.selection?.start ?: 0
        val oldSelectionEnd = uiState.value.selection?.end ?: 0

        // set new cursor position
        val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, patches)
            .coerceIn(0, newText.length)
        val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, patches)
            .coerceIn(0, newText.length)

        _uiState.update { old ->
            old.copy(
                text = AnnotatedString(newText),
                selection = TextRange(newSelectionStart, newSelectionEnd),
            )
        }
        saveEditorState()
    }

    private fun calculateNewSelectionIndex(
        oldSelection: Int,
        patches: LinkedList<diff_match_patch.Patch>
    ): Int {
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
    private fun disconnect(reason: String = "None", throwable: Throwable? = null) {
        disableEditMode()
        documentSyncManager?.disconnect(1000, reason)
        events.value = ConnectionStatus(connected = false, throwable = throwable)
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) =
        launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }


    private fun saveEditorState() = launch {
        dataRepository.saveEditorState(
            documentId.value!!,
            uiState.value.text?.toString(),
            uiState.value.selection?.start ?: 0,
            uiState.value.currentZoom,
            uiState.value.panX,
            uiState.value.panY
        )
    }

    private suspend fun onOpenInBrowserClicked(): Boolean {
        val documentId = documentId.value ?: return false
        return openDocumentInBrowserUseCase(documentId)
    }

    /**
     * Called when the user activates the edit mode
     */
    fun enableEditMode(): Boolean {
        // invert state of edit mode
        _uiState.update { old ->
            old.copy(
                editModeActive = uiState.value.editModeActive.not(),
                fabConfig = old.fabConfig.copy(
                    right = listOf(DisableEditModeFabConfig)
                )
            )
        }
        return true
    }

    private fun disableEditMode() {
        _uiState.update { old ->
            old.copy(
                editModeActive = false,
                fabConfig = old.fabConfig.copy(
                    right = when (editable.value) {
                        true -> listOf(EnableEditModeFabConfig)
                        else -> listOf()
                    }
                )
            )
        }
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
    private fun onRetryClicked() {
        dismissSnackbar()
        reconnectToServer()
    }

    fun isCachedContentAvailable(): Boolean {
        return currentResource.value?.data?.content?.target?.text != null
    }

    private fun onClose() {
        documentSyncManager?.disconnect(1000, "Closed")
    }

    companion object {
        const val FAB_ID_ENABLE_EDIT_MODE = 0
        const val FAB_ID_DISABLE_EDIT_MODE = 1

        internal val EnableEditModeFabConfig = FabConfig.Fab(
            id = FAB_ID_ENABLE_EDIT_MODE,
            description = R.string.code_editor_enable_edit_mode,
            icon = MaterialDesignIconic.Icon.gmi_edit,
        )

        internal val DisableEditModeFabConfig = FabConfig.Fab(
            id = FAB_ID_DISABLE_EDIT_MODE,
            description = R.string.code_editor_disable_edit_mode,
            icon = MaterialDesignIconic.Icon.gmi_check,
        )
    }


    data class UiState(
        val showInBrowserActionVisible: Boolean = true,

        val fabConfig: FabConfig = FabConfig(),

        val loading: Boolean = false,

        val documentId: String? = null,
        val title: String = "",

        /**
         * Indicates whether the CodeEditor is in "edit" mode or not
         */
        val editModeActive: Boolean = true,

        val text: AnnotatedString? = null,
        val selection: TextRange? = null,
        val currentZoom: Float = 1F,
        val panX: Float = 0F,
        val panY: Float = 0F,

        val isOfflineModeBannerVisible: Boolean = false,

        val snackbar: SnackbarData? = null,
    )

    sealed class UiEvent {
        data class TopAppBarActionClicked(val action: TopAppBarAction.CodeEditor) : UiEvent()

        data class ExpandableFabItemSelected(val item: FabConfig.Fab) : UiEvent()
        data class SnackbarActionClicked(val snackbar: SnackbarData) : UiEvent()

        data object BackPressed : UiEvent()
    }
}
