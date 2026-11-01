package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorColorScheme
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.MkDocsMarkdownRuleBook
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer


@Composable
internal fun CodeEditorLayout(
    text: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    remotePeers: List<RemotePeer> = emptyList(),
) {
    val palette = rememberEditorPalette()
    val colorScheme = remember(palette) { EditorColorScheme(palette) }
    val ruleBook = remember { MkDocsMarkdownRuleBook() }

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.primary,
            backgroundColor = palette.selection,
        )
    ) {
        KodeEditor(
            modifier = modifier,
            text = text,
            languageRuleBook = ruleBook,
            colorScheme = colorScheme,
            onValueChange = onTextChanged,
            colors = KodeEditorDefaults.editorColors(
                textFieldBackgroundColor = palette.background,
                lineNumberTextColor = palette.lineNumber,
                lineNumberBackgroundColor = palette.background,
                textFieldColors = KodeTextFieldDefaults.textFieldColors(
                    textColor = palette.text,
                    cursorColor = palette.cursor,
                )
            ),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
            ),
            enabled = true,
            readOnly = readOnly,
            overlay = { textLayoutResult ->
                RemotePeersOverlay(
                    textLayoutResult = textLayoutResult,
                    text = text.text,
                    remotePeers = remotePeers,
                    background = palette.background,
                )
            }
        )
    }
}

/**
 * The editor palette that matches the app theme (not only the system setting)
 */
@Composable
internal fun rememberEditorPalette(): EditorPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return EditorPalette.forDarkTheme(dark)
}
