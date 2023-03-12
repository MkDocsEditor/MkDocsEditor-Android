package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle


@Composable
internal fun CodeEditorLayout(
    text: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    KodeEditor(
        modifier = modifier,
        text = text,
        languageRuleBook = MarkdownRuleBook(),
        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
        onValueChange = onTextChanged,
        colors = KodeEditorDefaults.editorColors(
            textFieldBackgroundColor = MaterialTheme.colorScheme.background,
            lineNumberTextColor = MaterialTheme.colorScheme.onBackground,
            lineNumberBackgroundColor = MaterialTheme.colorScheme.background,
            textFieldColors = KodeTextFieldDefaults.textFieldColors(
                textColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        ),
        enabled = true,
        readOnly = readOnly
    )
}
