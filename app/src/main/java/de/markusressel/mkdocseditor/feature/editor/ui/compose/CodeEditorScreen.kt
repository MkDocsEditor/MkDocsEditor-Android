package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
    documentId: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    BackHandler(
        enabled = true,
        onBack = onBack,
    )

    // TODO: in the future this has to come from the viewmodel/documentsyncmanager
    var text by remember {
        mutableStateOf(TextFieldValue(""))
    }

    Column(
        modifier = modifier,
    ) {
        CodeEditorLayout(
            modifier = Modifier.fillMaxSize(),
            text = text,
            onTextChanged = {
                text = it
            },
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

@Composable
private fun CodeEditorLayout(
    text: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    KodeTextField(
        modifier = modifier,
        value = text,
        languageRuleBook = MarkdownRuleBook(),
        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
        onValueChange = onTextChanged,
    )
}
