package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocsrestclient.dashboard.PinScope
import de.markusressel.mkdocseditor.feature.dashboard.domain.PinActions
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.GetBacklinksUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.LoadWikiTreeUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogSlot
import de.markusressel.mkdocseditor.feature.filebrowser.ui.ItemAction
import de.markusressel.mkdocseditor.feature.filebrowser.ui.ItemActions
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.text
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogState as FileBrowserDialogState
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.GetAuthorshipUseCase
import de.markusressel.mkdocseditor.feature.editor.ui.compose.AuthorHighlightMode
import kotlinx.coroutines.flow.map
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorSettings
import android.net.Uri
import de.markusressel.mkdocseditor.feature.editor.domain.TextPositions
import de.markusressel.mkdocseditor.feature.editor.ui.compose.DEFAULT_SPLIT_RATIO
import de.markusressel.mkdocseditor.feature.editor.ui.compose.MAX_SPLIT_RATIO
import de.markusressel.mkdocseditor.feature.editor.ui.compose.MIN_SPLIT_RATIO
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import androidx.compose.runtime.State
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.editor.domain.localViewport
import kotlinx.coroutines.CancellationException
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import java.io.IOException
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocseditor.extensions.common.android.launch
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
import de.markusressel.mkdocseditor.feature.editor.domain.CreateJoinLinkUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.feature.editor.data.ClientInstanceId
import de.markusressel.mkdocseditor.feature.editor.data.LocalCursorColor
import de.markusressel.mkdocseditor.feature.editor.domain.isPresent
import de.markusressel.mkdocseditor.feature.editor.domain.joinFollowInstance
import de.markusressel.mkdocseditor.feature.editor.domain.lastSenderPosition
import de.markusressel.mkdocseditor.feature.editor.ui.compose.asPeer
import de.markusressel.mkdocseditor.feature.editor.ui.compose.label
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import android.content.Context
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import javax.inject.Inject

@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val sessionEnvironment: AndroidDocumentSessionEnvironment,
    private val preferencesHolder: KutePreferencesHolder,
    private val openDocumentInBrowserUseCase: OpenDocumentInBrowserUseCase,
    private val uploadFilesUseCase: UploadFilesUseCase,
    private val pageReferencesLoader: PageReferencesLoader,
    private val editorFiles: EditorFiles,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
    private val externalLinkOpener: ExternalLinkOpener,
    private val wikiTreeHolder: WikiTreeHolder,
    private val editorResources: EditorResources,
    private val eventBusManager: EventBusManager,
    private val context: Context,
    private val getAuthorshipUseCase: GetAuthorshipUseCase,
    private val createJoinLinkUseCase: CreateJoinLinkUseCase,
    private val localCursorColor: LocalCursorColor,
    clientInstanceId: ClientInstanceId,
    moveItemUseCase: MoveItemUseCase,
    getBacklinksUseCase: GetBacklinksUseCase,
    loadWikiTreeUseCase: LoadWikiTreeUseCase,
    deleteItemUseCase: DeleteItemUseCase,
    pinActions: PinActions,
) : ViewModel() {

    /** this app as a client instance, see [ClientInstanceId] */
    private val instanceId: String = clientInstanceId.value

    private val _uiState = MutableStateFlow(
        initialUiState(
            editModeOpening = preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value,
        )
    )
    internal val uiState = _uiState.asStateFlow()

    private var saveJob: Job? = null

    private val openedByJoinLink = MutableStateFlow(false)

    private val visibleRange = MutableStateFlow<TextRange?>(null)

    /** the source lines the rendered page shows, null while it has no room */
    private val previewLines = MutableStateFlow<IntRange?>(null)

    private var followEnd: Job? = null

    /** the upload of the image picker; cancelling it ends the upload, and nothing is inserted */
    private var uploadJob: Job? = null

    private val _navigationEvents = Channel<NavigationRequest>(Channel.BUFFERED)
    internal val navigationEvents = _navigationEvents.receiveAsFlow()

    /** rename, move and delete of this page, the same dialogs as in the file browser */
    private val itemActions = ItemActions(
        state = DialogSlot({ _uiState.value.itemDialog }) { change ->
            _uiState.update { it.copy(itemDialog = change(it.itemDialog)) }
        },
        moveItem = moveItemUseCase,
        getBacklinks = { getBacklinksUseCase(it) },
        loadTree = { loadWikiTreeUseCase() },
        showMessage = { showMessage(it.text(context)) },
        // the page follows a move through its connection, the tree is for the pickers and the preview
        reload = { launch { uiState.value.documentId?.let { refreshWikiTree(it) } } },
        deleteItem = { item, force -> deleteItemUseCase(item, force) },
        ownConnection = true,
        onDeleted = { _navigationEvents.send(NavigationRequest.Close) },
        pins = pinActions,
    )

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

    /** every new value asks the backend again who wrote what, once the text has settled */
    private val authorshipRequests = MutableStateFlow(0)

    private val editorText = EditorTextController(
        editModeActive = { uiState.value.editModeActive },
        onPeersChanged = ::publishPeers,
        onSelectionChanged = {
            session.onLocalSelectionChanged(it.start, it.end)
            onOwnCursorPlaced()
        },
        onEdited = { saveEditorState() },
        onLocalEdit = {
            // the user has started on their own work
            if (uiState.value.following != null) stopFollowing()
        },
        onDocumentChanged = { authorshipRequests.update { it + 1 } },
    )

    /** who wrote what, see [EditorTextController.authorRuns] */
    internal val editorAuthorRuns: State<AuthorRuns> = editorText.authorRuns

    internal fun editorAuthorColors(): Map<String, String?> = editorText.authorColors

    /** what the editor shows */
    internal val editorState: KodeEditorState = editorText.editorState

    /** the other users, see [EditorTextController.peers] */
    internal val editorPeers: State<List<RemotePeer>> = editorText.peers

    private val session: DocumentSession = DocumentSession(
        scope = viewModelScope,
        text = editorText,
        environment = sessionEnvironment,
        host = object : DocumentSession.Host {
            override suspend fun loadWikiTree(documentId: String) = this@CodeEditorViewModel.loadWikiTree(documentId)

            override suspend fun onBackendGone() {
                _navigationEvents.send(NavigationRequest.Close)
            }

            override fun onOfflineVersionShown() {
                disableEditMode()
                _uiState.update { old -> old.copy(isBottomAppBarVisible = false) }
            }

            override fun onReadOnly() = disableEditMode()

            override fun onRemoteChange() {
                saveEditorState()
            }
        },
        instanceId = instanceId,
    )

    init {
        launch {
            session.showsLoading.collect { loading -> _uiState.update { old -> old.copy(loading = loading) } }
        }

        launch {
            session.state.collect { state ->
                _uiState.update { old ->
                    old.copy(
                        documentId = state.documentId,
                        title = state.title,
                        isDocumentDeletedOnServer = state.deletedOnServer,
                        isNoOfflineVersionVisible = state.noOfflineVersion,
                    )
                }
            }
        }

        launch {
            session.documentId.filterNotNull().collect { documentId -> loadSplitRatio(documentId) }
        }

        launch {
            combine(
                preferencesHolder.editorLineWrappingPreference.persistedValue,
                preferencesHolder.editorLineNumbersPreference.persistedValue,
                preferencesHolder.editorTextSizePreference.persistedValue,
                preferencesHolder.editorAuthorHighlightPreference.persistedValue,
            ) { wrapping, numbers, size, authors ->
                EditorSettings(
                    lineWrapping = wrapping,
                    lineNumbers = numbers,
                    textSize = size.toInt(),
                    authorHighlight = AuthorHighlightMode.fromValue(authors),
                )
            }.collect { settings -> _uiState.update { it.copy(editorSettings = settings) } }
        }

        launch {
            combine(
                session.documentId,
                session.connectionStatus.map { it?.connected == true },
                preferencesHolder.editorAuthorHighlightPreference.persistedValue,
                authorshipRequests,
            ) { documentId, connected, mode, request ->
                val wanted = connected && AuthorHighlightMode.fromValue(mode) != AuthorHighlightMode.Off
                documentId.takeIf { wanted } to request
            }.collectLatest { (documentId, _) -> if (documentId != null) loadAuthors(documentId) }
        }

        session.start()

        launch { publishViewport() }

        launch {
            localCursorColor.color.collect { color ->
                session.onLocalColorChanged(color)
                _uiState.update { it.copy(localColor = color) }
            }
        }

        launch {
            session.editable.collect { editable ->
                if (editable.not()) {
                    // automatically disable edit mode, if (for whatever reason)
                    // editing is currently not possible
                    if (uiState.value.editModeActive) {
                        disableEditMode()
                    }
                }

                _uiState.update { old ->
                    old.copy(
                        canEdit = editable,
                    )
                }
            }
        }

        launch {
            combine(
                session.editable,
                openedByJoinLink,
            ) { editable, _ ->
                editModeOpensByItself(
                    editable = editable,
                    wanted = preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value || openedByJoinLink.value,
                )
            }.distinctUntilChanged().collect {
                // only opens the edit mode; losing the connection keeps it, because
                // edits are kept in the local copy and synced on reconnect
                if (it) enableEditMode()
            }
        }

        launch {
            // the page only comes up when the edit mode will not open after all
            session.awaitOpeningFailed()
            if (uiState.value.editModeOpening) disableEditMode()
        }

        launch {
            session.connectionStatus.filterNotNull().collectLatest {
                val backendId = session.backendId
                when {
                    backendId != null && it.isAuthenticationFailure -> showSnackbar(SnackbarData.SignedOut(backendId))
                    uiState.value.snackbar is SnackbarData.SignedOut -> dismissSnackbar()
                }
            }
        }

        launch {
            session.connectionIndicator.collect { indicator ->
                _uiState.update { old -> old.copy(connectionIndicator = indicator) }
            }
        }
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
        when (val target = PreviewLinks.resolve(wikiTreeHolder.tree.value, session.documentId.value, href)) {
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

    /** the text stays out of [uiState]: a new state for every keystroke recomposes the whole screen */
    private fun publishPeers() {
        _uiState.update { old -> old.copy(remotePeers = editorText.peers.value, ghosts = editorText.ghosts.value) }
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
                is UiEvent.ItemActionClicked -> itemActions.onActionClicked(
                    event.item, event.action, openElsewhere = uiState.value.remotePeers.isNotEmpty(),
                )
                is UiEvent.ItemMovePreviewRequested -> itemActions.previewMove(event.parentId, event.name)
                is UiEvent.ItemMoveBackClicked -> itemActions.backToTarget()
                is UiEvent.ItemMoveApplyClicked -> itemActions.applyMove()
                is UiEvent.ItemBacklinkClicked -> {
                    itemActions.dismiss()
                    _navigationEvents.send(NavigationRequest.OpenDocument(event.documentId))
                }
                is UiEvent.ItemDeleteConfirmed -> try {
                    itemActions.applyDelete()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    showMessage(context.getString(e.userMessage()))
                }
                is UiEvent.ItemDialogDismissed -> itemActions.dismiss()
                is UiEvent.ItemPinChanged -> try {
                    itemActions.setPinned(event.scope, event.pinned)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    showMessage(context.getString(R.string.pin_failed))
                }

                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    CodeEditorFabId.DisableEditMode -> {
                        // the other cursor only shows in the editor
                        stopFollowing()
                        _uiState.update { it.copy(readOnlyEditorShown = false) }
                        disableEditMode()
                    }
                }

                is UiEvent.FormatClicked -> editorText.applyFormat(event.action)
                is UiEvent.InsertLinkClicked -> showPicker(DialogState.LinkPicker(editorText.selectedText, editorText.text), FormatAction.Link)
                is UiEvent.InsertImageClicked -> showPicker(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = true), FormatAction.Image)
                is UiEvent.InsertFileReferenceClicked -> showPicker(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = false), null)
                is UiEvent.InsertMarkdown -> {
                    dismissCurrentDialog()
                    editorText.insertAtSelection(event.markdown)
                }

                is UiEvent.UploadFiles -> uploadFiles(event.sectionId, event.uris, event.altText)
                is UiEvent.CancelUploadClicked -> uploadJob?.cancel()

                is UiEvent.PreviewLinkClicked -> onPreviewLinkClicked(event.href)
                is UiEvent.SplitRatioChanged -> _uiState.update { it.copy(splitRatio = event.ratio) }
                is UiEvent.SplitRatioChangeFinished -> saveSplitRatio()

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
                    editorText.insertAfterSelection(event.markdown)
                }
                is UiEvent.PanelUploadClicked -> showDialog(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = false, startOnUpload = true))

                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
                is UiEvent.StopFollowingClicked -> stopFollowing()
                is UiEvent.RetryOpeningClicked -> session.retry()
                is UiEvent.CursorColorChosen -> {
                    localCursorColor.set(event.color)
                    dismissCurrentDialog()
                }
                is UiEvent.VisibleRangeChanged -> onVisibleRangeChanged(event.range)
                is UiEvent.PreviewLinesChanged -> previewLines.value = event.lines
            }
        }
    }

    /**
     * Opens a picker. Without the wiki tree (not loaded yet, or no connection and no cache)
     * the plain Markdown of [fallback] is inserted instead.
     */
    private fun showPicker(dialog: DialogState, fallback: FormatAction?) {
        val documentId = session.documentId.value
        if (wikiTreeHolder.tree.value?.documentById(documentId ?: "") != null) {
            showDialog(dialog)
        } else if (fallback != null) {
            editorText.applyFormat(fallback)
        }
    }

    /** uploads files into a section, then inserts the Markdown for them */
    private suspend fun uploadFiles(sectionId: String, uris: List<Uri>, altText: String) {
        val tree = wikiTreeHolder.tree.value ?: return
        val document = tree.documentById(session.documentId.value ?: return) ?: return
        val section = tree.section(sectionId) ?: return
        _uiState.update { old -> old.copy(uploading = true) }
        // lazy: the job is known before it reports progress
        val job = viewModelScope.async(start = CoroutineStart.LAZY) {
            uploadFilesUseCase(tree, document, section, uris, altText) { progress ->
                _uiState.update { old -> old.copy(uploadProgress = progress) }
            }
        }
        uploadJob = job
        val result = try {
            job.await()
        } catch (e: CancellationException) {
            // only the upload was cancelled, the editor goes on
            currentCoroutineContext().ensureActive()
            return
        } finally {
            if (uploadJob === job) uploadJob = null
            _uiState.update { old -> old.copy(uploading = false, uploadProgress = null) }
        }
        result.failure?.let { showMessage(context.getString(R.string.upload_failed, context.getString(it))) }
        if (result.snippets.isEmpty()) return

        dismissCurrentDialog()
        if (result.snippets.size == 1) {
            editorText.insertAtSelection(result.snippets.single())
        } else {
            editorText.insertSnippets(result.snippets)
        }
        // show the new files in the tree (pickers, preview)
        session.documentId.value?.let { refreshWikiTree(it) }
    }

    /** Waits until the text settled, because an answer only fits the exact state of the text it describes */
    private suspend fun loadAuthors(documentId: String) {
        repeat(AUTHORSHIP_ATTEMPTS) {
            delay(AUTHORSHIP_SETTLE_MS)
            val fits = getAuthorshipUseCase(documentId).fold(
                success = { editorText.showAuthors(it) },
                failure = {
                    // for example a backend that does not have this yet; the editor works without it
                    Timber.w(it)
                    return
                },
            )
            if (fits) return
        }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.CodeEditor) {
        when (action) {
            is TopAppBarAction.CodeEditor.EditorModeAction -> when (val mode = action.mode) {
                null -> enableEditMode()
                else -> _uiState.update { it.copy(splitMode = mode.next()) }
            }
            is TopAppBarAction.CodeEditor.ShowInBrowserAction -> onOpenInBrowserClicked()
            is TopAppBarAction.CodeEditor.ShareLinkAction -> onShareLinkClicked()
            is TopAppBarAction.CodeEditor.MoreAction,
            is TopAppBarAction.CodeEditor.MenuDivider -> Unit
            is TopAppBarAction.CodeEditor.RenameAction -> onItemAction(ItemAction.Rename)
            is TopAppBarAction.CodeEditor.MoveAction -> onItemAction(ItemAction.Move)
            is TopAppBarAction.CodeEditor.PinAction -> onItemAction(ItemAction.Pin)
            is TopAppBarAction.CodeEditor.DeleteAction -> onItemAction(ItemAction.Delete)
            is TopAppBarAction.CodeEditor.FollowUserAction -> startFollowing(action.instanceId)
            is TopAppBarAction.CodeEditor.CursorColorAction -> showDialog(DialogState.CursorColorPicker)
            is TopAppBarAction.CodeEditor.OnlineUsersAction -> Unit
            is TopAppBarAction.CodeEditor.ConnectionAction -> onConnectionIconClicked(action.indicator)
            is TopAppBarAction.CodeEditor.PageReferencesAction -> showPageReferences()
            is TopAppBarAction.CodeEditor.AuthorHighlightAction -> {
                val next = action.mode.next()
                preferencesHolder.editorAuthorHighlightPreference.persistValue(next.value)
                showMessage(context.getString(R.string.editor_author_highlight_changed, context.getString(next.nameRes)))
            }
        }
    }

    private suspend fun onItemAction(action: ItemAction) {
        val documentId = uiState.value.documentId ?: return
        val document = (wikiTreeHolder.tree.value ?: wikiTreeHolder.load(documentId))?.documentById(documentId)
        if (document == null) {
            showMessage(context.getString(R.string.item_actions_page_unknown))
            return
        }
        // the server counts this editor as well, so only the others say whether the page is open elsewhere
        itemActions.onActionClicked(WikiItem.Document(document), action, openElsewhere = uiState.value.remotePeers.isNotEmpty())
    }

    /** says what the state means, and does not wait for the next try to connect */
    private fun onConnectionIconClicked(indicator: ConnectionIndicator) {
        showMessage(
            context.getString(
                when {
                    indicator == ConnectionIndicator.Connected -> R.string.connection_connected_info
                    indicator == ConnectionIndicator.UnsyncedChanges -> R.string.connection_unsynced_changes_info
                    else -> R.string.connection_not_connected_info
                }
            )
        )
        if (session.connectionStatus.value?.connected != true) session.reconnect()
    }

    /** opens the references panel and loads its data */
    private suspend fun showPageReferences() {
        val documentId = session.documentId.value ?: return
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
        val range = TextPositions.rangeOf(editorText.text, reference.line, reference.column, reference.destination)
        editorText.select(range.first, range.last + 1)
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
        val range = TextPositions.rangeOf(editorText.text, reference.line, reference.column, reference.destination)
        if (range.isEmpty()) {
            showMessage(context.getString(R.string.page_references_changed))
            return
        }
        editorText.replace(range.first, range.last + 1, suggested)
        updateReferencesPanel { it.withFixedReference(reference, suggested) }
    }

    /**
     * Takes over the ratio saved for the document. There is none under a new id after a rename, and then the ratio
     * at hand moves to that id.
     */
    private suspend fun loadSplitRatio(documentId: String) {
        val saved = dataRepository.getSplitRatio(documentId)
        val current = uiState.value.splitRatio
        when {
            saved != null -> _uiState.update { it.copy(splitRatio = saved.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)) }
            current != DEFAULT_SPLIT_RATIO -> dataRepository.saveSplitRatio(documentId, current)
        }
    }

    private suspend fun saveSplitRatio() {
        val documentId = session.documentId.value ?: return
        dataRepository.saveSplitRatio(documentId, uiState.value.splitRatio)
    }

    private fun onSnackbarAction(snackbar: SnackbarData) {
        when (snackbar) {
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
        session.setDocument(documentId)
    }

    /**
     * The document was opened through a join link: the invited person is here to edit, whatever the settings say, and
     * looks at what the sender is looking at. Only once, so a recreated screen does not move the cursor again.
     *
     * @param from the account of the sender, null if the link names none
     * @param instance the client instance of the sender to follow, null if the link names none
     */
    fun onOpenedByJoinLink(from: String?, instance: String? = null) {
        if (openedByJoinLink.value) return
        openedByJoinLink.value = true
        onJoinLinkOpened(from, instance)
    }

    /** a join link for the document of this editor, opened while the editor is already there; see [onOpenedByJoinLink] */
    fun onJoinLinkOpened(from: String?, instance: String?) {
        // someone who can only read still gets the editor, because only there the cursor shows where the sender is
        _uiState.update { it.copy(readOnlyEditorShown = true) }
        if (!uiState.value.editModeActive) {
            _uiState.update {
                it.copy(editModeOpening = true, isBottomAppBarVisible = true, fabConfig = FabConfig(right = listOf(DisableEditModeFabConfig)))
            }
        }
        from?.let { launch { jumpToSender(it, instance) } }
    }

    private suspend fun jumpToSender(from: String, instance: String?) {
        // a link sent from this app: the sender is this user, right here
        if (instance != null && instance == session.instanceIdInUse) return
        session.connectionStatus.first { it?.connected == true }
        val selectionBefore = editorText.selection
        val known = withTimeoutOrNull(SENDER_WAIT_MS) {
            snapshotFlow { editorText.presenceKnown.value }.first { it }
        }
        val followed = if (known != null) joinFollowInstance(editorPeers.value, editorText.ghosts.value, from, instance) else null
        if (followed != null) {
            startFollowing(followed)
            return
        }
        // fully away: the link only goes to where they last were
        val position = withTimeoutOrNull(SENDER_WAIT_MS) {
            snapshotFlow { lastSenderPosition(editorText.lastPositions.value, from) }.filterNotNull().first()
        }
        // the user has started on their own: don't take the cursor away, and there is nothing to explain
        if (editorText.selection != selectionBefore) return
        if (position != null) {
            editorText.select(position, position)
            _uiState.update { it.copy(jumpRequest = JumpRequest(position)) }
        } else {
            showMessage(context.getString(R.string.join_sender_not_here))
        }
    }

    private fun followedName(instanceId: String): String =
        editorPeers.value.firstOrNull { it.instanceId == instanceId }?.label
            ?: editorText.ghosts.value.firstOrNull { it.instanceId == instanceId }?.asPeer()?.label
            ?: ""

    /** Following ends with the ghost. While disconnected nobody knows who is here, so that doesn't end it. */
    private fun startFollowing(instanceId: String) {
        followEnd?.cancel()
        // a reader of the rendered page gets the editor as well, only there the other cursor shows
        _uiState.update { it.copy(following = Following(instanceId, followedName(instanceId)), readOnlyEditorShown = true) }
        followEnd = launch {
            snapshotFlow {
                editorText.presenceKnown.value && !isPresent(editorPeers.value, editorText.ghosts.value, instanceId)
            }.first { it }
            val name = uiState.value.following?.name.orEmpty()
            stopFollowing()
            showMessage(context.getString(R.string.follow_ended, name))
        }
    }

    /** the user put the own cursor into the text while following */
    private fun onOwnCursorPlaced() {
        if (uiState.value.following == null) return
        followEnd?.cancel()
        followEnd = null
        _uiState.update { it.ownCursorPlaced() }
    }

    private fun stopFollowing() {
        followEnd?.cancel()
        followEnd = null
        _uiState.update { it.copy(following = null) }
    }

    /** what the editor shows now, for other users who follow this one. Null while it is out of sight or not laid out. */
    internal fun onVisibleRangeChanged(range: TextRange?) {
        visibleRange.value = range
    }

    private suspend fun publishViewport() {
        combine(visibleRange, previewLines, uiState.map { it.editorShown }.distinctUntilChanged()) { range, lines, shown ->
            localViewport(editorShown = shown, editorRange = range, previewLines = lines, text = editorText.text)
        }
            .distinctUntilChanged()
            // every change: the sync manager spreads them out, and sends the newest one along with each cursor
            .collect { viewport -> session.onLocalViewportChanged(viewport?.first, viewport?.second) }
    }

    /**
     * Called when the editor screen is no longer visible.
     * Writes the local copy to disk, so no edit is lost if the app gets killed.
     */
    internal fun onStop() {
        session.persist()
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }

    /**
     * Keeps the old offline cache up to date, some time after the last change: it holds the whole
     * text, and remote changes can come many times a second. A save that is cut off by closing
     * the editor loses nothing, the local copy is the one that has every edit.
     */
    private fun saveEditorState() {
        val documentId = session.documentId.value ?: return
        val text = editorText.text
        val selection = editorText.selection.start
        saveJob?.cancel()
        saveJob = launch {
            delay(SAVE_DELAY_MS)
            dataRepository.saveEditorState(documentId = documentId, text = text, selection = selection)
        }
    }

    private suspend fun onShareLinkClicked() {
        val documentId = session.documentId.value ?: return
        try {
            when (val link = createJoinLinkUseCase(documentId, session.instanceIdInUse)) {
                null -> showMessage(context.getString(R.string.share_document_link_unavailable))
                else -> _navigationEvents.send(NavigationRequest.ShareLink(link))
            }
        } catch (e: IOException) {
            Timber.w(e) { "Creating the join link failed" }
            showMessage(context.getString(R.string.share_document_link_failed))
        } catch (e: AuthApiException) {
            Timber.w(e) { "Creating the join link failed" }
            showMessage(context.getString(R.string.share_document_link_failed))
        }
    }

    private suspend fun onOpenInBrowserClicked(): Boolean {
        val documentId = session.documentId.value ?: return false
        return openDocumentInBrowserUseCase(documentId)
    }

    /**
     * Called when the user activates the edit mode
     */
    private fun enableEditMode(): Boolean {
        _uiState.update { it.editing() }
        return true
    }

    private fun disableEditMode() {
        _uiState.update { old ->
            old.copy(
                editModeActive = false,
                editModeOpening = false,
                fabConfig = old.fabConfig.copy(right = emptyList()),
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

    companion object {
        private const val SAVE_DELAY_MS = 2_000L

        /** how long to wait for the sender of a join link to show up, they may have looked away for a moment */
        private const val SENDER_WAIT_MS = 5_000L

        /** see docs/sync-protocol.md of the backend, "Viewport" */

        /**
         * Whether the edit mode opens on its own. It does not wait for the connection: a local copy can be edited
         * without one, and its edits sync later. Without a local copy the text only comes with the connection.
         *
         * @param wanted the user always wants the edit mode, or a join link asks for it
         */
        internal fun editModeOpensByItself(editable: Boolean, wanted: Boolean): Boolean =
            editable && wanted

        internal val DisableEditModeFabConfig = FabConfig.Fab<CodeEditorFabId>(
            id = CodeEditorFabId.DisableEditMode,
            description = R.string.code_editor_disable_edit_mode,
            icon = MaterialDesignIconic.Icon.gmi_check,
        )

        /**
         * An edit mode that opens on its own has its whole layout from the first frame, so nothing moves or fades in
         * while the screen slides in and the document loads.
         */
        /** the edit mode is on */
        internal fun UiState.editing() = copy(
            editModeActive = true,
            editModeOpening = false,
            fabConfig = fabConfig.copy(right = listOf(DisableEditModeFabConfig)),
            isBottomAppBarVisible = true,
        )

        /**
         * The user put the own cursor into the text while following: they want to work there now, not watch. An editor
         * that only following opened can't take a cursor, so the edit mode comes on for someone who may edit.
         */
        internal fun UiState.ownCursorPlaced(): UiState {
            if (following == null) return this
            val stopped = copy(following = null)
            return if (!editModeActive && canEdit) stopped.editing() else stopped
        }

        internal fun initialUiState(editModeOpening: Boolean) = UiState(
            editModeOpening = editModeOpening,
            isBottomAppBarVisible = editModeOpening,
            fabConfig = if (editModeOpening) FabConfig(right = listOf(DisableEditModeFabConfig)) else FabConfig(),
        )
    }


    /** A class and not just an index: going to the same place twice has to go there twice */
    internal class JumpRequest(val position: Int)

    data class Following(val instanceId: String, val name: String)

    data class UiState(
        val currentDialogState: DialogState? = null,

        /** rename, move or delete of this page, see [ItemActions] */
        val itemDialog: FileBrowserDialogState? = null,

        /** the color of the own cursor chosen for now, null for the color of the account */
        val localColor: String? = null,

        val fabConfig: FabConfig<CodeEditorFabId> = FabConfig(),

        val loading: Boolean = false,

        val documentId: String? = null,
        val title: String = "",

        /** the folder of the document below the wiki root (`a/b`), for paths in the preview */
        val previewSectionPath: String = "",

        /** true while files from the image picker are uploaded */
        val uploading: Boolean = false,

        /** how far the upload of the current file is, null before it starts */
        val uploadProgress: TransferProgress? = null,

        /**
         * Indicates whether the CodeEditor is in "edit" mode or not
         */
        val editModeActive: Boolean = false,
        /**
         * The edit mode opens on its own as soon as the document can be edited. Until then the editor shows the text
         * without taking input, so the page does not come up just to move away again.
         */
        val editModeOpening: Boolean = false,


        /**
         * Whether the document is connected, null until that is known
         */
        val connectionIndicator: ConnectionIndicator? = null,

        val editorSettings: EditorSettings = EditorSettings(),

        /** what the user chose for the edit mode, null to go with what fits the window */
        val splitMode: SplitMode? = null,

        /** the share of the code editor when it is split */
        val splitRatio: Float = DEFAULT_SPLIT_RATIO,

        val isBottomAppBarVisible: Boolean = false,

        /** whether reading can switch to the edit mode right now */
        val canEdit: Boolean = false,

        val snackbar: SnackbarData? = null,

        /** the editor stays when the edit mode ends by itself, see [onOpenedByJoinLink] */
        val readOnlyEditorShown: Boolean = false,

        /** where the editor should go, once */
        val jumpRequest: JumpRequest? = null,

        val ghosts: List<RemoteGhost> = emptyList(),

        /** whose view the editor follows */
        val following: Following? = null,

        /**
         * Other users in this document, with their resolved cursors and selections
         */
        val remotePeers: List<RemotePeer> = emptyList(),

        /**
         * The document was deleted on the server; the local copy is still shown
         */
        val isDocumentDeletedOnServer: Boolean = false,

        /**
         * There is no connection and no text to show
         */
        val isNoOfflineVersionVisible: Boolean = false,
    ) {
        val editorShown: Boolean
            get() = editModeActive || editModeOpening || readOnlyEditorShown
    }

    sealed class UiEvent {
        data object DismissDialog : UiEvent()

        data class ItemActionClicked(val item: WikiItem, val action: ItemAction) : UiEvent()
        data class ItemMovePreviewRequested(val parentId: String?, val name: String?) : UiEvent()
        data object ItemMoveBackClicked : UiEvent()
        data object ItemMoveApplyClicked : UiEvent()
        data class ItemBacklinkClicked(val documentId: String) : UiEvent()
        data object ItemDeleteConfirmed : UiEvent()
        data object ItemDialogDismissed : UiEvent()
        data class ItemPinChanged(val scope: PinScope, val pinned: Boolean) : UiEvent()

        data class TopAppBarActionClicked(val action: TopAppBarAction.CodeEditor) : UiEvent()

        data class ExpandableFabItemSelected(val item: FabConfig.Fab<CodeEditorFabId>) : UiEvent()
        data class SnackbarActionClicked(val snackbar: SnackbarData) : UiEvent()
        data object StopFollowingClicked : UiEvent()

        /** the page is not on this device yet, and the user asks the server again */
        data object RetryOpeningClicked : UiEvent()

        /** @param color `#rrggbb`, null for the color of the account */
        data class CursorColorChosen(val color: String?) : UiEvent()
        data class VisibleRangeChanged(val range: TextRange?) : UiEvent()

        /** @param lines the 0-based source lines the rendered page shows, null while it has no room */
        data class PreviewLinesChanged(val lines: IntRange?) : UiEvent()

        data class FormatClicked(val action: FormatAction) : UiEvent()
        data object InsertLinkClicked : UiEvent()
        data object InsertImageClicked : UiEvent()

        data object InsertFileReferenceClicked : UiEvent()

        /** Markdown from a picker, replaces the selection */
        data class InsertMarkdown(val markdown: String) : UiEvent()

        data class UploadFiles(val sectionId: String, val uris: List<Uri>, val altText: String) : UiEvent()
        data object CancelUploadClicked : UiEvent()

        data class PreviewLinkClicked(val href: String) : UiEvent()
        data class SplitRatioChanged(val ratio: Float) : UiEvent()
        data object SplitRatioChangeFinished : UiEvent()

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

    sealed interface NavigationRequest {
        data class Login(val backendId: Long) : NavigationRequest
        data class OpenDocument(val documentId: String) : NavigationRequest
        /** leave the editor, e.g. to show a folder in the file browser */
        data object Close : NavigationRequest
        data class ShareLink(val link: String) : NavigationRequest {
            override fun toString() = "ShareLink"
        }
    }

    sealed class CodeEditorFabId {
        data object DisableEditMode : CodeEditorFabId()
    }

    sealed interface DialogState {
        /**
         * @param selection the selected text, which becomes the link text
         * @param documentText the text of the document when the picker opened, for its headings
         */
        data class LinkPicker(val selection: String, val documentText: String) : DialogState

        /**
         * @param selection the selected text, which becomes the alt text
         * @param startOnUpload open the upload tab first
         */
        data class ResourcePicker(val selection: String, val imagesOnly: Boolean, val startOnUpload: Boolean = false) : DialogState

        /** the links and files of the page, and the pages that link to it */
        data class PageReferencesPanel(val data: PageReferences = PageReferences()) : DialogState

        data object CursorColorPicker : DialogState
    }
}

private const val AUTHORSHIP_SETTLE_MS = 2000L
private const val AUTHORSHIP_ATTEMPTS = 3
