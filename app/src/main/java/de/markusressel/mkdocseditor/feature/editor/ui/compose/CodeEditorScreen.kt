package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.ItemDialogs
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dagger.hilt.android.EntryPointAccessors
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.LinkPickerDialog
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferencesDialog
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.ResourcePickerDialog
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.qr.domain.QrLink
import de.markusressel.mkdocseditor.feature.history.ui.compose.ChangesSinceSeenSheet
import de.markusressel.mkdocseditor.feature.history.ui.compose.LineAuthorSheet
import de.markusressel.mkdocseditor.feature.history.ui.compose.HistoryScreen
import de.markusressel.mkdocseditor.feature.qr.ui.compose.PageQrCodesDialog

internal data class CodeEditorScreen(
    val documentId: String,
    /** opened through a join link, see [CodeEditorViewModel.onOpenedByJoinLink] */
    private val joinLink: Boolean = false,
    /** the account of the one who sent the join link */
    private val joinFrom: String? = null,
    /** the client instance of the sender to follow */
    private val joinInstance: String? = null,
    /** opened at a change, from the feed or a notification, see [CodeEditorViewModel.onOpenedAtChange] */
    val change: ChangeLocation? = null,
) : Screen {

    /**
     * Each editor on the stack needs its own key: links in the preview open other documents
     * on top, and the key scopes the view model
     */
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val viewModel = getViewModel<CodeEditorViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = true,
            onBack = { navigator.pop() },
        )

        LaunchedEffect(documentId) {
            viewModel.setCurrentDocumentId(documentId)
            if (joinLink) viewModel.onOpenedByJoinLink(joinFrom, joinInstance)
            viewModel.onOpenedAtChange(change)
        }

        // a later join link for this document comes to this editor, see OpenJoinedDocument
        val editorJoins = remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, MainNavigationRequests.Accessor::class.java).editorJoins()
        }
        val join by editorJoins.request.collectAsState()
        LaunchedEffect(join) {
            join?.takeIf { it.screenKey == key }?.let {
                editorJoins.taken(it)
                viewModel.onJoinLinkOpened(it.from, it.instance)
            }
        }

        // a later notification for this document comes to this editor, see OpenRequestedDocument
        val navigationRequests = remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, MainNavigationRequests.Accessor::class.java).mainNavigationRequests()
        }
        val change by navigationRequests.editorChange.collectAsState()
        LaunchedEffect(change) {
            change?.takeIf { it.screenKey == key }?.let {
                navigationRequests.changeTaken(it)
                viewModel.onChangeOpened(it.change)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.navigationEvents.collect { request ->
                when (request) {
                    is CodeEditorViewModel.NavigationRequest.Login ->
                        navigator.push(LoginScreen(backendId = request.backendId))

                    is CodeEditorViewModel.NavigationRequest.OpenDocument ->
                        navigator.push(CodeEditorScreen(documentId = request.documentId))

                    is CodeEditorViewModel.NavigationRequest.OpenHistory ->
                        navigator.push(
                            HistoryScreen(
                                documentId = request.documentId,
                                canRestore = request.canRestore,
                                canRevert = request.canRevert,
                                canRestoreSection = request.canRestoreSection,
                                canCheckRevert = request.canCheckRevert,
                            )
                        )

                    is CodeEditorViewModel.NavigationRequest.Close -> navigator.pop()

                    is CodeEditorViewModel.NavigationRequest.ShareLink -> {
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, request.link)
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
            }
        }

        // persist the local copy when the app goes to the background
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        CodeEditorScreenContent(
            modifier = Modifier,
            uiState = uiState,
            editorState = viewModel.editorState,
            remotePeers = viewModel.editorPeers,
            authorRuns = viewModel.editorAuthorRuns,
            authorColors = viewModel::editorAuthorColors,
            onUiEvent = viewModel::onUiEvent,
            previewResourceLoader = viewModel.previewResourceLoader,
        )

        ItemDialogs(
            dialogState = uiState.itemDialog,
            onAction = { item, action -> viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemActionClicked(item, action)) },
            onMovePreview = { parentId, name ->
                viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemMovePreviewRequested(parentId, name))
            },
            onMoveBack = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemMoveBackClicked) },
            onMoveApply = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemMoveApplyClicked) },
            onBacklinkClicked = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemBacklinkClicked(it)) },
            onDeleteConfirmed = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemDeleteConfirmed) },
            onDismissRequest = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemDialogDismissed) },
            onPinChanged = { scope, pinned -> viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ItemPinChanged(scope, pinned)) },
        )

        val tree by viewModel.pickerTree.collectAsState()
        val documentId = uiState.documentId
        when (val dialogState = uiState.currentDialogState) {
            is CodeEditorViewModel.DialogState.LinkPicker -> if (tree != null && documentId != null) {
                LinkPickerDialog(
                    tree = tree!!,
                    documentId = documentId,
                    documentText = dialogState.documentText,
                    selection = dialogState.selection,
                    textLoader = viewModel.documentTextLoader,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    onInsert = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.InsertMarkdown(it)) },
                    onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
                )
            }

            is CodeEditorViewModel.DialogState.ResourcePicker -> if (tree != null && documentId != null) {
                ResourcePickerDialog(
                    tree = tree!!,
                    documentId = documentId,
                    selection = dialogState.selection,
                    imagesOnly = dialogState.imagesOnly,
                    startOnUpload = dialogState.startOnUpload,
                    uploading = uiState.uploading,
                    uploadProgress = uiState.uploadProgress,
                    onCancelUpload = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.CancelUploadClicked) },
                    thumbnailLoader = viewModel.thumbnailLoader,
                    photoUriFactory = viewModel.photoUriFactory,
                    onInsert = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.InsertMarkdown(it)) },
                    onUpload = { sectionId, uris, altText ->
                        viewModel.onUiEvent(CodeEditorViewModel.UiEvent.UploadFiles(sectionId, uris, altText))
                    },
                    onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
                )
            }

            // in the content, which knows whether a split is open
            is CodeEditorViewModel.DialogState.ViewSettings -> Unit

            is CodeEditorViewModel.DialogState.ChangesSinceSeen -> ChangesSinceSeenSheet(
                changes = dialogState.changes,
                onHunkClicked = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ChangeHunkClicked(it)) },
                onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
            )

            is CodeEditorViewModel.DialogState.LineAuthor -> LineAuthorSheet(
                block = dialogState.block,
                onOpenHistory = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.LineAuthorHistoryClicked) }
                    .takeIf { uiState.historyAvailable },
                onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
            )

            is CodeEditorViewModel.DialogState.PageReferencesPanel -> if (tree != null && documentId != null) {
                PageReferencesDialog(
                    tree = tree!!,
                    documentId = documentId,
                    state = dialogState.data,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    onInsert = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.InsertMarkdownAfterSelection(it)) },
                    onJump = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ReferenceClicked(it)) },
                    onFix = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.ReferenceFixClicked(it)) },
                    onOpenDocument = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.OpenDocumentClicked(it)) },
                    onUpload = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.PanelUploadClicked) },
                    onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
                )
            }

            is CodeEditorViewModel.DialogState.QrCodes -> {
                val clientUrl = stringResource(R.string.auth_web_client_url)
                PageQrCodesDialog(
                    state = dialogState,
                    readOnly = !uiState.canEdit,
                    stickerUrl = { QrLink.build(clientUrl, it) },
                    onKeyChanged = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.QrKeyChanged(it)) },
                    onLabelChanged = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.QrLabelChanged(it)) },
                    onAdd = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.QrAddClicked) },
                    onRemove = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.QrRemoveClicked(it)) },
                    onDismiss = { viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
                )
            }

            null -> {}
        }
    }
}
