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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.util.compose.CombinedPreview


@Composable
internal fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    onTextChanged: (TextFieldValue) -> Unit,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    var tfv: TextFieldValue by remember(uiState.documentId) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = uiState.text ?: AnnotatedString(""),
                selection = uiState.selection ?: TextRange.Zero,
            )
        )
    }

    remember(uiState.text, uiState.selection) {
        tfv = tfv.copy(
            annotatedString = uiState.text ?: AnnotatedString(""),
            selection = uiState.selection ?: TextRange.Zero,
        )
        derivedStateOf { true }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(CodeEditorViewModel.UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
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
                Snackbar(
                    modifier = Modifier.padding(4.dp),
                    action = {
                        TextButton(onClick = {
                            onUiEvent(CodeEditorViewModel.UiEvent.SnackbarActionClicked(uiState.snackbar))
                        }) {
                            Text(text = uiState.snackbar.action)
                        }
                    }
                ) {
                    Text(text = uiState.snackbar.text)
                }
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
                ScreenTitle(title = uiState.title)

                if (uiState.isOfflineModeBannerVisible) {
                    OfflineModeBanner()
                }

                CodeEditorLayout(
                    modifier = Modifier.fillMaxSize(),
                    text = tfv,
                    onTextChanged = {
                        onTextChanged(it)
                        tfv = it
                    },
                    readOnly = uiState.editModeActive.not()
                )
            }
        }
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
                snackbar = SnackbarData(
                    text = "Connection failed",
                    action = "Retry"
                ),
            ),
            onTextChanged = {},
            onUiEvent = {},
        )
    }
}