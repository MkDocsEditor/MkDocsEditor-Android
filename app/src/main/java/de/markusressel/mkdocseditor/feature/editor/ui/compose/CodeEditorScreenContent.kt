package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.coerceIn
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.EditorField
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.EnableEditModeFabConfig
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.MarkdownPreview
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.PreviewResourceLoader

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    editorField: EditorField,
    onTextChanged: (TextFieldValue) -> Unit,
    onValueShown: (Long) -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    previewResourceLoader: PreviewResourceLoader = PreviewResourceLoader { null },
) {
    var splitOrientation by remember { mutableStateOf(SplitOrientation.Vertical) }

    val chrome = remember { EditorChromeState() }
    val keyboardVisible = WindowInsets.isImeVisible
    chrome.shortScreen = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_HEIGHT_DP
    SideEffect { chrome.keyboardVisible = keyboardVisible }

    val onEditorValueChanged: (TextFieldValue) -> Unit = { value ->
        if (value.text != editorField.value.text) chrome.onTextEdited()
        onTextChanged(value)
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
                        actions = listOfNotNull(
                            TopAppBarAction.CodeEditor.OnlineUsersAction(uiState.remotePeers)
                                .takeIf { uiState.remotePeers.isNotEmpty() },
                            TopAppBarAction.CodeEditor.PageReferencesAction,
                            TopAppBarAction.CodeEditor.TogglePreviewAction(splitOrientation),
                            TopAppBarAction.CodeEditor.ShowInBrowserAction
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
            AnimatedVisibility(
                visible = uiState.isBottomAppBarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                CodeEditorBottomBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (keyboardVisible) Modifier.height(COMPACT_TOOLBAR_HEIGHT) else Modifier),
                    uiState = uiState,
                    onUiEvent = onUiEvent,
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
                .observeDragDown { chrome.onDraggedDown() }
                .onGloballyPositioned { layoutCoordinates ->
                    val width = layoutCoordinates.size.width
                    val height = layoutCoordinates.size.height
                    splitOrientation = when {
                        width > height -> SplitOrientation.Horizontal
                        else -> SplitOrientation.Vertical
                    }
                },
        ) {
            when (splitOrientation) {
                SplitOrientation.Vertical -> {
                    VerticalSplit(
                        uiState = uiState,
                        editorField = editorField,
                        onTextChanged = onEditorValueChanged,
                        onValueShown = onValueShown,
                        onFocusChanged = onFocusChanged,
                        onUiEvent = onUiEvent,
                        previewResourceLoader = previewResourceLoader,
                    )
                }

                SplitOrientation.Horizontal -> {
                    HorizontalSplit(
                        uiState = uiState,
                        editorField = editorField,
                        onTextChanged = onEditorValueChanged,
                        onValueShown = onValueShown,
                        onFocusChanged = onFocusChanged,
                        onUiEvent = onUiEvent,
                        previewResourceLoader = previewResourceLoader,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerticalSplit(
    uiState: CodeEditorViewModel.UiState,
    editorField: EditorField,
    onTextChanged: (TextFieldValue) -> Unit,
    onValueShown: (Long) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    previewResourceLoader: PreviewResourceLoader,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Markdown Editor
        Column(
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.isOfflineModeBannerVisible) {
                OfflineModeBanner()
            }

            if (uiState.editModeActive) {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    field = editorField,
                    onTextChanged = onTextChanged,
                    onValueShown = onValueShown,
                    onFocusChanged = onFocusChanged,
                    readOnly = false,
                )
            } else {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    field = editorField,
                    onTextChanged = onTextChanged,
                    onValueShown = onValueShown,
                    onFocusChanged = onFocusChanged,
                    readOnly = true,
                )
            }
        }

        // Page Preview
        AnimatedVisibility(
            modifier = Modifier.weight(1f),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            visible = uiState.isPreviewVisible
        ) {
            MarkdownPreview(
                modifier = Modifier.fillMaxWidth(),
                text = editorField.value.text,
                sectionPath = uiState.previewSectionPath,
                resourceLoader = previewResourceLoader,
                onLinkClicked = { onUiEvent(CodeEditorViewModel.UiEvent.PreviewLinkClicked(it)) },
            )
        }
    }
}

@Composable
internal fun HorizontalSplit(
    uiState: CodeEditorViewModel.UiState,
    editorField: EditorField,
    onTextChanged: (TextFieldValue) -> Unit,
    onValueShown: (Long) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    previewResourceLoader: PreviewResourceLoader,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Markdown Editor
        Column(
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.isOfflineModeBannerVisible) {
                OfflineModeBanner()
            }

            if (uiState.editModeActive) {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    field = editorField,
                    onTextChanged = onTextChanged,
                    onValueShown = onValueShown,
                    onFocusChanged = onFocusChanged,
                    readOnly = false,
                )
            } else {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    field = editorField,
                    onTextChanged = onTextChanged,
                    onValueShown = onValueShown,
                    onFocusChanged = onFocusChanged,
                    readOnly = true,
                )
            }
        }

        // Page Preview
        AnimatedVisibility(
            modifier = Modifier.weight(1f),
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            visible = uiState.isPreviewVisible
        ) {
            MarkdownPreview(
                modifier = Modifier.fillMaxHeight(),
                text = editorField.value.text,
                sectionPath = uiState.previewSectionPath,
                resourceLoader = previewResourceLoader,
                onLinkClicked = { onUiEvent(CodeEditorViewModel.UiEvent.PreviewLinkClicked(it)) },
            )
        }
    }
}

private val COMPACT_TOOLBAR_HEIGHT = 56.dp
private const val SHORT_SCREEN_HEIGHT_DP = 480

enum class SplitOrientation {
    Vertical,
    Horizontal
}


@Composable
internal fun TogglePreviewAction(
    modifier: Modifier,
    orientation: SplitOrientation,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Image(
            asset = when (orientation) {
                SplitOrientation.Vertical -> GoogleMaterial.Icon.gmd_horizontal_split
                SplitOrientation.Horizontal -> GoogleMaterial.Icon.gmd_vertical_split
            },
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun ShowInBrowserAction(
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Image(
            asset = MaterialDesignIconic.Icon.gmi_open_in_browser,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
internal fun CodeEditorSackbar(
    snackbar: SnackbarData,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    val text = when (snackbar) {
        is SnackbarData.ConnectionFailed -> stringResource(R.string.code_editor_connection_failed)
        is SnackbarData.Disconnected -> stringResource(R.string.code_editor_disconnected)
        is SnackbarData.SignedOut -> stringResource(R.string.code_editor_signed_out)
        is SnackbarData.Message -> snackbar.text
    }

    val action: (@Composable () -> Unit)? = when (snackbar) {
        is SnackbarData.Message -> null

        is SnackbarData.ConnectionFailed -> {
            {
                TextButton(onClick = {
                    onUiEvent(CodeEditorViewModel.UiEvent.SnackbarActionClicked(snackbar))
                }) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }

        is SnackbarData.Disconnected -> {
            {
                TextButton(onClick = {
                    onUiEvent(CodeEditorViewModel.UiEvent.SnackbarActionClicked(snackbar))
                }) {
                    Text(text = stringResource(R.string.connect))
                }
            }
        }

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

@Composable
internal fun OfflineModeBanner(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                when (isSystemInDarkTheme()) {
                    true -> colorResource(R.color.md_deep_orange_800)
                    false -> colorResource(R.color.md_deep_orange_300)
                }
            )
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.offline)
        )
    }
}

@CombinedPreview
@Composable
private fun CodeEditorScreenContentOfflinePreview() {
    MkDocsEditorTheme {
        CodeEditorScreenContent(
            uiState = CodeEditorViewModel.UiState(
                text = buildAnnotatedString { append("# Hallo Welt!") },
                isOfflineModeBannerVisible = true,
                snackbar = SnackbarData.ConnectionFailed,
                fabConfig = FabConfig(
                    right = listOf(EnableEditModeFabConfig)
                ),
            ),
            editorField = EditorField(),
            onTextChanged = {},
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
                text = buildAnnotatedString { append("# Hallo Welt!") },
                isOfflineModeBannerVisible = false,
                fabConfig = FabConfig(
                    right = listOf(EnableEditModeFabConfig)
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
            editorField = EditorField(),
            onTextChanged = {},
            onUiEvent = {},
        )
    }
}