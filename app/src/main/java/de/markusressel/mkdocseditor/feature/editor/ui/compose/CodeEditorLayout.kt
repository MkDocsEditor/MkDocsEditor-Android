package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.lineShares
import androidx.compose.ui.unit.sp
import de.markusressel.kodeeditor.library.compose.rememberLineNumberColumn
import de.markusressel.mkdocseditor.feature.preferences.data.DEFAULT_EDITOR_TEXT_SIZE
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorColorScheme
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.MkDocsMarkdownRuleBook
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

internal val NoRemotePeers: State<List<RemotePeer>> = mutableStateOf(emptyList())

@Composable
internal fun CodeEditorLayout(
    state: KodeEditorState,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    remotePeers: State<List<RemotePeer>> = NoRemotePeers,
    settings: EditorSettings = EditorSettings(),
    authorRuns: State<AuthorRuns> = NoAuthorRuns,
    authorColors: () -> Map<String, String?> = { emptyMap() },
) {
    val palette = rememberEditorPalette()
    val colorScheme = remember(palette) { EditorColorScheme(palette) }
    val ruleBook = remember { MkDocsMarkdownRuleBook() }
    val colors = KodeEditorDefaults.editorColors(
        textFieldBackgroundColor = palette.background,
        lineNumberTextColor = palette.lineNumber,
        lineNumberBackgroundColor = palette.background,
        textFieldColors = KodeTextFieldDefaults.textFieldColors(
            textColor = palette.text,
            cursorColor = MaterialTheme.colorScheme.primary,
        )
    )
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = settings.textSize.sp,
    )
    val lineNumbers = rememberLineNumberColumn(textStyle, colors, enabled = true)
    val colorOf: (String) -> Color = { authorColor(it, authorColors(), palette.background) }
    val stripWidth = with(LocalDensity.current) { AUTHOR_STRIP_WIDTH.roundToPx() }
    val runs = authorRuns.value
    val authorStrip = if (settings.authorHighlight != AuthorHighlightMode.Off && runs.runs.isNotEmpty()) {
        remember(runs, palette, stripWidth) {
            AuthorStripColumn(lineShares(state.text, runs.runs), stripWidth, colorOf)
        }
    } else {
        null
    }

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.primary,
            backgroundColor = palette.selection,
        )
    ) {
        KodeEditor(
            modifier = modifier,
            state = state,
            languageRuleBook = ruleBook,
            colorScheme = colorScheme,
            colors = colors,
            textStyle = textStyle,
            enabled = true,
            readOnly = readOnly,
            lineWrapping = settings.lineWrapping,
            gutterColumns = listOfNotNull(lineNumbers.takeIf { settings.lineNumbers }, authorStrip),
            overlay = { layout ->
                if (settings.authorHighlight == AuthorHighlightMode.StripAndMarker) {
                    AuthorMarkers(layout = layout, runs = authorRuns.value.runs, colorOf = colorOf)
                }
                RemotePeersOverlay(
                    layout = layout,
                    remotePeers = remotePeers.value,
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

/** How the code editor looks, from the settings */
internal data class EditorSettings(
    val lineWrapping: Boolean = true,
    val lineNumbers: Boolean = true,
    val textSize: Int = DEFAULT_EDITOR_TEXT_SIZE.toInt(),
    val authorHighlight: AuthorHighlightMode = AuthorHighlightMode.StripAndMarker,
)

private val NoAuthorRuns: State<AuthorRuns> = mutableStateOf(AuthorRuns())
private val AUTHOR_STRIP_WIDTH = 3.dp
