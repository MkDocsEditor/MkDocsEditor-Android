package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.coerceIn
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.EnableEditModeFabConfig
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    onTextChanged: (TextFieldValue) -> Unit,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
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

    Scaffold(
        modifier = modifier,
        topBar = {
            MkDocsEditorTopAppBar<TopAppBarAction.CodeEditor>(
                title = uiState.title,
                actions = listOf(
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
        ) {
            Column {
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
        }
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
private fun CodeEditorScreenContentPreview() {
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
        )
    }
}