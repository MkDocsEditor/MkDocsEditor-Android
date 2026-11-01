package de.markusressel.mkdocseditor.feature.editor.ui

import android.net.Uri
import de.markusressel.mkdocseditor.feature.editor.domain.TextPositions
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.State
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
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
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocumentInBrowserUseCase
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
internal class CodeEditorViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    sessionEnvironment: AndroidDocumentSessionEnvironment,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _navigationEvents = Channel<NavigationRequest>(Channel.BUFFERED)
    internal val navigationEvents = _navigationEvents.receiveAsFlow()

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

    private val editorText = EditorTextController(
        scope = viewModelScope,
        now = SystemClock::elapsedRealtime,
        editModeActive = { uiState.value.editModeActive },
        onChanged = ::publishEditorText,
        onEdited = { saveEditorState() },
    )

    /** what the text field shows, see [EditorTextController.field] */
    internal val editorField: State<EditorField> = editorText.field

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
    )

    init {
        launch {
            session.state.collect { state ->
                _uiState.update { old ->
                    old.copy(
                        documentId = state.documentId,
                        title = state.title,
                        loading = state.loading,
                        isOfflineModeBannerVisible = state.offlineBannerVisible,
                        isDocumentDeletedOnServer = state.deletedOnServer,
                    )
                }
            }
        }

        session.start()

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
                        isBottomAppBarVisible = editable,
                        // the toggle that opens the edit mode, once editing is possible
                        fabConfig = old.fabConfig.copy(right = editModeFabs(editable, old.editModeActive)),
                    )
                }
            }
        }

        launch {
            combine(
                session.connectionStatus,
                session.editable,
                sessionEnvironment.offlineMode,
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
            session.connectionStatus.filterNotNull().collectLatest {
                val backendId = session.backendId
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

    private fun publishEditorText() {
        val text = editorText.text
        _uiState.update { old ->
            old.copy(
                text = if (old.text?.text == text) old.text else AnnotatedString(text),
                selection = editorText.selection,
                remotePeers = editorText.peers,
            )
        }
    }

    /**
     * The field took the value with this id, see [EditorTextController.onValueShown]
     */
    internal fun onEditorValueShown(id: Long) = editorText.onValueShown(id)

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

                is UiEvent.FormatClicked -> editorText.applyFormat(event.action)
                is UiEvent.InsertLinkClicked -> showPicker(DialogState.LinkPicker(editorText.selectedText), FormatAction.Link)
                is UiEvent.InsertImageClicked -> showPicker(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = true), FormatAction.Image)
                is UiEvent.InsertFileReferenceClicked -> showPicker(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = false), null)
                is UiEvent.InsertMarkdown -> {
                    dismissCurrentDialog()
                    editorText.insertAtSelection(event.markdown)
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
                    editorText.insertAfterSelection(event.markdown)
                }
                is UiEvent.PanelUploadClicked -> showDialog(DialogState.ResourcePicker(editorText.selectedText, imagesOnly = false, startOnUpload = true))

                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
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
        val result = try {
            uploadFilesUseCase(tree, document, section, uris, altText)
        } finally {
            _uiState.update { old -> old.copy(uploading = false) }
        }
        result.failure?.let { showMessage(context.getString(R.string.upload_failed, it)) }
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
                session.reconnect()
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
        session.setDocument(documentId)
    }

    /**
     * The text field got or lost the focus
     */
    internal fun onEditorFocusChanged(focused: Boolean) = editorText.onFocusChanged(focused)

    internal fun onUserTextInput(value: TextFieldValue) {
        editorText.onFieldChanged(value)
        session.onLocalSelectionChanged(editorText.selection.start, editorText.selection.end)
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

    private fun saveEditorState(): Job = launch {
        dataRepository.saveEditorState(
            documentId = session.documentId.value!!,
            text = uiState.value.text?.toString(),
            selection = uiState.value.selection?.start ?: 0,
        )
    }

    private suspend fun onOpenInBrowserClicked(): Boolean {
        val documentId = session.documentId.value ?: return false
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
                    right = editModeFabs(session.editable.value, editModeActive = false)
                ),
                // the bar holds the toggle back into the edit mode
                isBottomAppBarVisible = session.editable.value,
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
        session.reconnect()
    }

    companion object {
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
