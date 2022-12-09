package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.ui.KodeTextField
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import de.markusressel.mkdocseditor.ui.activity.UiState


@Composable
internal fun CodeEditorScreen(
    uiState: UiState,
    documentId: String
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CodeEditorLayout(
            text = TextFieldValue(""),
            onTextChanged = {},
        )

        AnimatedVisibility(visible = uiState.snackbar != null) {
            Box(
                modifier = Modifier
                    .wrapContentHeight()
                    .align(Alignment.BottomCenter)
            ) {
                Snackbar(
                    modifier = Modifier.padding(4.dp),
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
}

@Composable
private fun CodeEditorLayout(
    text: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
) {
    KodeTextField(
        modifier = Modifier.fillMaxSize(),
        value = text,
        languageRuleBook = MarkdownRuleBook(),
        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
        onValueChange = onTextChanged,
    )
}
