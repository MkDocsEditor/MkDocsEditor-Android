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
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentProjectConfigUseCase
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.domain.FormatBoldUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.FormatCodeBlockUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.FormatItalicUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.FormatListBulletedUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.FormatStrikethroughUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUrlUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.Error
import de.markusressel.mkdocseditor.feature.editor.ui.compose.WebViewAction
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindParentSectionOfDocumentUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.FindSectionUseCase
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.transformIndex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: DataRepository,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val getCurrentProjectConfigUseCase: GetCurrentProjectConfigUseCase,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val authRepository: AuthRepository,
    private val preferencesHolder: KutePreferencesHolder,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val getDocumentUrlUseCase: GetDocumentUrlUseCase,
    private val openDocumentInBrowserUseCase: OpenDocumentInBrowserUseCase,
    private val findParentSectionOfDocumentUseCase: FindParentSectionOfDocumentUseCase,
    private val findSectionUseCase: FindSectionUseCase,
    private val formatCodeBlockUseCase: FormatCodeBlockUseCase,
    private val formatListBulletedUseCase: FormatListBulletedUseCase,
    private val formatStrikethroughUseCase: FormatStrikethroughUseCase,
    private val formatItalicUseCase: FormatItalicUseCase,
    private val formatBoldUseCase: FormatBoldUseCase,
    private val localDocumentStore: LocalDocumentStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal val events = MutableLiveData<CodeEditorEvent>()

    private val _navigationEvents = Channel<NavigationRequest>(Channel.BUFFERED)
    internal val navigationEvents = _navigationEvents.receiveAsFlow()

    /** the backend of the open document */
    private var currentBackendId: Long? = null

    //val documentId = savedStateHandle.getStateFlow<String?>("documentId", null)
    val documentId = MutableStateFlow<String?>(null)

    private val currentResource: MutableStateFlow<Resource<DocumentEntity?>?> =
        MutableStateFlow(null)

    private val connectionStatus = MutableStateFlow<ConnectionStatus?>(null)

    /**
     * Indicates whether a local Automerge copy of the document exists (and may be edited)
     */
    private val localCopyAvailable = MutableStateFlow(false)

    /**
     * Indicates whether the edit mode can be activated or not.
     * Editing works offline as soon as a local copy exists; the edits are synced later.
     */
    val editable = combine(
        localCopyAvailable,
        connectionStatus
    ) { localCopyAvailable, _ ->
        localCopyAvailable && automergeDocumentSyncManager?.readOnly != true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val webViewActionChannel = Channel<WebViewAction>(Channel.BUFFERED)
    val webViewActionFlow = webViewActionChannel.receiveAsFlow()

    private var automergeDocumentSyncManager: AutomergeDocumentSyncManager? = null

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

                _uiState.update { old ->
                    old.copy(
                        isBottomAppBarVisible = editable,
                    )
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
            }.distinctUntilChanged().collect {
                // only opens the edit mode; losing the connection keeps it, because
                // edits are kept in the local copy and synced on reconnect
                if (it) enableEditMode()
            }
        }

        launch {
            // sync again as soon as the user signed in again
            authRepository.sessionChanges.collect { backendId ->
                if (backendId == currentBackendId && authRepository.isSignedIn(backendId) &&
                    automergeDocumentSyncManager?.isConnected == false &&
                    uiState.value.isOfflineModeBannerVisible.not()
                ) {
                    reconnectToServer()
                }
            }
        }

        launch {
            connectionStatus.filterNotNull().collectLatest {
                val backendId = currentBackendId
                showSnackbar(
                    when {
                        it.connected -> null
                        backendId != null && it.isAuthenticationFailure -> SnackbarData.SignedOut(backendId)
                        it.errorCode != null -> SnackbarData.ConnectionFailed
                        else -> SnackbarData.Disconnected
                    }
                )
            }
        }

        launch {
            isOfflineModeEnabledFlowUseCase().collect { enabled ->
                _uiState.update { old ->
                    old.copy(
                        isOfflineModeBannerVisible = enabled,
                    )
                }

                when {
                    enabled -> disconnect("Offline mode activated")
                    else -> {
                        if (preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value.not()) {
                            connectionStatus.value = ConnectionStatus(
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
            while (true) {
                delay(1000 * 5)
                if (uiState.value.isOfflineModeBannerVisible.not() && uiState.value.webUrl != null) {
                    webViewActionChannel.send(WebViewAction.Refresh)
                }
            }
        }

        events.observeForever { event ->
            when (event) {
                is Error -> _uiState.update { old ->
                    old.copy(loading = false)
                }

                else -> {}
            }
        }
    }

    private suspend fun initializeEditorWithDocument(documentId: String?) {
        val backendConfig = getCurrentBackendConfigUseCase()
        val documentUrl = if (documentId != null && backendConfig != null) {
            val document = requireNotNull(getDocumentUseCase(documentId).data)
            getDocumentUrlUseCase(backendConfig, document)
        } else {
            null
        }
        _uiState.update { old ->
            old.copy(
                documentId = documentId,
                webUrl = documentUrl
            )
        }
        automergeDocumentSyncManager?.shutdown()
        localCopyAvailable.value = false

        when (documentId) {
            null -> automergeDocumentSyncManager = null
            else -> {
                val currentBackend = requireNotNull(getCurrentBackendConfigUseCase())
                val serverConfig = requireNotNull(currentBackend.serverConfig)
                currentBackendId = currentBackend.id

                val manager = createAutomergeSyncManager(documentId, serverConfig, currentBackend.id)
                automergeDocumentSyncManager = manager
                // show the local copy right away, it may hold edits made offline
                manager.text?.let { setEditorText(it) }
                localCopyAvailable.value = manager.hasLocalCopy

                loadDocumentResource(documentId)
            }
        }
    }

    private fun createAutomergeSyncManager(
        documentId: String,
        serverConfig: BackendServerConfig,
        backendId: Long,
    ): AutomergeDocumentSyncManager {
        return AutomergeDocumentSyncManager(
            hostname = serverConfig.domain,
            port = serverConfig.port,
            ssl = serverConfig.useSsl,
            authorizationProvider = AuthorizationProvider.bearer { authRepository.token(backendId) },
            documentId = documentId,
            store = localDocumentStore,
            listener = object : AutomergeDocumentSyncManager.Listener {
                override fun onConnectionStatusChanged(connected: Boolean, closeCode: Int?, throwable: Throwable?) {
                    val status = ConnectionStatus(connected, closeCode, throwable)
                    if (status.isAuthenticationFailure) {
                        // edits stay in the local copy and sync after the next sign-in
                        authRepository.onUnauthenticated(backendId)
                    }
                    connectionStatus.value = status
                    if (connected) {
                        _uiState.update { old -> old.copy(loading = false) }
                    }
                }

                override fun onTextReplaced(text: String) {
                    setEditorText(text)
                    localCopyAvailable.value = true
                    _uiState.update { old -> old.copy(loading = false) }

                    // keep the old offline cache up to date
                    updateDocumentContentInCache(
                        documentId = documentId,
                        text = text
                    )
                }

                override fun onRemoteChange(text: String, splices: List<TextSplice>) {
                    onRemoteTextChanged(text, splices)
                }

                override fun onReadOnlyChanged(readOnly: Boolean) {
                    // re-evaluate editable
                    connectionStatus.value = connectionStatus.value?.copy()
                    if (readOnly) disableEditMode()
                }

                override fun onPeersChanged(peers: List<RemotePeer>) {
                    _uiState.update { old -> old.copy(remotePeers = peers) }
                }

                override fun onDocumentDeleted() {
                    // the local copy (and any unsynced edits) stays available
                    _uiState.update { old -> old.copy(isDocumentDeletedOnServer = true) }
                }
            },
        )
    }

    private suspend fun loadDocumentResource(documentId: String) {
        val resource = getDocumentUseCase(documentId)
        currentResource.value = resource
        when (resource) {
            is Success -> {
                updateTitle(resource.data?.name ?: "")

                if (isOfflineModeEnabledFlowUseCase().value) {
                    showOfflineVersionIfPossible()
                } else {
                    reconnectToServer()
                }
            }

            is Resource.Error -> {
                Timber.e(resource.error) { "Error loading document resource" }
                updateTitle(resource.data?.name ?: "")
                showOfflineVersionIfPossible()
            }

            else -> {
                Timber.d { "$resource" }
            }
        }
    }

    /**
     * Restores the editor state from persistence
     *
     * @param text the new text to use, or null to keep the current text
     */
    private fun restoreEditorState(
        entity: DocumentEntity? = null,
        text: String? = null
    ) {
        val content = entity?.content?.target
        if (content != null) {
            if (text != null) {
                setEditorText(text)
            } else {
                // restore values from cache
                setEditorText(content.text, content.selection)
            }

//            val absolutePosition = computeAbsolutePosition(PointF(content.panX, content.panY))
//            codeEditorLayout.codeEditorView.moveTo(
//                content.zoomLevel,
//                absolutePosition.x,
//                absolutePosition.y,
//                animate = false
//            )
        } else {
            if (text != null) {
                setEditorText(text)
            }
        }
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setEditorText(
        text: String,
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ) {
        _uiState.update { old ->
            old.copy(
                text = AnnotatedString(text),
                selection = selectionStart?.let {
                    computeEditorSelection(text.length, it, selectionEnd)
                } ?: old.selection
            )
        }
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setSelection(
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ) {
        _uiState.update { old ->
            old.copy(
                selection = selectionStart?.let {
                    computeEditorSelection(old.text?.length ?: 0, it, selectionEnd)
                } ?: old.selection
            )
        }
    }

    private fun computeEditorSelection(
        maxIndex: Int,
        selectionStart: Int,
        selectionEnd: Int?
    ): TextRange {
        val endIndex = selectionEnd ?: selectionStart
        return TextRange(
            selectionStart.coerceIn(0, maxIndex),
            endIndex.coerceIn(0, maxIndex)
        )
    }

    private fun updateTitle(title: String) {
        _uiState.update { old ->
            old.copy(title = title)
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
        // prefer the local Automerge copy, it includes edits made offline
        return automergeDocumentSyncManager?.text
            ?: currentResource.value?.data?.content?.target?.text
    }

    private fun initializeInOfflineMode(cachedContent: String) {
        disableEditMode()
        _uiState.update { old ->
            old.copy(
                title = currentResource.value?.data?.name ?: "",
                text = AnnotatedString(cachedContent),
                isOfflineModeBannerVisible = true,
                isBottomAppBarVisible = false,
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
                is UiEvent.DismissDialog -> dismissCurrentDialog()

                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    CodeEditorFabId.EnableEditMode -> enableEditMode()
                    CodeEditorFabId.DisableEditMode -> disableEditMode()
                }

                is UiEvent.FormatBoldClicked -> formatBold()
                is UiEvent.FormatItalicClicked -> formatItalic()
                is UiEvent.FormatStrikethroughClicked -> formatStrikethrough()
                is UiEvent.FormatListBulletedClicked -> formatListBulleted()
                is UiEvent.FormatCodeBlockClicked -> formatCodeBlock()

                is UiEvent.InsertFileReferenceClicked -> showResourceSelectionDialog()
                is UiEvent.ResourceSelected -> {
                    dismissCurrentDialog()
                    insertFileReference(uiState.value.selection, event.resource)
                }

                is UiEvent.BackPressed -> onClose()
                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
            }
        }
    }

    private fun formatBold() {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart

        val (newText, newSelectionStart, newSelectionEnd) = formatBoldUseCase(
            text,
            selectionStart,
            selectionEnd
        )

        onTextChanged(newText)
        setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun formatItalic() {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart

        val (newText, newSelectionStart, newSelectionEnd) = formatItalicUseCase(
            text,
            selectionStart,
            selectionEnd
        )

        onTextChanged(newText)
        setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun formatStrikethrough() {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart

        val (newText, newSelectionStart, newSelectionEnd) = formatStrikethroughUseCase(
            text,
            selectionStart,
            selectionEnd
        )

        onTextChanged(newText)
        setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun formatCodeBlock() {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart

        val (newText, newSelectionStart, newSelectionEnd) = formatCodeBlockUseCase(text, selectionStart, selectionEnd)

        onTextChanged(newText)
        setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun formatListBulleted() {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart

        val (newText, newSelectionStart, newSelectionEnd) = formatListBulletedUseCase(
            text,
            selectionStart,
            selectionEnd
        )

        onTextChanged(newText)
        setSelection(newSelectionStart, newSelectionEnd)
    }

    private fun insertFileReference(selection: TextRange?, resource: ResourceData) {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = selection?.start ?: 0
        val selectionEnd = selection?.end ?: selectionStart

        val referenceText = "![${resource.name}](./${resource.name})"
        val newText = text.substring(0, selectionStart) +
            referenceText +
            text.substring(selectionEnd)

        onTextChanged(newText)
        // move cursor to the end of the inserted text
        setSelection(selectionStart + referenceText.length)
    }

    private suspend fun showResourceSelectionDialog() {
        val documentId = requireNotNull(documentId.value)
        val parentSectionId = requireNotNull(findParentSectionOfDocumentUseCase(documentId)).id
        val parentSection = findSectionUseCase(parentSectionId)
        showDialog(
            DialogState.SelectLinkTarget(items = parentSection.resources)
        )
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.CodeEditor) {
        when (action) {
            is TopAppBarAction.CodeEditor.TogglePreviewAction -> togglePreviewVisibility()
            is TopAppBarAction.CodeEditor.ShowInBrowserAction -> onOpenInBrowserClicked()
            is TopAppBarAction.CodeEditor.OnlineUsersAction -> Unit
        }
    }

    private fun togglePreviewVisibility() {
        _uiState.update { old ->
            old.copy(isPreviewVisible = old.isPreviewVisible.not())
        }
    }

    private fun onSnackbarAction(snackbar: SnackbarData) {
        when (snackbar) {
            SnackbarData.ConnectionFailed -> onRetryClicked()

            SnackbarData.Disconnected -> {
                dismissSnackbar()
                reconnectToServer()
            }

            is SnackbarData.SignedOut -> launch {
                _navigationEvents.send(NavigationRequest.Login(snackbar.backendId))
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

    internal fun onUserTextInput(text: AnnotatedString, selection: TextRange) {
        val oldText = uiState.value.text?.text
        _uiState.update { old ->
            old.copy(
                text = text,
                selection = selection,
            )
        }
        val manager = automergeDocumentSyncManager ?: return
        if (uiState.value.editModeActive && text.text != oldText) {
            manager.onLocalTextChanged(text.text)
        }
        manager.onLocalSelectionChanged(selection.start, selection.end)
    }

    /**
     * Called when the editor screen is no longer visible.
     * Writes the local copy to disk, so no edit is lost if the app gets killed.
     */
    internal fun onStop() {
        automergeDocumentSyncManager?.persistNow()
    }

    override fun onCleared() {
        automergeDocumentSyncManager?.shutdown()
        super.onCleared()
    }

    private fun onTextChanged(newText: String) {
        automergeDocumentSyncManager?.onLocalTextChanged(newText)

        val oldSelectionStart = uiState.value.selection?.start ?: 0
        val oldSelectionEnd = uiState.value.selection?.end ?: 0

        _uiState.update { old ->
            old.copy(
                text = AnnotatedString(newText),
                selection = TextRange(
                    oldSelectionStart.coerceIn(0, newText.length),
                    oldSelectionEnd.coerceIn(0, newText.length),
                ),
            )
        }
        saveEditorState()
    }

    /**
     * Applies remote changes to the editor and moves the selection with them,
     * so it stays on the same characters.
     */
    private fun onRemoteTextChanged(newText: String, splices: List<TextSplice>) {
        _uiState.update { old ->
            val selection = old.selection ?: TextRange.Zero
            old.copy(
                text = AnnotatedString(newText),
                selection = TextRange(
                    transformIndex(selection.start, splices).coerceIn(0, newText.length),
                    transformIndex(selection.end, splices).coerceIn(0, newText.length),
                ),
            )
        }
        saveEditorState()
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    private fun reconnectToServer() {
        _uiState.update { old ->
            old.copy(loading = true)
        }
        automergeDocumentSyncManager?.connect()
    }

    /**
     * Disconnect from the server
     *
     * @param reason a textual description of the reasoning behind the disconnect
     * @param throwable an (optional) exception that is causing the disconnect
     */
    private fun disconnect(reason: String = "None", throwable: Throwable? = null) {
        automergeDocumentSyncManager?.disconnect(reason = reason)
        connectionStatus.value = ConnectionStatus(connected = false, throwable = throwable)
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) =
        launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }


    private fun saveEditorState() = launch {
        // TODO: this will probably cause problems when deleting all characters in a document

//            val positioningPercentage = getCurrentPositionPercentage()
//
//        if (positioningPercentage.x.isNaN() || positioningPercentage.y.isNaN()) {
//            // don't save the state if it is incomplete
//            return
//        }
//
//        viewModel.saveEditorState(
//            selection = codeEditorLayout.codeEditorView.codeEditText.selectionStart,
//            panX = positioningPercentage.x,
//            panY = positioningPercentage.y
//        )

        dataRepository.saveEditorState(
            documentId = documentId.value!!,
            text = uiState.value.text?.toString(),
            selection = uiState.value.selection?.start ?: 0,
            zoomLevel = uiState.value.currentZoom,
            panX = uiState.value.panX,
            panY = uiState.value.panY
        )
    }

    private suspend fun onOpenInBrowserClicked(): Boolean {
        val documentId = documentId.value ?: return false
        return openDocumentInBrowserUseCase(documentId)
    }

    /**
     * Called when the user activates the edit mode
     */
    private fun enableEditMode(): Boolean {
        _uiState.update { old ->
            old.copy(
                editModeActive = true,
                fabConfig = old.fabConfig.copy(
                    right = listOf(DisableEditModeFabConfig)
                ),
                isBottomAppBarVisible = true,
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
                ),
                isBottomAppBarVisible = false,
            )
        }
    }

    private fun showDialog(dialogState: DialogState?) {
        _uiState.update { old ->
            old.copy(currentDialogState = dialogState)
        }
    }

    private fun dismissCurrentDialog() = showDialog(null)

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
        return getCachedContent() != null
    }

    private fun onClose() {
        automergeDocumentSyncManager?.disconnect(reason = "Closed")
    }

    companion object {
        internal val EnableEditModeFabConfig = FabConfig.Fab<CodeEditorFabId>(
            id = CodeEditorFabId.EnableEditMode,
            description = R.string.code_editor_enable_edit_mode,
            icon = MaterialDesignIconic.Icon.gmi_edit,
        )

        internal val DisableEditModeFabConfig = FabConfig.Fab<CodeEditorFabId>(
            id = CodeEditorFabId.DisableEditMode,
            description = R.string.code_editor_disable_edit_mode,
            icon = MaterialDesignIconic.Icon.gmi_check,
        )
    }


    data class UiState(
        val currentDialogState: DialogState? = null,

        val showInBrowserActionVisible: Boolean = true,

        val fabConfig: FabConfig<CodeEditorFabId> = FabConfig(),

        val loading: Boolean = false,

        val documentId: String? = null,
        val title: String = "",
        val webUrl: String? = null,

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
        val isPreviewVisible: Boolean = false,
        val isBottomAppBarVisible: Boolean = false,

        val snackbar: SnackbarData? = null,

        /**
         * Other users in this document, with their resolved cursors and selections
         */
        val remotePeers: List<RemotePeer> = emptyList(),

        /**
         * The document was deleted on the server; the local copy is still shown
         */
        val isDocumentDeletedOnServer: Boolean = false,
    )

    sealed class UiEvent {
        data object DismissDialog : UiEvent()

        data class TopAppBarActionClicked(val action: TopAppBarAction.CodeEditor) : UiEvent()

        data class ExpandableFabItemSelected(val item: FabConfig.Fab<CodeEditorFabId>) : UiEvent()
        data class SnackbarActionClicked(val snackbar: SnackbarData) : UiEvent()

        data object FormatBoldClicked : UiEvent()
        data object FormatItalicClicked : UiEvent()
        data object FormatStrikethroughClicked : UiEvent()
        data object FormatListBulletedClicked : UiEvent()
        data object FormatCodeBlockClicked : UiEvent()

        data object InsertFileReferenceClicked : UiEvent()
        data class ResourceSelected(val resource: ResourceData) : UiEvent()

        data object BackPressed : UiEvent()
    }

    data class ConnectionStatus(
        val connected: Boolean,
        val errorCode: Int? = null,
        val throwable: Throwable? = null
    ) {
        /**
         * the server refused the session: HTTP 401 on the upgrade, or close code 4001
         */
        val isAuthenticationFailure: Boolean
            get() = !connected && (errorCode == 401 || errorCode == CloseCode.UNAUTHORIZED)
    }

    sealed interface NavigationRequest {
        data class Login(val backendId: Long) : NavigationRequest
    }

    sealed class CodeEditorFabId {
        data object EnableEditMode : CodeEditorFabId()
        data object DisableEditMode : CodeEditorFabId()
    }

    sealed interface DialogState {
        data class SelectLinkTarget(
            val items: List<ResourceData> = emptyList(),
        ) : DialogState
    }
}
