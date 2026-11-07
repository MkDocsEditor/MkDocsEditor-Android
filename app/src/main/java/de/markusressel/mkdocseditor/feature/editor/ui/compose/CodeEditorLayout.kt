package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.mkdocseditor.feature.editor.domain.ChangeSection
import kotlinx.coroutines.flow.takeWhile
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.KodeEditorViewState
import de.markusressel.kodeeditor.library.compose.rememberKodeEditorViewState
import de.markusressel.mkdocseditor.R
import android.os.SystemClock
import de.markusressel.mkdocseditor.feature.editor.domain.FOLLOW_PAUSE_MIN_MS
import de.markusressel.mkdocseditor.feature.editor.domain.EditorScrollView
import de.markusressel.mkdocseditor.feature.editor.domain.FollowTarget
import de.markusressel.mkdocseditor.feature.editor.domain.lineOfPosition
import de.markusressel.mkdocseditor.feature.editor.domain.SyncAnchor
import de.markusressel.mkdocseditor.feature.editor.domain.editorTargetOf
import de.markusressel.mkdocseditor.feature.editor.domain.lineCountOf
import kotlinx.coroutines.flow.first
import de.markusressel.mkdocseditor.feature.editor.domain.nextFollowPauseMs
import de.markusressel.mkdocseditor.feature.editor.domain.followTarget
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.withTimeoutOrNull
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
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.MkDocsFoldProvider
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
    localColor: String? = null,
    onStopFollowing: () -> Unit = {},
    onVisibleRangeChanged: (TextRange?) -> Unit = {},
    scrollSync: ScrollSync? = null,
    restoreToCursor: Boolean = false,
    changeJump: CodeEditorViewModel.ChangeJump? = null,
    onChangeJumpShown: (CodeEditorViewModel.ChangeJump) -> Unit = {},
) {
    val viewState = rememberKodeEditorViewState()
    val currentOnVisibleRangeChanged by rememberUpdatedState(onVisibleRangeChanged)
    LaunchedEffect(viewState, scrollSync) {
        snapshotFlow { viewState.visibleRange }.collect { range ->
            currentOnVisibleRangeChanged(range)
        }
    }
    LaunchedEffect(viewState, scrollSync) {
        if (scrollSync == null) return@LaunchedEffect
        val view = viewState.asScrollView()
        snapshotFlow { scrollSync.editorAnchorOf(view) { lineOfPosition(state.text, state.selection.end) } }
            .collect { scrollSync.onEditorAnchor(it) }
    }
    LaunchedEffect(viewState, scrollSync) {
        if (scrollSync == null) return@LaunchedEffect
        // the page reports every frame of its scroll, so the editor jumps along with it
        snapshotFlow { scrollSync.editorTarget }.filterNotNull().conflate().collect { target ->
            viewState.scrollToLine(target.line, target.fraction, target.viewportFraction, animate = false)
        }
    }
    LaunchedEffect(viewState, scrollSync) {
        if (scrollSync == null) return@LaunchedEffect
        snapshotFlow { viewState.lineAt(0f)?.let { it.line + it.fraction.toDouble() } }
            .collect { scrollSync.onEditorTop(it) }
    }
    LaunchedEffect(viewState, scrollSync) {
        val line = scrollSync?.editorRestore ?: return@LaunchedEffect
        // after a recreation: back to the line at the top, once the editor is laid out and has the text, which comes
        // later after process death
        withTimeoutOrNull(RESTORE_WAIT_MS) {
            snapshotFlow { viewState.visibleRange != null && lineCountOf(state.text) > line.toInt() }.first { it }
            if (restoreToCursor) {
                // the editor gets its focus back, and the keyboard opens at the cursor
                viewState.scrollToPosition(state.selection.end, animate = false)
            } else {
                val target = editorTargetOf(SyncAnchor(line, 0f), lineCountOf(state.text))
                viewState.scrollToLine(target.line, target.fraction, target.viewportFraction, animate = false)
            }
        }
        scrollSync.onEditorRestored()
    }
    LaunchedEffect(jumpRequest) {
        jumpRequest?.let { viewState.scrollToPosition(it.position, animate = false) }
    }
    val currentOnChangeJumpShown by rememberUpdatedState(onChangeJumpShown)
    var changeHighlight by remember { mutableStateOf<ChangeSection?>(null) }
    LaunchedEffect(changeJump) {
        val jump = changeJump ?: return@LaunchedEffect
        val line = jump.section.startLine
        withTimeoutOrNull(RESTORE_WAIT_MS) {
            snapshotFlow { viewState.visibleRange != null && lineCountOf(state.text) > line }.first { it }
            // the page of a split follows the editor there
            scrollSync?.onEditorDriving()
            viewState.scrollToLine(line, animate = false)
        }
        changeHighlight = jump.section
        currentOnChangeJumpShown(jump)
    }
    LaunchedEffect(changeHighlight) {
        val section = changeHighlight ?: return@LaunchedEffect
        // the first layouts of an editor that has just opened move its view back to the top, like for following
        withTimeoutOrNull(OPENING_SETTLE_MS) {
            snapshotFlow { viewState.visibleRange to viewState.isUserMovingView }
                .takeWhile { (_, moving) -> !moving }
                .collect {
                    if (viewState.lineAt(0f)?.line != section.startLine) viewState.scrollToLine(section.startLine, animate = false)
                }
        }
    }
    val changeHighlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(changeHighlight) {
        changeHighlight ?: return@LaunchedEffect
        changeHighlightAlpha.snapTo(1f)
        delay(CHANGE_HIGHLIGHT_HOLD_MS)
        changeHighlightAlpha.animateTo(0f, tween(CHANGE_HIGHLIGHT_FADE_MS))
        changeHighlight = null
    }

    // moving the view by hand only pauses following, like in the navigation of a map
    var followPaused by remember(following?.instanceId) { mutableStateOf(false) }
    var followPauseMs by remember(following?.instanceId) { mutableLongStateOf(FOLLOW_PAUSE_MIN_MS) }
    var followResumedAt by remember(following?.instanceId) { mutableStateOf<Long?>(null) }
    // moving the page of a split moves the editor, so it pauses following as well
    val previewMoves = scrollSync?.previewMovedByUser ?: 0
    LaunchedEffect(previewMoves) {
        if (previewMoves > 0 && following != null && !followPaused) {
            followPauseMs = nextFollowPauseMs(followPauseMs, followResumedAt?.let { SystemClock.uptimeMillis() - it })
            followPaused = true
        }
    }
    LaunchedEffect(followPaused, viewState.isUserMovingView, previewMoves) {
        if (followPaused && !viewState.isUserMovingView) {
            delay(followPauseMs)
            followPaused = false
            followResumedAt = SystemClock.uptimeMillis()
        }
    }
    val followTarget = following?.let { followTarget(remotePeers.value, ghosts, it.instanceId) }
    LaunchedEffect(followTarget, followPaused) {
        if (followPaused || followTarget == null) return@LaunchedEffect
        // the page of a split shows what is followed as well
        scrollSync?.onEditorDriving()
        if (viewState.visibleRange != null) {
            viewState.scrollTo(followTarget, animate = true)
            return@LaunchedEffect
        }
        // an editor that following has just opened grows in its first layouts, and they move its view back to the top
        withTimeoutOrNull(OPENING_SETTLE_MS) {
            snapshotFlow { viewState.visibleRange }.collect { visible ->
                if (!viewState.isUserMovingView && (visible == null || !followTarget.isIn(visible))) {
                    viewState.scrollTo(followTarget, animate = false)
                }
            }
        }
    }


    val palette = rememberEditorPalette()
    val highlightColor = MaterialTheme.colorScheme.primary
    val colorScheme = remember(palette) { EditorColorScheme(palette) }
    val ownColor = parsePeerColor(localColor)?.readableOn(palette.background)
    val ruleBook = remember { MkDocsMarkdownRuleBook() }
    val colors = KodeEditorDefaults.editorColors(
        textFieldBackgroundColor = palette.background,
        lineNumberTextColor = palette.lineNumber,
        lineNumberBackgroundColor = palette.background,
        textFieldColors = KodeTextFieldDefaults.textFieldColors(
            textColor = palette.text,
            cursorColor = ownColor ?: MaterialTheme.colorScheme.primary,
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
            handleColor = ownColor ?: MaterialTheme.colorScheme.primary,
            backgroundColor = ownColor?.copy(alpha = 0.3f) ?: palette.selection,
        )
    ) {
        Box(modifier = modifier) {
        KodeEditor(
            modifier = Modifier.fillMaxSize(),
            state = state,
            viewState = viewState,
            onUserMovedView = {
                scrollSync?.onEditorDriving()
                if (following != null && !followPaused) {
                    followPauseMs = nextFollowPauseMs(followPauseMs, followResumedAt?.let { SystemClock.uptimeMillis() - it })
                    followPaused = true
                }
            },
            // following must not fight the local cursor when an edit of someone else moves it
            keepCursorVisible = following == null,
            languageRuleBook = ruleBook,
            // the rule book has no language, so the editor would not know what to fold
            foldProvider = MkDocsFoldProvider,
            colorScheme = colorScheme,
            colors = colors,
            textStyle = textStyle,
            enabled = true,
            readOnly = readOnly,
            lineWrapping = settings.lineWrapping,
            gutterColumns = listOfNotNull(lineNumbers.takeIf { settings.lineNumbers }, authorStrip),
            overlay = { layout ->
                changeHighlight?.let { ChangeHighlight(layout, it, highlightColor, { changeHighlightAlpha.value }) }
                if (settings.authorHighlight == AuthorHighlightMode.StripAndMarker) {
                    AuthorMarkers(layout = layout, runs = authorRuns.value.runs, colorOf = colorOf)
                }
                RemotePeersOverlay(
                    layout = layout,
                    remotePeers = remotePeers.value,
                    background = palette.background,
                    foreground = palette.text,
                )
                RemotePeersOverlay(
                    layout = layout,
                    remotePeers = ghosts.map { it.asPeer() },
                    background = palette.background,
                    foreground = palette.text,
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

/** a band behind the lines of [section], for the moment after the editor went there */
@Composable
private fun BoxScope.ChangeHighlight(layout: KodeEditorLayout, section: ChangeSection, color: Color, alpha: () -> Float) {
    Canvas(modifier = Modifier.matchParentSize()) {
        val last = minOf(section.endLine, layout.lineCount) - 1
        if (last < section.startLine) return@Canvas
        val top = layout.getLineTop(section.startLine)
        val bottom = layout.getLineBottom(last)
        drawRect(
            color = color.copy(alpha = CHANGE_HIGHLIGHT_ALPHA * alpha()),
            topLeft = Offset(0f, top),
            size = Size(maxOf(size.width, layout.contentSize.width.toFloat()), bottom - top),
        )
    }
}

private fun FollowTarget.isIn(visible: TextRange): Boolean = when (this) {
    is FollowTarget.Range -> (top + bottom) / 2 in visible.start..visible.end
    is FollowTarget.Position -> position in visible.start..visible.end
}

private fun KodeEditorViewState.asScrollView() = object : EditorScrollView {
    override val scrollOffset: Float? get() = verticalScrollOffset
    override val maxScrollOffset: Float? get() = maxVerticalScrollOffset
    override fun lineAt(viewportFraction: Float): Double? =
        this@asScrollView.lineAt(viewportFraction)?.let { it.line + it.fraction.toDouble() }
}

private suspend fun KodeEditorViewState.scrollTo(target: FollowTarget, animate: Boolean): Boolean = when (target) {
    is FollowTarget.Range -> scrollToRange(target.top, target.bottom + 1, animate)
    is FollowTarget.Position -> scrollToPosition(target.position, animate)
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
    /** whether editor and rendered page of a split scroll together */
    val scrollSync: Boolean = true,
)

private val NoAuthorRuns: State<AuthorRuns> = mutableStateOf(AuthorRuns())
private val AUTHOR_STRIP_WIDTH = 3.dp
private const val GHOST_ALPHA = 0.4f
private const val OPENING_SETTLE_MS = 1_000L

/** a changed section the editor went to lights up this long, then fades */
private const val CHANGE_HIGHLIGHT_HOLD_MS = 1_400L
private const val CHANGE_HIGHLIGHT_FADE_MS = 600
private const val CHANGE_HIGHLIGHT_ALPHA = 0.2f

/** how long the editor waits for its text to go back to its line after a recreation */
private const val RESTORE_WAIT_MS = 10_000L
