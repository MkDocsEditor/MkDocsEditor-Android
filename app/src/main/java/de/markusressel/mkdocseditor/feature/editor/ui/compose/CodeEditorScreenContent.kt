package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodeeditor.library.compose.TextSplice
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.DisableEditModeFabConfig
import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.MarkdownPreview
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.PreviewResourceLoader

internal const val CONNECTION_ACTION_TAG = "connection-action"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    editorState: KodeEditorState,
    remotePeers: State<List<RemotePeer>> = NoRemotePeers,
    authorRuns: State<AuthorRuns> = remember { mutableStateOf(AuthorRuns()) },
    authorColors: () -> Map<String, String?> = { emptyMap() },
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    previewResourceLoader: PreviewResourceLoader = PreviewResourceLoader { null },
) {
    // from the window and not from the space of the editor: the keyboard takes away height, and that must
    // not close the page or change the arrangement while typing
    val configuration = LocalConfiguration.current
    val splitMode = uiState.splitMode ?: defaultSplitMode(configuration.screenWidthDp, configuration.screenHeightDp)

    val editorShown = uiState.editorShown

    // at the start of the animation, so the keyboard does not wait for the editor to leave
    val focusManager = LocalFocusManager.current
    LaunchedEffect(uiState.editModeActive) {
        if (!uiState.editModeActive) focusManager.clearFocus()
    }

    val chrome = remember { EditorChromeState() }
    val keyboardVisible = WindowInsets.isImeVisible
    chrome.shortScreen = configuration.screenHeightDp < SHORT_SCREEN_HEIGHT_DP
    SideEffect { chrome.keyboardVisible = keyboardVisible }

    DisposableEffect(editorState) {
        val listener = object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) = chrome.onTextEdited()
        }
        editorState.addListener(listener)
        onDispose { editorState.removeListener(listener) }
    }

    TabContentScaffold(
        // only the editor makes room for the keyboard, in sync with its animation: the
        // formatting toolbar sits on top of the keyboard, the text fills what is left above
        modifier = modifier.imePadding(),
        topBar = {
            // the status bar area stays reserved, so the text does not jump under it
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            ) {
                AnimatedVisibility(
                    visible = chrome.topBarVisible,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    MkDocsEditorTopAppBar(
                        title = uiState.title,
                        actions = editorTopBarActions(
                            uiState = uiState,
                            splitMode = splitMode,
                            wide = configuration.screenWidthDp >= WIDE_TOP_BAR_DP,
                        ),
                        onActionClicked = {
                            onUiEvent(CodeEditorViewModel.UiEvent.TopAppBarActionClicked(it))
                        },
                        windowInsets = WindowInsets(0),
                    )
                }
            }
        },
        bottomBar = {
            // the buttons stay while the bar leaves, and the size change moves the content along with it
            val lastVisibleState = remember { mutableStateOf(uiState) }
            if (uiState.isBottomAppBarVisible) lastVisibleState.value = uiState
            AnimatedVisibility(
                visible = uiState.isBottomAppBarVisible,
                enter = expandVertically(tween(EDITOR_MODE_ANIMATION_MS), expandFrom = Alignment.Top),
                exit = shrinkVertically(tween(EDITOR_MODE_ANIMATION_MS), shrinkTowards = Alignment.Top),
            ) {
                CodeEditorBottomBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (keyboardVisible) Modifier.height(COMPACT_TOOLBAR_HEIGHT) else Modifier),
                    uiState = lastVisibleState.value,
                    // while the edit mode opens there is no document to change yet
                    onUiEvent = { if (uiState.editModeActive) onUiEvent(it) },
                )
            }
        },
        snackbarHost = {
            AnimatedVisibility(
                visible = uiState.snackbar != null,
                enter = slideInVertically(),
                exit = slideOutVertically(),
            ) {
                if (uiState.snackbar == null) {
                    return@AnimatedVisibility
                }
                CodeEditorSackbar(
                    snackbar = uiState.snackbar,
                    onUiEvent = onUiEvent,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .observeDragDown { chrome.onDraggedDown() },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorBanners(uiState)

                EditorPanes(
                    modifier = Modifier.weight(1f),
                    showEditor = editorShown,
                    // reading shows the page; editing shows it only if the user or a wide window wants it
                    showPreview = !editorShown || splitMode != SplitMode.Off,
                    splitMode = splitMode,
                    ratio = uiState.splitRatio,
                    onRatioChange = { onUiEvent(CodeEditorViewModel.UiEvent.SplitRatioChanged(it)) },
                    onRatioChangeFinished = { onUiEvent(CodeEditorViewModel.UiEvent.SplitRatioChangeFinished) },
                    editor = {
                        CodeEditorLayout(
                            modifier = Modifier.fillMaxSize(),
                            state = editorState,
                            remotePeers = remotePeers,
                            settings = uiState.editorSettings,
                            authorRuns = authorRuns,
                            authorColors = authorColors,
                            readOnly = !uiState.editModeActive,
                            jumpRequest = uiState.jumpRequest,
                            ghosts = uiState.ghosts,
                            following = uiState.following,
                            localColor = uiState.localColor,
                            onStopFollowing = { onUiEvent(CodeEditorViewModel.UiEvent.StopFollowingClicked) },
                            onVisibleRangeChanged = { onUiEvent(CodeEditorViewModel.UiEvent.VisibleRangeChanged(it)) },
                        )
                    },
                    preview = {
                        MarkdownPreview(
                            modifier = Modifier.fillMaxSize(),
                            visible = !editorShown || splitMode != SplitMode.Off,
                            text = editorState.text,
                            sectionPath = uiState.previewSectionPath,
                            resourceLoader = previewResourceLoader,
                            onLinkClicked = { onUiEvent(CodeEditorViewModel.UiEvent.PreviewLinkClicked(it)) },
                        )
                    },
                )
            }

            // on top of the text, so the text does not move when it goes away
            if (uiState.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .testTag(LOADING_INDICATOR_TAG),
                )
            }
        }
    }
}

internal const val LOADING_INDICATOR_TAG = "editor-loading"

/** from this width the bar has room for the actions that are toggled or looked at often */
internal const val WIDE_TOP_BAR_DP = 600

/**
 * The bar keeps what shows a state (connection, users, mode). The rest goes into the menu: on a phone also the author
 * colors and the references, so that the title still fits.
 */
internal fun editorTopBarActions(
    uiState: CodeEditorViewModel.UiState,
    splitMode: SplitMode,
    wide: Boolean,
): List<TopAppBarAction.CodeEditor> {
    val editorShown = uiState.editorShown
    val authorHighlight = TopAppBarAction.CodeEditor.AuthorHighlightAction(uiState.editorSettings.authorHighlight)
        .takeIf { editorShown }
    val references = TopAppBarAction.CodeEditor.PageReferencesAction
    val menu = listOfNotNull(
        authorHighlight.takeIf { !wide },
        references.takeIf { !wide },
        TopAppBarAction.CodeEditor.CursorColorAction,
        TopAppBarAction.CodeEditor.MenuDivider,
        TopAppBarAction.CodeEditor.ShareLinkAction,
        TopAppBarAction.CodeEditor.ShowInBrowserAction,
        TopAppBarAction.CodeEditor.MenuDivider,
        TopAppBarAction.CodeEditor.RenameAction,
        TopAppBarAction.CodeEditor.MoveAction,
        TopAppBarAction.CodeEditor.PinAction,
        TopAppBarAction.CodeEditor.DeleteAction,
    )
    return listOfNotNull(
        uiState.connectionIndicator?.let { TopAppBarAction.CodeEditor.ConnectionAction(it) },
        (uiState.remotePeers + uiState.ghosts.map { it.asPeer() }).takeIf { it.isNotEmpty() }
            ?.let { TopAppBarAction.CodeEditor.OnlineUsersAction(it) },
        authorHighlight.takeIf { wide },
        references.takeIf { wide },
        // one place for both, so nothing moves when the mode changes
        TopAppBarAction.CodeEditor.EditorModeAction(splitMode.takeIf { editorShown })
            .takeIf { editorShown || uiState.canEdit },
        TopAppBarAction.CodeEditor.MoreAction(menu),
    )
}

private val COMPACT_TOOLBAR_HEIGHT = 56.dp
internal const val SHORT_SCREEN_HEIGHT_DP = 480

/** starts editing while reading, and changes the arrangement while editing */
@Composable
internal fun EditorModeAction(
    modifier: Modifier,
    mode: SplitMode?,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Crossfade(targetState = mode, animationSpec = tween(EDITOR_MODE_ANIMATION_MS)) { shown ->
            Image(
                asset = when (shown) {
                    null -> MaterialDesignIconic.Icon.gmi_edit
                    SplitMode.Off -> GoogleMaterial.Icon.gmd_code
                    SplitMode.Vertical -> GoogleMaterial.Icon.gmd_horizontal_split
                    SplitMode.Horizontal -> GoogleMaterial.Icon.gmd_vertical_split
                },
                contentDescription = stringResource(
                    when (shown) {
                        null -> R.string.code_editor_enable_edit_mode
                        SplitMode.Off -> R.string.split_mode_off
                        SplitMode.Vertical -> R.string.split_mode_vertical
                        SplitMode.Horizontal -> R.string.split_mode_horizontal
                    }
                ),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

/** small on purpose: not being connected is no problem, edits sync later */
@Composable
internal fun ConnectionAction(
    modifier: Modifier,
    indicator: ConnectionIndicator,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = modifier.testTag(CONNECTION_ACTION_TAG),
        onClick = onClick,
    ) {
        Image(
            modifier = Modifier.size(18.dp),
            asset = when (indicator) {
                ConnectionIndicator.Connected -> MaterialDesignIconic.Icon.gmi_cloud_done
                ConnectionIndicator.UnsyncedChanges -> MaterialDesignIconic.Icon.gmi_cloud_upload
                ConnectionIndicator.NotConnected,
                ConnectionIndicator.OfflineMode -> MaterialDesignIconic.Icon.gmi_cloud_off
            },
            contentDescription = stringResource(
                when (indicator) {
                    ConnectionIndicator.Connected -> R.string.connection_connected
                    ConnectionIndicator.UnsyncedChanges -> R.string.unsynced_changes
                    ConnectionIndicator.NotConnected,
                    ConnectionIndicator.OfflineMode -> R.string.connection_not_connected
                }
            ),
            // the one state the user may want to know about
            colorFilter = ColorFilter.tint(
                if (indicator == ConnectionIndicator.UnsyncedChanges) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ),
        )
    }
}

@Composable
internal fun AuthorHighlightAction(
    modifier: Modifier,
    mode: AuthorHighlightMode,
    onClick: () -> Unit
) {
    val modeName = stringResource(mode.nameRes)
    IconButton(
        modifier = modifier.testTag("author-highlight-action"),
        onClick = onClick
    ) {
        Image(
            asset = if (mode == AuthorHighlightMode.Off) MaterialDesignIconic.Icon.gmi_accounts_outline else MaterialDesignIconic.Icon.gmi_accounts,
            contentDescription = stringResource(R.string.editor_author_highlight_action, modeName),
            colorFilter = ColorFilter.tint(
                if (mode == AuthorHighlightMode.StripAndMarker) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
internal fun PageReferencesAction(
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier.testTag("page-references-action"),
        onClick = onClick
    ) {
        Image(
            asset = MaterialDesignIconic.Icon.gmi_link,
            contentDescription = stringResource(R.string.page_references_action),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
internal fun MoreAction(
    modifier: Modifier,
    entries: List<TopAppBarAction.CodeEditor>,
    onEntryClicked: (TopAppBarAction.CodeEditor) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_more_vert,
                contentDescription = stringResource(R.string.more_actions),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                if (entry is TopAppBarAction.CodeEditor.MenuDivider) {
                    HorizontalDivider()
                    return@forEach
                }
                val text = menuText(entry) ?: return@forEach
                DropdownMenuItem(
                    modifier = Modifier.testTag("menu-${entry::class.simpleName}"),
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onEntryClicked(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun menuText(entry: TopAppBarAction.CodeEditor): String? = when (entry) {
    is TopAppBarAction.CodeEditor.AuthorHighlightAction ->
        stringResource(R.string.editor_author_highlight_action, stringResource(entry.mode.nameRes))
    TopAppBarAction.CodeEditor.PageReferencesAction -> stringResource(R.string.page_references_action)
    TopAppBarAction.CodeEditor.CursorColorAction -> stringResource(R.string.cursor_color)
    TopAppBarAction.CodeEditor.ShareLinkAction -> stringResource(R.string.share_document_link)
    TopAppBarAction.CodeEditor.ShowInBrowserAction -> stringResource(R.string.open_in_browser)
    TopAppBarAction.CodeEditor.RenameAction -> stringResource(R.string.item_action_rename)
    TopAppBarAction.CodeEditor.MoveAction -> stringResource(R.string.item_action_move)
    TopAppBarAction.CodeEditor.PinAction -> stringResource(R.string.item_action_pin)
    TopAppBarAction.CodeEditor.DeleteAction -> stringResource(R.string.item_action_delete)
    else -> null
}

@Composable
internal fun CodeEditorSackbar(
    snackbar: SnackbarData,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    val text = when (snackbar) {
        is SnackbarData.SignedOut -> stringResource(R.string.code_editor_signed_out)
        is SnackbarData.Message -> snackbar.text
    }

    val action: (@Composable () -> Unit)? = when (snackbar) {
        is SnackbarData.Message -> null

        is SnackbarData.SignedOut -> {
            {
                TextButton(onClick = {
                    onUiEvent(CodeEditorViewModel.UiEvent.SnackbarActionClicked(snackbar))
                }) {
                    Text(text = stringResource(R.string.auth_sign_in))
                }
            }
        }
    }

    Snackbar(
        modifier = Modifier.padding(4.dp),
        action = action,
    ) {
        Text(text = text)
    }
}

/** what the user has to know about the open document */
@Composable
private fun EditorBanners(uiState: CodeEditorViewModel.UiState) {
    if (uiState.isNoOfflineVersionVisible) EditorBanner(stringResource(R.string.no_offline_version))
    if (uiState.isDocumentDeletedOnServer) EditorBanner(stringResource(R.string.document_deleted_on_server))
}

@Composable
private fun EditorBanner(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (isSystemInDarkTheme()) {
                    true -> colorResource(R.color.md_deep_orange_800)
                    false -> colorResource(R.color.md_deep_orange_300)
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        text = text,
        textAlign = TextAlign.Center,
    )
}

@CombinedPreview
@Composable
private fun CodeEditorScreenContentOfflinePreview() {
    MkDocsEditorTheme {
        CodeEditorScreenContent(
            uiState = CodeEditorViewModel.UiState(
                connectionIndicator = ConnectionIndicator.NotConnected,
                canEdit = true,
            ),
            editorState = remember { KodeEditorState() },
            onUiEvent = {},
        )
    }
}


@CombinedPreview
@Composable
private fun CodeEditorScreenContentOnlinePreview() {
    MkDocsEditorTheme {
        CodeEditorScreenContent(
            uiState = CodeEditorViewModel.UiState(
                connectionIndicator = ConnectionIndicator.Connected,
                editModeActive = true,
                fabConfig = FabConfig(
                    right = listOf(DisableEditModeFabConfig)
                ),
                isBottomAppBarVisible = true,
                remotePeers = listOf(
                    RemotePeer(
                        clientId = "1",
                        username = "alice",
                        displayName = "Alice",
                        color = "#E53935",
                        selections = listOf(RemoteSelection(anchor = 2, head = 7)),
                    )
                ),
            ),
            editorState = remember { KodeEditorState() },
            onUiEvent = {},
        )
    }
}