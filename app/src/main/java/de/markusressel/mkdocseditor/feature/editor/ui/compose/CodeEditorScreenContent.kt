package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.visibleSourceLines
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
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
import de.markusressel.mkdocseditor.feature.common.ui.compose.TransferProgressBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.barsAndCutout
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
    val previewShown = !editorShown || splitMode != SplitMode.Off

    // saved: a rotation recreates the screen, and both sides go back to their lines
    val scrollSync = rememberSaveable(editorState, saver = ScrollSync.saver(text = { editorState.text })) {
        ScrollSync(text = { editorState.text })
    }
    SideEffect {
        scrollSync.enabled = uiState.editorSettings.scrollSync
        scrollSync.active = editorShown && previewShown
    }

    // at the start of the animation, so the keyboard does not wait for the editor to leave
    val focusManager = LocalFocusManager.current
    LaunchedEffect(uiState.editModeActive) {
        if (!uiState.editModeActive) focusManager.clearFocus()
    }

    val editorFocus = rememberEditorFocus(
        editModeActive = uiState.editModeActive,
        editModeOpening = uiState.editModeOpening,
    )

    val chrome = remember { EditorChromeState() }
    val keyboardVisible = WindowInsets.isImeVisible
    chrome.shortScreen = configuration.screenHeightDp < SHORT_SCREEN_HEIGHT_DP
    SideEffect { chrome.keyboardVisible = keyboardVisible }

    DisposableEffect(editorState) {
        val listener = object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) {
                chrome.onTextEdited()
                scrollSync.onLocalEdit()
            }
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
                    .windowInsetsPadding(WindowInsets.barsAndCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                AnimatedVisibility(
                    visible = chrome.topBarVisible,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    MkDocsEditorTopAppBar(
                        title = uiState.shownTitle,
                        actions = editorTopBarActions(
                            uiState = uiState,
                            splitMode = splitMode,
                            widthDp = configuration.screenWidthDp,
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
            Column {
                uiState.download?.let { download ->
                    TransferProgressBar(
                        progress = download,
                        onCancel = { onUiEvent(CodeEditorViewModel.UiEvent.CancelDownloadClicked) },
                    )
                }
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
                    showPreview = previewShown,
                    splitMode = splitMode,
                    ratio = uiState.splitRatio,
                    onRatioChange = { onUiEvent(CodeEditorViewModel.UiEvent.SplitRatioChanged(it)) },
                    onRatioChangeFinished = { onUiEvent(CodeEditorViewModel.UiEvent.SplitRatioChangeFinished) },
                    editor = {
                        CodeEditorLayout(
                            modifier = Modifier.fillMaxSize().then(editorFocus.modifier),
                            restoreToCursor = editorFocus.hadFocus,
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
                            scrollSync = scrollSync,
                            // the editor goes to a change while it is shown, else the page while reading
                            changeJump = uiState.changeJump,
                            onChangeJumpShown = { onUiEvent(CodeEditorViewModel.UiEvent.ChangeJumpShown(it)) },
                        )
                    },
                    preview = {
                        MarkdownPreview(
                            modifier = Modifier.fillMaxSize(),
                            visible = previewShown,
                            text = editorState.text,
                            sectionPath = uiState.previewSectionPath,
                            resourceLoader = previewResourceLoader,
                            onLinkClicked = { onUiEvent(CodeEditorViewModel.UiEvent.PreviewLinkClicked(it)) },
                            onPageMeta = { onUiEvent(CodeEditorViewModel.UiEvent.PageMetaChanged(it)) },
                            // in a split the page only lights the change up, and follows the editor there
                            change = uiState.changeJump?.target,
                            scrollToChange = !editorShown,
                            onChangeShown = {
                                uiState.changeJump?.takeIf { !editorShown }?.let { onUiEvent(CodeEditorViewModel.UiEvent.ChangeJumpShown(it)) }
                            },
                            syncTarget = { scrollSync.previewTarget },
                            onLayout = scrollSync::onPreviewLayout,
                            onScrolled = { scroll ->
                                scrollSync.onPreviewScrolled(scroll)
                                onUiEvent(CodeEditorViewModel.UiEvent.PreviewLinesChanged(visibleSourceLines(scroll)))
                            },
                        )
                    },
                )
            }

            if (uiState.isNoOfflineVersionVisible) {
                NotOnDeviceView(onRetry = { onUiEvent(CodeEditorViewModel.UiEvent.RetryOpeningClicked) })
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

        if (uiState.currentDialogState is CodeEditorViewModel.DialogState.ViewSettings) {
            EditorViewSheet(
                settings = uiState.editorSettings,
                localColor = uiState.localColor,
                splitOpen = previewShown,
                onUiEvent = onUiEvent,
            )
        }
    }
}

internal const val LOADING_INDICATOR_TAG = "editor-loading"

/**
 * Gives the focus back to the editor after a rotation, or after Android restored the screen, if it had it in the edit
 * mode before, and opens the keyboard again. The keyboard's composition ends with the old window all the same. The
 * text and the selection stay in the editor's state, so nothing goes through the document here.
 */
@Composable
internal fun rememberEditorFocus(editModeActive: Boolean, editModeOpening: Boolean): EditorFocus {
    var focused by rememberSaveable { mutableStateOf(false) }
    // read once: the first focus events of the new editor say it has none, and must not overwrite what was saved
    val hadFocus = remember { focused }
    var restoring by remember { mutableStateOf(hadFocus) }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(restoring, editModeActive, editModeOpening) {
        if (!restoring) return@LaunchedEffect
        if (editModeActive) {
            // after the first layout, the editor's focus target is there then
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
            restoring = false
        } else if (!editModeOpening) {
            // the edit mode does not come back on its own
            restoring = false
        }
    }
    val modifier = Modifier
        .focusRequester(requester)
        .onFocusChanged {
            // the window gives up the focus when the activity goes away, before its state is saved
            if (!restoring && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) focused = it.hasFocus
        }
    return EditorFocus(modifier, hadFocus)
}

/**
 * @param modifier for the editor
 * @param hadFocus the editor had the focus before the screen was recreated, so the cursor matters more than the line
 * at the top
 */
internal class EditorFocus(val modifier: Modifier, val hadFocus: Boolean)

/** from this width the bar has room for the actions that are toggled or looked at often */
internal const val WIDE_TOP_BAR_DP = 600

/** what the back button and the paddings of the bar take */
private const val TOP_BAR_RESERVED_DP = 64

/** the width of each action in the bar */
private const val TOP_BAR_ACTION_DP = 48

/** the room a title needs to still be read */
internal const val MIN_TITLE_DP = 140

/**
 * The bar keeps what shows a state (connection, users, mode) and the view settings, if the title still fits. The menu
 * holds only actions: on a phone also the references, so that the title still fits.
 *
 * @param widthDp the width of the window
 */
internal fun editorTopBarActions(
    uiState: CodeEditorViewModel.UiState,
    splitMode: SplitMode,
    widthDp: Int,
): List<TopAppBarAction.CodeEditor> {
    val wide = widthDp >= WIDE_TOP_BAR_DP
    val editorShown = uiState.editorShown
    val connection = uiState.connectionIndicator?.let { TopAppBarAction.CodeEditor.ConnectionAction(it) }
    val users = (uiState.remotePeers + uiState.ghosts.map { it.asPeer() }).takeIf { it.isNotEmpty() }
        ?.let { TopAppBarAction.CodeEditor.OnlineUsersAction(it) }
    val references = TopAppBarAction.CodeEditor.PageReferencesAction.takeIf { wide }
    // one place for both, so nothing moves when the mode changes
    val mode = TopAppBarAction.CodeEditor.EditorModeAction(splitMode.takeIf { editorShown })
        .takeIf { editorShown || uiState.canEdit }
    // the settings change only the editor
    val viewSettings = TopAppBarAction.CodeEditor.ViewSettingsAction.takeIf { editorShown }
    // the menu button and the view settings come along
    val actionsWithView = listOfNotNull(connection, users, references, mode).size + 2
    val viewInBar = wide || widthDp - TOP_BAR_RESERVED_DP - actionsWithView * TOP_BAR_ACTION_DP >= MIN_TITLE_DP

    val menu = withoutStrayDividers(
        listOfNotNull(
            viewSettings.takeIf { !viewInBar },
            TopAppBarAction.CodeEditor.PageReferencesAction.takeIf { !wide },
            TopAppBarAction.CodeEditor.MenuDivider,
            TopAppBarAction.CodeEditor.ShareLinkAction,
            TopAppBarAction.CodeEditor.ShowInBrowserAction,
            TopAppBarAction.CodeEditor.MenuDivider,
            TopAppBarAction.CodeEditor.RenameAction,
            TopAppBarAction.CodeEditor.MoveAction,
            TopAppBarAction.CodeEditor.PinAction,
            TopAppBarAction.CodeEditor.DeleteAction,
        )
    )
    return listOfNotNull(
        connection,
        users,
        viewSettings.takeIf { viewInBar },
        references,
        mode,
        TopAppBarAction.CodeEditor.MoreAction(menu),
    )
}

/** a divider only between two groups, not at the start, the end or next to another one */
private fun withoutStrayDividers(entries: List<TopAppBarAction.CodeEditor>): List<TopAppBarAction.CodeEditor> =
    entries.fold(mutableListOf<TopAppBarAction.CodeEditor>()) { kept, entry ->
        val divider = entry is TopAppBarAction.CodeEditor.MenuDivider
        if (!divider || (kept.isNotEmpty() && kept.last() !is TopAppBarAction.CodeEditor.MenuDivider)) kept += entry
        kept
    }.dropLastWhile { it is TopAppBarAction.CodeEditor.MenuDivider }

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
                ConnectionIndicator.NotConnected -> MaterialDesignIconic.Icon.gmi_cloud_off
            },
            contentDescription = stringResource(
                when (indicator) {
                    ConnectionIndicator.Connected -> R.string.connection_connected
                    ConnectionIndicator.UnsyncedChanges -> R.string.unsynced_changes
                    ConnectionIndicator.NotConnected -> R.string.connection_not_connected
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
internal fun ViewSettingsAction(
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier.testTag(VIEW_SETTINGS_ACTION_TAG),
        onClick = onClick
    ) {
        Image(
            asset = GoogleMaterial.Icon.gmd_tune,
            contentDescription = stringResource(R.string.editor_view_settings),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

internal const val VIEW_SETTINGS_ACTION_TAG = "view-settings-action"

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
    TopAppBarAction.CodeEditor.ViewSettingsAction -> stringResource(R.string.editor_view_settings)
    TopAppBarAction.CodeEditor.PageReferencesAction -> stringResource(R.string.page_references_action)
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
    if (uiState.isDocumentDeletedOnServer) EditorBanner(stringResource(R.string.document_deleted_on_server))
}

/**
 * Nothing to read yet, so this takes the place of the empty page (docs/error-handling.md). It covers the editor
 * instead of replacing it, so the editor keeps its state for the text that comes with the connection.
 */
@Composable
private fun NotOnDeviceView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = stringResource(R.string.no_offline_version), textAlign = TextAlign.Center)
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
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