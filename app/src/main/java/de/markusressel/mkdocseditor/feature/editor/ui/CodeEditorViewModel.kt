package de.markusressel.mkdocseditor.feature.editor.ui

import android.net.Uri
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocseditor.feature.editor.domain.TextPositions
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.domain.EditorFiles
import de.markusressel.mkdocseditor.feature.editor.domain.EditorResources
import de.markusressel.mkdocseditor.feature.editor.domain.ExternalLinkOpener
import de.markusressel.mkdocseditor.feature.editor.domain.WikiTreeHolder
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinkTarget
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinks
import de.markusressel.mkdocseditor.feature.editor.domain.PageReferencesLoader
import de.markusressel.mkdocseditor.feature.editor.domain.UploadFilesUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.withFixedReference
import de.markusressel.mkdocseditor.feature.editor.domain.FormatEdit
import de.markusressel.mkdocseditor.feature.editor.domain.MarkdownFormatting
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import android.content.Context
import android.os.SystemClock
import org.mobilenativefoundation.store.store5.impl.extensions.get
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.Resource.Success
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.EditorFieldValue
import de.markusressel.mkdocsrestclient.sync.automerge.EditorTextBinding
import de.markusressel.mkdocsrestclient.sync.automerge.LocalCopyRedirects
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val authRepository: AuthRepository,
    private val preferencesHolder: KutePreferencesHolder,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val openDocumentInBrowserUseCase: OpenDocumentInBrowserUseCase,
    private val restClient: IMkDocsRestClient,
    private val uploadFilesUseCase: UploadFilesUseCase,
    private val pageReferencesLoader: PageReferencesLoader,
    private val editorFiles: EditorFiles,
    private val localDocumentStore: LocalDocumentStore,
    private val localCopyRedirects: LocalCopyRedirects,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
    private val externalLinkOpener: ExternalLinkOpener,
    private val wikiTreeHolder: WikiTreeHolder,
    private val editorResources: EditorResources,
    private val eventBusManager: EventBusManager,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _navigationEvents = Channel<NavigationRequest>(Channel.BUFFERED)
    internal val navigationEvents = _navigationEvents.receiveAsFlow()

    /** the backend of the open document */
    private var currentBackendId: Long? = null

    /** the document could not be loaded from the server, until the server is back */
    private var documentLoadFailed = false

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

    /** the wiki tree, for the link and image pickers */
    internal val pickerTree = wikiTreeHolder.tree

    /** the text of other documents, for their headings in the link picker */
    internal val documentTextLoader = editorResources.documentTextLoader

    /** small images of resources for the pickers */
    internal val thumbnailLoader = editorResources.thumbnailLoader

    /** content URIs for photos taken from the image picker */
    internal val photoUriFactory = editorFiles.photoUriFactory

    /** loads the images of the preview */
    val previewResourceLoader = editorResources.previewResourceLoader { wikiTreeHolder.tree.value }

    private var automergeDocumentSyncManager: AutomergeDocumentSyncManager? = null

    /** connects the text field to the document, see [EditorTextBinding] */
    private var editorBinding = EditorTextBinding(NoDocument, SystemClock::elapsedRealtime)

    /** the other users, resolved against the text of the document */
    private var documentPeers: List<RemotePeer> = emptyList()

    private var editorFlushJob: Job? = null

    private var editorFocused = false

    // structural equality (the default): an unchanged field does not recompose
    private val _editorField = mutableStateOf(EditorField())

    /**
     * What the text field shows. This is snapshot state and not part of [uiState]: the field
     * must get a value in the same frame it was set, a flow would deliver it a frame later.
     */
    internal val editorField: State<EditorField> = _editorField

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
                        // the toggle that opens the edit mode, once editing is possible
                        fabConfig = old.fabConfig.copy(right = editModeFabs(editable, old.editModeActive)),
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
                    isOfflineModeEnabledFlowUseCase().value.not()
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
                updateOfflineBanner()

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
        }    }

    private suspend fun initializeEditorWithDocument(documentId: String?) {
        if (documentId != null && automergeDocumentSyncManager?.documentId == documentId) {
            // the open document moved: the manager already follows its new id
            _uiState.update { old -> old.copy(documentId = documentId) }
            launch { loadWikiTree(documentId) }
            loadDocumentResource(documentId, connect = false)
            return
        }
        if (documentId != null && !isOfflineModeEnabledFlowUseCase().value) {
            if (withContext(Dispatchers.IO) { !localDocumentStore.contains(documentId) }) {
                // a copy held under an old id of this document (moved while the app was
                // offline) must be re-keyed before the copy is loaded, or its edits would not
                // show up
                val currentId = withTimeoutOrNull(REDIRECT_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { localCopyRedirects.beforeOpening(documentId) }
                }
                if (currentId != null && currentId != documentId) {
                    this.documentId.value = currentId
                    return
                }
            } else {
                // the copy is here; other moved copies are re-keyed meanwhile (sessions merge
                // what lands under their id, see DocumentSyncSession.persist)
                launch(Dispatchers.IO) { localCopyRedirects.followAll() }
            }
        }
        _uiState.update { old ->
            old.copy(
                documentId = documentId,
                loading = documentId != null && !isOfflineModeEnabledFlowUseCase().value,
            )
        }
        if (documentId != null) launch { loadWikiTree(documentId) }
        automergeDocumentSyncManager?.shutdown()
        // nothing may reach the old document while the next one loads
        automergeDocumentSyncManager = null
        bindEditorTo(NoDocument)
        localCopyAvailable.value = false
        documentLoadFailed = false
        updateOfflineBanner()

        when (documentId) {
            null -> {}

            else -> {
                val currentBackend = getCurrentBackendConfigUseCase()
                val serverConfig = currentBackend?.serverConfig
                if (serverConfig == null) {
                    // Android restored the editor, but the backend is gone (removed by the user)
                    _navigationEvents.send(NavigationRequest.Close)
                    return
                }
                currentBackendId = currentBackend.id

                // reads and parses the local copy, which takes long for a big document
                val manager = withContext(Dispatchers.IO) {
                    createAutomergeSyncManager(documentId, serverConfig, currentBackend.id)
                }
                automergeDocumentSyncManager = manager
                // show the local copy right away, it may hold edits made offline
                bindEditorTo(manager)
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
                    updateOfflineBanner()
                    // without a local copy the text is still on its way. A failed connection
                    // ends the loading too: it keeps retrying, and the snackbar says so.
                    _uiState.update { old -> old.copy(loading = connected && !localCopyAvailable.value) }
                    if (connected && documentLoadFailed) reloadAfterServerIsBack()
                }

                override fun onTextReplaced(text: String) {
                    editorBinding.onDocumentChanged()
                    syncEditorState()
                    localCopyAvailable.value = true
                    _uiState.update { old -> old.copy(loading = false) }

                    // keep the old offline cache up to date
                    updateDocumentContentInCache(
                        documentId = automergeDocumentSyncManager?.documentId ?: documentId,
                        text = text
                    )
                }

                override fun onRemoteChange(text: String, splices: List<TextSplice>) {
                    editorBinding.onDocumentChanged(splices)
                    syncEditorState()
                    saveEditorState()
                }

                override fun onReadOnlyChanged(readOnly: Boolean) {
                    // re-evaluate editable
                    connectionStatus.value = connectionStatus.value?.copy()
                    if (readOnly) disableEditMode()
                }

                override fun onPeersChanged(peers: List<RemotePeer>) {
                    documentPeers = peers
                    syncEditorState()
                }

                override fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {
                    // the copy moved with the document; follow it without reconnecting the editor
                    this@CodeEditorViewModel.documentId.value = newDocumentId
                }

                override fun onDocumentDeleted() {
                    // the local copy (and any unsynced edits) stays available
                    _uiState.update { old -> old.copy(isDocumentDeletedOnServer = true) }
                }
            },
        )
    }

    /** loads the wiki tree, so the preview can resolve links relative to the folder of the document */
    private suspend fun loadWikiTree(documentId: String) {
        wikiTreeHolder.load(documentId)?.let { showWikiTree(it, documentId) }
    }

    /** loads the wiki tree from the server, after uploads and moves */
    private suspend fun refreshWikiTree(documentId: String) {
        wikiTreeHolder.refresh()?.let { showWikiTree(it, documentId) }
    }

    private fun showWikiTree(tree: WikiTree, documentId: String) {
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
        when (val target = PreviewLinks.resolve(wikiTreeHolder.tree.value, documentId.value, href)) {
            is PreviewLinkTarget.External -> if (!externalLinkOpener.open(target.href)) {
                showMessage(context.getString(R.string.preview_link_not_found, href))
            }

            is PreviewLinkTarget.Document -> _navigationEvents.send(NavigationRequest.OpenDocument(target.documentId))
            is PreviewLinkTarget.Section -> {
                eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToSection(target.sectionId))
                _navigationEvents.send(NavigationRequest.Close)
            }

            is PreviewLinkTarget.Resource -> try {
                val file = downloadResourceUseCase(target.resourceId, target.name)
                shareFileUseCase(file)
            } catch (ex: Exception) {
                Timber.e(ex)
                showMessage(context.getString(R.string.preview_resource_failed, target.name))
            }

            PreviewLinkTarget.NotFound -> showMessage(context.getString(R.string.preview_link_not_found, href))
            null -> {}
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

    /**
     * @param connect false when the sync manager is connected already (after a move)
     */
    private suspend fun loadDocumentResource(documentId: String, connect: Boolean = true) {
        val offlineMode = isOfflineModeEnabledFlowUseCase().value
        // the text comes through the connection, it does not wait for the item tree
        if (connect && !offlineMode) reconnectToServer()

        val resource = getDocumentUseCase(documentId) { cached ->
            if (connect) showCachedDocument(cached)
        }
        currentResource.value = resource
        when (resource) {
            is Success -> {
                updateTitle(resource.data?.name ?: "")

                if (connect && offlineMode) showOfflineVersionIfPossible()
            }

            is Resource.Error -> {
                Timber.e(resource.error) { "Error loading document resource" }
                updateTitle(resource.data?.name ?: "")
                val connected = automergeDocumentSyncManager?.isConnected == true
                documentLoadFailed = !connected
                updateOfflineBanner()
                // a local copy is shown and editable already, and its edits sync later. The
                // connection keeps trying, and ends the offline state as soon as the server answers
                if (!localCopyAvailable.value && !connected) showOfflineVersionIfPossible()
            }

            else -> {
                Timber.d { "$resource" }
            }
        }
    }

    /**
     * Shows the text of the offline cache until the server sends the document. It cannot be
     * edited: without a local copy the edit mode stays off. The text of a local copy wins.
     */
    private fun showCachedDocument(cached: DocumentEntity?) {
        cached ?: return
        updateTitle(cached.name)
        cached.content.target?.text?.let { setEditorText(it) }
    }

    /**
     * The document could not be loaded when it was opened. Catches up on what is missing, the
     * sync manager already brought the text up to date.
     */
    private fun reloadAfterServerIsBack() {
        val documentId = documentId.value ?: return
        documentLoadFailed = false
        launch {
            loadWikiTree(documentId)
            loadDocumentResource(documentId, connect = false)
        }
    }

    private fun updateOfflineBanner() {
        val visible = isOfflineBannerVisible(
            offlineModeEnabled = isOfflineModeEnabledFlowUseCase().value,
            documentLoadFailed = documentLoadFailed,
            connectionStatus = connectionStatus.value,
        )
        _uiState.update { old -> old.copy(isOfflineModeBannerVisible = visible) }
    }

    /**
     * Connects the editor to another document
     */
    private fun bindEditorTo(document: EditableTextDocument) {
        editorFlushJob?.cancel()
        editorBinding = EditorTextBinding(document, SystemClock::elapsedRealtime)
        updateInputMethodActive()
        documentPeers = emptyList()
        syncEditorState()
    }

    /**
     * Shows a text that does not come from the document, as long as there is no local copy
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
        editorBinding.showDetachedText(text)
        if (selectionStart != null) setSelection(selectionStart, selectionEnd) else syncEditorState()
    }

    /**
     * Sets the selection of the editor.
     *
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setSelection(
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ) {
        if (selectionStart != null) {
            val selection = computeEditorSelection(editorBinding.documentText.length, selectionStart, selectionEnd)
            editorBinding.setSelection(selection.start, selection.end)
        }
        syncEditorState()
    }

    /**
     * Copies the state of the binding to the field and to [uiState], and takes care that
     * changes the field holds back get shown. Everything but the field uses the text of the
     * document, and its selection.
     */
    private fun syncEditorState() {
        val binding = editorBinding
        updateInputMethodActive()
        val value = binding.value
        val peers = documentPeers.map { peer ->
            peer.copy(
                selections = peer.selections.map {
                    it.copy(anchor = binding.toFieldIndex(it.anchor), head = binding.toFieldIndex(it.head))
                }
            )
        }
        _editorField.value = EditorField(value.toTextFieldValue(), binding.valueId, peers)
        val text = binding.documentText
        val selection = TextRange(binding.documentSelectionStart, binding.documentSelectionEnd)
        _uiState.update { old ->
            old.copy(
                text = if (old.text?.text == text) old.text else AnnotatedString(text),
                selection = selection,
                remotePeers = documentPeers,
            )
        }
        scheduleEditorFlush()
    }

    private fun scheduleEditorFlush() {
        val delayMs = editorBinding.holdDelay()
        editorFlushJob?.cancel()
        if (delayMs == null) return
        editorFlushJob = launch {
            delay(delayMs)
            editorBinding.flush()
            syncEditorState()
        }
    }

    /**
     * The field took the value with this id, see [EditorTextBinding.onValueShown]
     */
    internal fun onEditorValueShown(id: Long) {
        editorBinding.onValueShown(id)
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
        setEditorText(cachedContent)
        _uiState.update { old ->
            old.copy(
                title = currentResource.value?.data?.name ?: "",
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
                is UiEvent.InsertLinkClicked -> showPicker(DialogState.LinkPicker(selectedText()), FormatAction.Link)
                is UiEvent.InsertImageClicked -> showPicker(DialogState.ResourcePicker(selectedText(), imagesOnly = true), FormatAction.Image)
                is UiEvent.InsertFileReferenceClicked -> showPicker(DialogState.ResourcePicker(selectedText(), imagesOnly = false), null)
                is UiEvent.InsertMarkdown -> {
                    dismissCurrentDialog()
                    insertAtSelection(event.markdown)
                }

                is UiEvent.UploadFiles -> uploadFiles(event.sectionId, event.uris, event.altText)

                is UiEvent.PreviewLinkClicked -> onPreviewLinkClicked(event.href)

                is UiEvent.ReferenceClicked -> {
                    dismissCurrentDialog()
                    selectReference(event.reference)
                }
                is UiEvent.ReferenceFixClicked -> fixReference(event.reference)
                is UiEvent.OpenDocumentClicked -> {
                    dismissCurrentDialog()
                    _navigationEvents.send(NavigationRequest.OpenDocument(event.documentId))
                }
                is UiEvent.InsertMarkdownAfterSelection -> {
                    dismissCurrentDialog()
                    val text = uiState.value.text?.toString() ?: ""
                    val end = uiState.value.selection?.max ?: text.length
                    applyEdit(MarkdownFormatting.replaceSelection(text, end, end, event.markdown))
                }
                is UiEvent.PanelUploadClicked -> showDialog(DialogState.ResourcePicker(selectedText(), imagesOnly = false, startOnUpload = true))

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
        editorBinding.applyEdit(edit.text, edit.selectionStart, edit.selectionEnd)
        syncEditorState()
        saveEditorState()
    }

    /** the selected text, which the pickers use as link text */
    private fun selectedText(): String {
        val text = uiState.value.text?.toString() ?: return ""
        val selection = uiState.value.selection ?: return ""
        return text.substring(selection.min.coerceIn(0, text.length), selection.max.coerceIn(0, text.length))
    }

    /**
     * Opens a picker. Without the wiki tree (not loaded yet, or no connection and no cache)
     * the plain Markdown of [fallback] is inserted instead.
     */
    private fun showPicker(dialog: DialogState, fallback: FormatAction?) {
        val documentId = documentId.value
        if (wikiTreeHolder.tree.value?.documentById(documentId ?: "") != null) {
            showDialog(dialog)
        } else if (fallback != null) {
            applyFormat(fallback)
        }
    }

    /** replaces the selection with Markdown from a picker */
    private fun insertAtSelection(markdown: String) {
        val text = uiState.value.text?.toString() ?: ""
        val selectionStart = uiState.value.selection?.start ?: text.length
        val selectionEnd = uiState.value.selection?.end ?: selectionStart
        applyEdit(MarkdownFormatting.replaceSelection(text, selectionStart, selectionEnd, markdown))
    }

    /** uploads files into a section, then inserts the Markdown for them */
    private suspend fun uploadFiles(sectionId: String, uris: List<Uri>, altText: String) {
        val tree = wikiTreeHolder.tree.value ?: return
        val document = tree.documentById(documentId.value ?: return) ?: return
        val section = tree.section(sectionId) ?: return
        _uiState.update { old -> old.copy(uploading = true) }
        val result = try {
            uploadFilesUseCase(tree, document, section, uris, altText)
        } finally {
            _uiState.update { old -> old.copy(uploading = false) }
        }
        result.failure?.let { showMessage(context.getString(R.string.upload_failed, it)) }
        if (result.snippets.isEmpty()) return

        dismissCurrentDialog()
        if (result.snippets.size == 1) {
            insertAtSelection(result.snippets.single())
        } else {
            val text = uiState.value.text?.toString() ?: ""
            applyEdit(MarkdownFormatting.insertSnippets(text, uiState.value.selection?.max ?: text.length, result.snippets))
        }
        // show the new files in the tree (pickers, preview)
        documentId.value?.let { refreshWikiTree(it) }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.CodeEditor) {
        when (action) {
            is TopAppBarAction.CodeEditor.TogglePreviewAction -> togglePreviewVisibility()
            is TopAppBarAction.CodeEditor.ShowInBrowserAction -> onOpenInBrowserClicked()
            is TopAppBarAction.CodeEditor.OnlineUsersAction -> Unit
            is TopAppBarAction.CodeEditor.PageReferencesAction -> showPageReferences()
        }
    }

    /** opens the references panel and loads its data */
    private suspend fun showPageReferences() {
        val documentId = documentId.value ?: return
        if (wikiTreeHolder.tree.value?.documentById(documentId) == null) {
            showMessage(context.getString(R.string.page_references_offline))
            return
        }
        showDialog(DialogState.PageReferencesPanel())
        loadPageReferences(documentId)
    }

    private suspend fun loadPageReferences(documentId: String) {
        pageReferencesLoader.load(documentId).collect { update -> updateReferencesPanel(update) }
    }

    private fun updateReferencesPanel(update: (PageReferences) -> PageReferences) {
        _uiState.update { old ->
            val dialog = old.currentDialogState
            if (dialog is DialogState.PageReferencesPanel) old.copy(currentDialogState = dialog.copy(data = update(dialog.data))) else old
        }
    }

    /** selects the destination of a reference in the editor */
    private fun selectReference(reference: ReferenceModel) {
        val text = uiState.value.text?.toString() ?: return
        val range = TextPositions.rangeOf(text, reference.line, reference.column, reference.destination)
        setSelection(range.first, range.last + 1)
    }

    /**
     * Replaces the destination of a broken reference to a moved item with the destination the
     * backend suggests, when the text there is still the old destination
     */
    private suspend fun fixReference(reference: ReferenceModel) {
        val suggested = reference.suggestedDestination ?: return
        if (!uiState.value.editModeActive) {
            showMessage(context.getString(R.string.page_references_edit_mode_needed))
            return
        }
        val text = uiState.value.text?.toString() ?: return
        val range = TextPositions.rangeOf(text, reference.line, reference.column, reference.destination)
        if (range.isEmpty()) {
            showMessage(context.getString(R.string.page_references_changed))
            return
        }
        applyEdit(MarkdownFormatting.replaceSelection(text, range.first, range.last + 1, suggested))
        updateReferencesPanel { it.withFixedReference(reference, suggested) }
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

    /**
     * The text field got or lost the focus
     */
    internal fun onEditorFocusChanged(focused: Boolean) {
        editorFocused = focused
        updateInputMethodActive()
        syncEditorState()
    }

    private fun updateInputMethodActive() {
        editorBinding.inputMethodActive = editorFocused && uiState.value.editModeActive
    }

    internal fun onUserTextInput(value: TextFieldValue) {
        editorBinding.onFieldChanged(value.toEditorFieldValue(), editable = uiState.value.editModeActive)
        syncEditorState()
        automergeDocumentSyncManager?.onLocalSelectionChanged(
            editorBinding.documentSelectionStart,
            editorBinding.documentSelectionEnd,
        )
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

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    private fun reconnectToServer() {
        _uiState.update { old ->
            old.copy(loading = automergeDocumentSyncManager != null)
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
        _uiState.update { old -> old.copy(loading = false) }
    }

    private fun updateDocumentContentInCache(documentId: String, text: String) =
        launch {
            dataRepository.updateDocumentContentInCache(documentId, text)
        }


    private fun saveEditorState() = launch {
        dataRepository.saveEditorState(
            documentId = documentId.value!!,
            text = uiState.value.text?.toString(),
            selection = uiState.value.selection?.start ?: 0,
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
                    right = editModeFabs(editable.value, editModeActive = false)
                ),
                // the bar holds the toggle back into the edit mode
                isBottomAppBarVisible = editable.value,
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
     * Called when the user wants to reconnect to the server
     * after a previous connection (attempt) has failed
     */
    private fun onRetryClicked() {
        dismissSnackbar()
        reconnectToServer()
    }

    companion object {
        /** how long opening a document waits for the redirect lookups of the local copies */
        private const val REDIRECT_TIMEOUT_MS = 5_000L

        /** the toggle between reading and editing; there is none while editing is impossible */
        internal fun editModeFabs(editable: Boolean, editModeActive: Boolean): List<FabConfig.Fab<CodeEditorFabId>> = when {
            editModeActive -> listOf(DisableEditModeFabConfig)
            editable -> listOf(EnableEditModeFabConfig)
            else -> emptyList()
        }

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

        val fabConfig: FabConfig<CodeEditorFabId> = FabConfig(),

        val loading: Boolean = false,

        val documentId: String? = null,
        val title: String = "",

        /** the folder of the document below the wiki root (`a/b`), for paths in the preview */
        val previewSectionPath: String = "",

        /** true while files from the image picker are uploaded */
        val uploading: Boolean = false,

        /**
         * Indicates whether the CodeEditor is in "edit" mode or not
         */
        val editModeActive: Boolean = true,

        val text: AnnotatedString? = null,
        val selection: TextRange? = null,

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

        /** Markdown from a picker, replaces the selection */
        data class InsertMarkdown(val markdown: String) : UiEvent()

        data class UploadFiles(val sectionId: String, val uris: List<Uri>, val altText: String) : UiEvent()

        data class PreviewLinkClicked(val href: String) : UiEvent()

        /** selects a reference of the page in the editor */
        data class ReferenceClicked(val reference: ReferenceModel) : UiEvent()

        /** replaces a reference to a moved item with the suggested destination */
        data class ReferenceFixClicked(val reference: ReferenceModel) : UiEvent()

        data class OpenDocumentClicked(val documentId: String) : UiEvent()

        /** the upload button of the references panel */
        data object PanelUploadClicked : UiEvent()

        /**
         * Markdown from the references panel, inserted after the selection: the selection may
         * be a reference the panel selected, which must stay
         */
        data class InsertMarkdownAfterSelection(val markdown: String) : UiEvent()

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
        /** @param selection the selected text, which becomes the link text */
        data class LinkPicker(val selection: String) : DialogState

        /**
         * @param selection the selected text, which becomes the alt text
         * @param startOnUpload open the upload tab first
         */
        data class ResourcePicker(val selection: String, val imagesOnly: Boolean, val startOnUpload: Boolean = false) : DialogState

        /** the links and files of the page, and the pages that link to it */
        data class PageReferencesPanel(val data: PageReferences = PageReferences()) : DialogState
    }
}

/**
 * "Offline" means the offline mode of the settings, or that the server does not answer: the
 * document could not be loaded, and the connection is not back yet. A server that answers and
 * refuses the session is not offline, the user has to sign in again.
 */
internal fun isOfflineBannerVisible(
    offlineModeEnabled: Boolean,
    documentLoadFailed: Boolean,
    connectionStatus: CodeEditorViewModel.ConnectionStatus?,
): Boolean {
    if (offlineModeEnabled) return true
    if (!documentLoadFailed || connectionStatus?.connected == true) return false
    return connectionStatus?.isAuthenticationFailure != true
}

/**
 * The state of the text field of the editor
 *
 * @param value what the field shows
 * @param id identifies [value], see [CodeEditorViewModel.onEditorValueShown]
 * @param remotePeers the other users, resolved against the text of [value]
 */
internal data class EditorField(
    val value: TextFieldValue = TextFieldValue(),
    val id: Long = 0,
    val remotePeers: List<RemotePeer> = emptyList(),
)

/** There is no document open */
private object NoDocument : EditableTextDocument {
    override val text: String? = null
    override val version: DocumentVersion? = null
    override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
    override fun splicesSince(version: DocumentVersion, text: String) = emptyList<TextSplice>()
}

private fun EditorFieldValue.toTextFieldValue() = TextFieldValue(
    text = text,
    selection = TextRange(selectionStart, selectionEnd),
    composition = if (isComposing) TextRange(compositionStart!!, compositionEnd!!) else null,
)

private fun TextFieldValue.toEditorFieldValue() = EditorFieldValue(
    text = text,
    selectionStart = selection.start,
    selectionEnd = selection.end,
    compositionStart = composition?.min,
    compositionEnd = composition?.max,
)
