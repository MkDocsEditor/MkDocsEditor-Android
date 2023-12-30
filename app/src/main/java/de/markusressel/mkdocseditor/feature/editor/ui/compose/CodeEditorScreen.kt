package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.UiState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

internal data class CodeEditorScreen(
    private val documentId: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel: CodeEditorViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = true,
            onBack = { navigator.pop() },
        )

        LaunchedEffect(documentId) {
            viewModel.loadDocument(documentId)
        }

        CodeEditorScreenContent(
            modifier = Modifier,
            uiState = uiState,
            onTextChanged = {
                viewModel.onUserTextInput(
                    it.annotatedString,
                    it.selection
                )
            }
        )
    }
}

@Composable
private fun CodeEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: UiState,
    onTextChanged: (TextFieldValue) -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(modifier),
    ) {
        CodeEditorLayout(
            modifier = Modifier
                .fillMaxSize(),
            text = tfv,
            onTextChanged = {
                onTextChanged(it)
                tfv = it
            },
            readOnly = uiState.editModeActive.not()
        )

        AnimatedVisibility(
            visible = uiState.snackbar != null,
            enter = slideInVertically(),
            exit = slideOutVertically(),
        ) {
            Snackbar(
                modifier = Modifier
                    .padding(4.dp),
                action = {
                    TextButton(onClick = {
                        // onUiEvent(UiEvent.SnackbarActionTriggered)
                    }) {
                        Text(text = uiState.snackbar?.action ?: "")
                    }
                }
            ) {
                Text(text = uiState.snackbar?.text ?: "")
            }
        }
    }
}

@CombinedPreview
@Composable
private fun CodeEditorScreenContentPreview() {
    MkDocsEditorTheme {
        CodeEditorScreenContent(
            uiState = UiState(
                text = buildAnnotatedString { append("# Hallo Welt!") }
            ),
            onTextChanged = {}
        )
    }
}