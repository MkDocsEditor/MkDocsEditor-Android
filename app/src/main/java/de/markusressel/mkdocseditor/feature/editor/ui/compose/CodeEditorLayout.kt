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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.rememberKodeEditorViewState
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.domain.FollowTarget
import de.markusressel.mkdocseditor.feature.editor.domain.followTarget
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import kotlinx.coroutines.delay
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
    jumpRequest: CodeEditorViewModel.JumpRequest? = null,
    ghosts: List<RemoteGhost> = emptyList(),
    following: CodeEditorViewModel.Following? = null,
    followPauseRequest: Int = 0,
    onStopFollowing: () -> Unit = {},
    onVisibleRangeChanged: (TextRange?) -> Unit = {},
) {
    val viewState = rememberKodeEditorViewState()
    LaunchedEffect(viewState) {
        snapshotFlow { viewState.visibleRange }.collect(onVisibleRangeChanged)
    }
    LaunchedEffect(jumpRequest) {
        jumpRequest?.let { viewState.scrollToPosition(it.position, animate = false) }
    }

    // moving the view by hand only pauses following, like in the navigation of a map
    var followPaused by remember(following?.accountId) { mutableStateOf(false) }
    var followerTyping by remember(following?.accountId) { mutableStateOf(false) }
    LaunchedEffect(followPauseRequest) {
        if (following != null && followPauseRequest > 0) {
            followPaused = true
            followerTyping = true
        }
    }
    LaunchedEffect(followPaused, viewState.isUserMovingView, followPauseRequest) {
        if (followPaused && !viewState.isUserMovingView) {
            delay(FOLLOW_RESUME_DELAY_MS)
            followPaused = false
            followerTyping = false
        }
    }
    val followTarget = following?.let { followTarget(remotePeers.value, ghosts, it.accountId) }
    LaunchedEffect(followTarget, followPaused) {
        if (followPaused) return@LaunchedEffect
        when (followTarget) {
            is FollowTarget.Range -> viewState.scrollToRange(followTarget.top, followTarget.bottom + 1)
            is FollowTarget.Position -> viewState.scrollToPosition(followTarget.position)
            null -> Unit
        }
    }

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
    val colorOf: (String) -> Color = { authorColor(it, authorColors()) }
    val stripWidth = with(LocalDensity.current) { AUTHOR_STRIP_WIDTH.roundToPx() }
    val runs = authorRuns.value
    val authorStrip = if (settings.authorHighlight != AuthorHighlightMode.Off && runs.runs.isNotEmpty()) {
        remember(runs, stripWidth) {
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
        Box(modifier = modifier) {
        KodeEditor(
            modifier = Modifier.fillMaxSize(),
            state = state,
            viewState = viewState,
            onUserMovedView = { if (following != null) followPaused = true },
            // following must not fight the local cursor when an edit of someone else moves it, but the follower sees what they type
            keepCursorVisible = following == null || followerTyping,
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
                RemotePeersOverlay(
                    layout = layout,
                    remotePeers = ghosts.map { it.asPeer() },
                    background = palette.background,
                    alpha = GHOST_ALPHA,
                )
            }
        )
        following?.let {
            AssistChip(
                modifier = Modifier.align(Alignment.TopCenter),
                onClick = onStopFollowing,
                label = {
                    Text(stringResource(if (followPaused) R.string.following_user_paused else R.string.following_user, it.name))
                },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.follow_stop))
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        }
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
private const val GHOST_ALPHA = 0.4f
/** same in the web client */
private const val FOLLOW_RESUME_DELAY_MS = 5_000L
