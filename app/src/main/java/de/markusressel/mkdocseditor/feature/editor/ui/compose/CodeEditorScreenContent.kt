package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.EnableEditModeFabConfig
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
internal fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    onTextChanged: (TextFieldValue) -> Unit,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    webViewActionFlow: Flow<WebViewAction>,
) {
    var splitOrientation by remember { mutableStateOf(SplitOrientation.Vertical) }

    var tfv: TextFieldValue by remember(uiState.documentId) {
        val text = uiState.text ?: AnnotatedString("")
        val selection = uiState.selection?.coerceIn(0, text.length) ?: TextRange.Zero
        mutableStateOf(
            TextFieldValue(
                annotatedString = text,
                selection = selection,
            )
        )
    }

    remember(uiState.text, uiState.selection) {
        val text = uiState.text ?: AnnotatedString("")
        val selection = uiState.selection?.coerceIn(0, text.length) ?: TextRange.Zero

        tfv = tfv.copy(
            annotatedString = text,
            selection = selection,
        )
        derivedStateOf { true }
    }

    TabContentScaffold(
        modifier = modifier,
        topBar = {
            MkDocsEditorTopAppBar(
                title = uiState.title,
                actions = listOf(
                    TopAppBarAction.CodeEditor.TogglePreviewAction(splitOrientation),
                    TopAppBarAction.CodeEditor.ShowInBrowserAction
                ),
                onActionClicked = {
                    onUiEvent(CodeEditorViewModel.UiEvent.TopAppBarActionClicked(it))
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isBottomAppBarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                CodeEditorBottomBar(
                    modifier = Modifier.fillMaxWidth(),
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
                        tfv = tfv,
                        onTextChanged = onTextChanged,
                        webViewActionFlow = webViewActionFlow
                    )
                }

                SplitOrientation.Horizontal -> {
                    HorizontalSplit(
                        uiState = uiState,
                        tfv = tfv,
                        onTextChanged = onTextChanged,
                        webViewActionFlow = webViewActionFlow
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerticalSplit(
    uiState: CodeEditorViewModel.UiState,
    tfv: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    webViewActionFlow: Flow<WebViewAction>
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
                    text = tfv,
                    onTextChanged = onTextChanged,
                    readOnly = false
                )
            } else {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    text = tfv,
                    onTextChanged = onTextChanged,
                    readOnly = true
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
            uiState.webUrl?.let {
                PagePreview(
                    modifier = Modifier.fillMaxWidth(),
                    url = it,
                    actions = webViewActionFlow
                )
            }
        }
    }
}

@Composable
internal fun HorizontalSplit(
    uiState: CodeEditorViewModel.UiState,
    tfv: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    webViewActionFlow: Flow<WebViewAction>
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
                    text = tfv,
                    onTextChanged = onTextChanged,
                    readOnly = false
                )
            } else {
                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    text = tfv,
                    onTextChanged = onTextChanged,
                    readOnly = true
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
            uiState.webUrl?.let {
                PagePreview(
                    modifier = Modifier.fillMaxHeight(),
                    url = it,
                    actions = webViewActionFlow
                )
            }
        }
    }
}

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
    }

    val action: @Composable () -> Unit = when (snackbar) {
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
            onTextChanged = {},
            onUiEvent = {},
            webViewActionFlow = MutableSharedFlow()
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
                webUrl = "https://www.google.com",
                isOfflineModeBannerVisible = false,
                fabConfig = FabConfig(
                    right = listOf(EnableEditModeFabConfig)
                ),
                isBottomAppBarVisible = true
            ),
            onTextChanged = {},
            onUiEvent = {},
            webViewActionFlow = MutableSharedFlow()
        )
    }
}