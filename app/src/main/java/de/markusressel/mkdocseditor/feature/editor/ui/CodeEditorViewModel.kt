package de.markusressel.mkdocseditor.feature.editor.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.FileProvider
import de.markusressel.mkdocseditor.feature.editor.domain.PickerMarkdown
import de.markusressel.mkdocseditor.feature.editor.domain.decodeThumbnail
import de.markusressel.mkdocseditor.feature.editor.domain.shrinkImage
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.DocumentTextLoader
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PhotoUriFactory
import de.markusressel.mkdocseditor.feature.filebrowser.data.LinkPaths
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.ThumbnailLoader
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import de.markusressel.mkdocseditor.feature.editor.domain.TextPositions
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import com.github.kittinunf.fuel.core.FuelError
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import android.os.SystemClock
import android.content.Intent
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val restClient: IMkDocsRestClient,
    private val contentResolver: ContentResolver,
    private val localDocumentStore: LocalDocumentStore,
    private val localCopyRedirects: LocalCopyRedirects,
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

    /** the document could not be loaded from the server, until the server is back */
    private var documentLoadFailed = false

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

    /** the wiki tree, for the link and image pickers */
    internal val pickerTree = wikiTree.asStateFlow()

    /** the text of other documents, for their headings in the link picker */
    internal val documentTextLoader = DocumentTextLoader { documentId ->
        withContext(Dispatchers.IO) {
            restClient.getDocumentContent(documentId).fold(success = { it }, failure = { null })
                ?: localDocumentStore.load(documentId)?.let { AutomergeTextDocument.load(it.documentState).text }
        }
    }

    private val thumbnails = object : LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_SIZE) {}

    /** small images of resources for the pickers */
    internal val thumbnailLoader = ThumbnailLoader { resource ->
        val key = "${resource.id}@${resource.modtime.time}"
        thumbnails.get(key) ?: withContext(Dispatchers.IO) {
            previewFileCache.get(resource)?.let { decodeThumbnail(it, THUMBNAIL_SIZE_PX) }
        }?.also { thumbnails.put(key, it) }
    }

    /** content URIs for photos taken from the image picker */
    internal val photoUriFactory = PhotoUriFactory {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val name = "photo-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH).format(Date()) + ".jpg"
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(directory, name))
    }

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

    /** connects the text field to the document, see [EditorTextBinding] */
    private var editorBinding = EditorTextBinding(NoDocument, SystemClock::elapsedRealtime)

    /** the other users, resolved against the text of the document */
    private var documentPeers: List<RemotePeer> = emptyList()

    private var editorFlushJob: Job? = null

    private var editorFocused = false

    /** when the open document started loading, for [logOpenStep] */
    private var openStartedAt = 0L

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

    private fun logOpenStep(step: String) {
        Timber.d { "Opening document: $step after ${SystemClock.elapsedRealtime() - openStartedAt} ms" }
    }

    private suspend fun initializeEditorWithDocument(documentId: String?) {
        openStartedAt = SystemClock.elapsedRealtime()
        if (documentId != null && automergeDocumentSyncManager?.documentId == documentId) {
            // the open document moved: the manager already follows its new id
            _uiState.update { old -> old.copy(documentId = documentId) }
            launch { loadWikiTree(documentId) }
            loadDocumentResource(documentId, connect = false)
            return
        }
        if (documentId != null && !isOfflineModeEnabledFlowUseCase().value) {
            if (withContext(Dispatchers.IO) { localDocumentStore.load(documentId) } == null) {
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
            old.copy(documentId = documentId)
        }
        if (documentId != null) launch { loadWikiTree(documentId) }
        automergeDocumentSyncManager?.shutdown()
        localCopyAvailable.value = false
        documentLoadFailed = false
        updateOfflineBanner()

        when (documentId) {
            null -> {
                automergeDocumentSyncManager = null
                bindEditorTo(NoDocument)
            }

            else -> {
                val currentBackend = getCurrentBackendConfigUseCase()
                val serverConfig = currentBackend?.serverConfig
                if (serverConfig == null) {
                    // Android restored the editor, but the backend is gone (removed by the user)
                    _navigationEvents.send(NavigationRequest.Close)
                    return
                }
                currentBackendId = currentBackend.id

                val manager = createAutomergeSyncManager(documentId, serverConfig, currentBackend.id)
                logOpenStep("sync manager created")
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
                    if (connected) logOpenStep("connected")
                    connectionStatus.value = status
                    updateOfflineBanner()
                    if (connected) {
                        _uiState.update { old -> old.copy(loading = false) }
                        if (documentLoadFailed) reloadAfterServerIsBack()
                    }
                }

                override fun onTextReplaced(text: String) {
                    logOpenStep("text received")
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
        if (tree.documentById(documentId) == null) {
            // the cached tree is older than the document (new or moved)
            refreshWikiTree(documentId)
            return
        }
        showWikiTree(tree, documentId)
    }

    /** loads the wiki tree from the server, after uploads and moves */
    private suspend fun refreshWikiTree(documentId: String) {
        val root = try {
            dataRepository.sectionStore.fresh(ROOT_SECTION_ID)
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not refresh the wiki tree" }
            return
        }
        showWikiTree(WikiTree(root), documentId)
    }

    private fun showWikiTree(tree: WikiTree, documentId: String) {
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

    /**
     * @param connect false when the sync manager is connected already (after a move)
     */
    private suspend fun loadDocumentResource(documentId: String, connect: Boolean = true) {
        val resource = getDocumentUseCase(documentId)
        logOpenStep("item tree loaded")
        currentResource.value = resource
        when (resource) {
            is Success -> {
                updateTitle(resource.data?.name ?: "")

                if (!connect) {
                    // keep the connection
                } else if (isOfflineModeEnabledFlowUseCase().value) {
                    showOfflineVersionIfPossible()
                } else {
                    reconnectToServer()
                }
            }

            is Resource.Error -> {
                Timber.e(resource.error) { "Error loading document resource" }
                updateTitle(resource.data?.name ?: "")
                documentLoadFailed = true
                updateOfflineBanner()
                // a local copy is shown and editable already, and its edits sync later
                if (!localCopyAvailable.value) showOfflineVersionIfPossible()
                // keeps trying, and ends the offline state as soon as the server answers
                if (connect) reconnectToServer()
            }

            else -> {
                Timber.d { "$resource" }
            }
        }
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
        if (wikiTree.value?.documentById(documentId ?: "") != null) {
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

    /**
     * Uploads files into a section under unique, safe names (large photos are scaled down),
     * then inserts the Markdown for them, relative to this document
     */
    private suspend fun uploadFiles(sectionId: String, uris: List<Uri>, altText: String) {
        val tree = wikiTree.value ?: return
        val document = tree.documentById(documentId.value ?: return) ?: return
        val section = tree.section(sectionId) ?: return
        val fromPath = tree.pathOf(WikiItem.Document(document))
        _uiState.update { old -> old.copy(uploading = true) }
        val created = mutableListOf<String>()
        try {
            uris.forEach { uri ->
                val (displayName, bytes) = withContext(Dispatchers.IO) { readUpload(uri) }
                val name = PickerMarkdown.uniqueName(
                    section.resources.map { it.name } + created,
                    PickerMarkdown.sanitizeFileName(displayName),
                )
                val content = withContext(Dispatchers.Default) { shrinkImage(name, bytes) }
                restClient.uploadResource(sectionId, name, content).get()
                created += name
                deleteIfPhoto(uri)
            }
        } catch (ex: Exception) {
            Timber.e(ex) { "Upload failed" }
            showMessage(context.getString(R.string.upload_failed, ex.message ?: ex.javaClass.simpleName))
        } finally {
            _uiState.update { old -> old.copy(uploading = false) }
        }
        if (created.isEmpty()) return

        val sectionPath = tree.pathOf(section)
        val snippets = created.map { name ->
            val path = if (sectionPath.isEmpty()) name else "$sectionPath/$name"
            val image = ResourceKind.ofName(name) == ResourceKind.IMAGE
            val label = PickerMarkdown.escapeLinkText(
                altText.trim().takeIf { created.size == 1 && it.isNotEmpty() }
                    ?: if (image) name.replace(Regex("\\.[^.]+$"), "") else name
            )
            val destination = LinkPaths.linkPath(fromPath, LinkPaths.Target(LinkPaths.TargetType.RESOURCE, path))
            (if (image) "!" else "") + "[$label]($destination)"
        }
        dismissCurrentDialog()
        if (snippets.size == 1) {
            insertAtSelection(snippets.single())
        } else {
            val text = uiState.value.text?.toString() ?: ""
            applyEdit(MarkdownFormatting.insertSnippets(text, uiState.value.selection?.max ?: text.length, snippets))
        }
        // show the new files in the tree (pickers, preview)
        documentId.value?.let { refreshWikiTree(it) }
    }

    /** photos taken for an upload are not needed after it */
    private fun deleteIfPhoto(uri: Uri) {
        if (uri.authority != "${context.packageName}.fileprovider") return
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return
        File(File(context.cacheDir, "camera"), name).takeIf { it.isFile }?.delete()
    }

    /** the name and content of a file chosen for an upload */
    private fun readUpload(uri: Uri): Pair<String, ByteArray> {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val bytes = requireNotNull(contentResolver.openInputStream(uri)) { "Could not read $uri" }.use { it.readBytes() }
        return name to bytes
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
        if (wikiTree.value?.documentById(documentId) == null) {
            showMessage(context.getString(R.string.page_references_offline))
            return
        }
        showDialog(DialogState.PageReferencesPanel())
        loadPageReferences(documentId)
    }

    private suspend fun loadPageReferences(documentId: String) = coroutineScope {
        launch {
            restClient.getDocumentReferences(documentId).fold(
                success = { result -> updateReferencesPanel { it.copy(references = result.references, referencesError = null) } },
                failure = { error ->
                    Timber.w(error) { "Could not load the references" }
                    updateReferencesPanel { it.copy(referencesError = referencesErrorText(error)) }
                },
            )
        }
        launch {
            restClient.getBacklinks(ItemType.DOCUMENT, documentId).fold(
                success = { result -> updateReferencesPanel { it.copy(backlinks = result.backlinks, backlinksError = null) } },
                failure = { error ->
                    Timber.w(error) { "Could not load the backlinks" }
                    updateReferencesPanel { it.copy(backlinksError = referencesErrorText(error)) }
                },
            )
        }
    }

    /** older backends have no reference index; otherwise the server is not reachable */
    private fun referencesErrorText(error: FuelError): String = context.getString(
        if (error.isUnsupportedEndpoint()) R.string.page_references_unsupported else R.string.page_references_offline
    )

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
        // the backend sees the edit through the sync; show the fixed state right away
        updateReferencesPanel { data ->
            data.copy(references = data.references?.map {
                if (it == reference) it.copy(destination = suggested, status = "ok", target = it.movedTo, reason = null, path = null, movedTo = null, suggestedDestination = null) else it
            })
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
        /** how long opening a document waits for the redirect lookups of the local copies */
        private const val REDIRECT_TIMEOUT_MS = 5_000L

        private const val THUMBNAIL_SIZE_PX = 128
        private const val THUMBNAIL_CACHE_SIZE = 200

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

        val showInBrowserActionVisible: Boolean = true,

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
