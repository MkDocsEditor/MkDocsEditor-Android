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
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.domain.FormatEdit
import de.markusressel.mkdocseditor.feature.editor.domain.MarkdownFormatting
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorEvent.Error
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewFileCache
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.PreviewResourceLoader
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import org.mobilenativefoundation.store.store5.impl.extensions.get
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
    private val openDocumentInBrowserUseCase: OpenDocumentInBrowserUseCase,
    private val findParentSectionOfDocumentUseCase: FindParentSectionOfDocumentUseCase,
    private val findSectionUseCase: FindSectionUseCase,
    private val localDocumentStore: LocalDocumentStore,
    private val previewFileCache: PreviewFileCache,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
    private val chromeCustomTabManager: ChromeCustomTabManager,
    private val eventBusManager: EventBusManager,
    private val context: Context,
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

    /** the wiki tree, for links and images in the preview */
    private val wikiTree = MutableStateFlow<WikiTree?>(null)

    /**
     * Loads the images of the preview: wiki paths are resolved in the tree, the files come
     * from the server with the session, or from the offline cache
     */
    val previewResourceLoader = PreviewResourceLoader { encodedPath ->
        val tree = wikiTree.value ?: return@PreviewResourceLoader null
        val resource = (tree.resolve(tree.root, encodedPath) as? WikiItem.Resource)?.resource
            ?: return@PreviewResourceLoader null
        val bytes = runBlocking { previewFileCache.get(resource) } ?: return@PreviewResourceLoader null
        val extension = resource.name.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        WebResourceResponse(mimeType, null, bytes.inputStream())
    }

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
        _uiState.update { old ->
            old.copy(documentId = documentId)
        }
        if (documentId != null) launch { loadWikiTree(documentId) }
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

    /**
     * Loads the wiki tree (from the offline cache if the server is not reachable), so the
     * preview can resolve links and images relative to the folder of the document
     */
    private suspend fun loadWikiTree(documentId: String) {
        val root = try {
            dataRepository.sectionStore.get(ROOT_SECTION_ID)
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not load the wiki tree" }
            return
        }
        val tree = WikiTree(root)
        wikiTree.value = tree
        val section = tree.parentOfDocument(documentId)
        _uiState.update { old ->
            old.copy(previewSectionPath = section?.let { tree.pathOf(it) } ?: "")
        }
    }

    /**
     * Opens the target of a link clicked in the preview: documents in the editor, folders in
     * the file browser, resources in another app, everything else in the browser
     */
    private suspend fun onPreviewLinkClicked(href: String) {
        if (WikiTree.isExternal(href)) {
            openExternalLink(href)
            return
        }
        val documentId = documentId.value ?: return
        val tree = wikiTree.value ?: return
        val from = tree.parentOfDocument(documentId) ?: tree.root
        when (val target = tree.resolve(from, href)) {
            is WikiItem.Document -> _navigationEvents.send(NavigationRequest.OpenDocument(target.id))
            is WikiItem.Section -> {
                eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToSection(target.id))
                _navigationEvents.send(NavigationRequest.Close)
            }

            is WikiItem.Resource -> try {
                val file = downloadResourceUseCase(target.id, target.name)
                shareFileUseCase(file)
            } catch (ex: Exception) {
                Timber.e(ex)
                showMessage(context.getString(R.string.preview_resource_failed, target.name))
            }

            null -> showMessage(context.getString(R.string.preview_link_not_found, href))
        }
    }

    private fun openExternalLink(href: String) {
        try {
            if (href.startsWith("http://") || href.startsWith("https://")) {
                chromeCustomTabManager.openChromeCustomTab(href)
            } else {
                val url = if (href.startsWith("//")) "https:$href" else href
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            showMessage(context.getString(R.string.preview_link_not_found, href))
        }
    }

    /** shows a short message for a few seconds */
    private fun showMessage(text: String) {
        val message = SnackbarData.Message(text)
        showSnackbar(message)
        launch {
            delay(4000)
            if (uiState.value.snackbar == message) showSnackbar(null)
        }
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

                is UiEvent.FormatClicked -> applyFormat(event.action)
                is UiEvent.InsertLinkClicked -> applyFormat(FormatAction.Link)
                is UiEvent.InsertImageClicked -> applyFormat(FormatAction.Image)

                is UiEvent.InsertFileReferenceClicked -> showResourceSelectionDialog()
                is UiEvent.ResourceSelected -> {
                    dismissCurrentDialog()
                    insertFileReference(uiState.value.selection, event.resource)
                }

                is UiEvent.PreviewLinkClicked -> onPreviewLinkClicked(event.href)

                is UiEvent.BackPressed -> onClose()
                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
            }
        }
    }

    private fun applyFormat(action: FormatAction) {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: 0
        val selectionEnd = uiState.value.selection?.end ?: selectionStart
        applyEdit(MarkdownFormatting.apply(action, text, selectionStart, selectionEnd))
    }

    private fun applyEdit(edit: FormatEdit) {
        onTextChanged(edit.text)
        setSelection(edit.selectionStart, edit.selectionEnd)
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

            is SnackbarData.Message -> dismissSnackbar()
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

        /** the folder of the document below the wiki root (`a/b`), for paths in the preview */
        val previewSectionPath: String = "",

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

        data class FormatClicked(val action: FormatAction) : UiEvent()
        data object InsertLinkClicked : UiEvent()
        data object InsertImageClicked : UiEvent()

        data object InsertFileReferenceClicked : UiEvent()
        data class ResourceSelected(val resource: ResourceData) : UiEvent()

        data class PreviewLinkClicked(val href: String) : UiEvent()

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
        data class OpenDocument(val documentId: String) : NavigationRequest
        /** leave the editor, e.g. to show a folder in the file browser */
        data object Close : NavigationRequest
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
