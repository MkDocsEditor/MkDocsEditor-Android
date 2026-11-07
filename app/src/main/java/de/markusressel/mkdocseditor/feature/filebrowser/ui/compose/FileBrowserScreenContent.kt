package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import de.markusressel.mkdocseditor.feature.common.ui.compose.syncStatusText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.horizontalBarsAndCutoutPadding
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.SignInRequiredBanner
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.TransferProgressBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun FileBrowserScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val error = uiState.error
    val errorText = error?.let { stringResource(it.message) }
    val retryText = stringResource(R.string.retry)
    LaunchedEffect(error) {
        if (error == null || errorText == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = errorText,
            actionLabel = retryText.takeIf { error.canRetry },
            withDismissAction = true,
            duration = if (error.canRetry) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        onUiEvent(if (result == SnackbarResult.ActionPerformed) UiEvent.Refresh else UiEvent.DismissError)
    }

    val folderTransition = rememberFolderTransition(uiState, onUiEvent)

    TabContentScaffold(
        topBar = {
            Column {
                MkDocsEditorTopAppBar<TopAppBarAction.FileBrowser>(
                    canGoBack = false,
                    title = stringResource(id = R.string.screen_files_title),
                    subtitle = syncStatusText(uiState.lastSync, uiState.lastSyncFailed),
                    actions = listOf(
                        TopAppBarAction.FileBrowser.Search,
                        TopAppBarAction.FileBrowser.Profile,
                    ),
                    onActionClicked = { action ->
                        onUiEvent(UiEvent.TopAppBarActionClicked(action))
                    }
                )

                if (uiState.loginRequiredBackendId != null) {
                    SignInRequiredBanner(
                        modifier = Modifier.horizontalBarsAndCutoutPadding(),
                        onSignInClicked = { onUiEvent(UiEvent.SignInClicked) }
                    )
                }
            }

        },
        bottomBar = {
            Column {
                uiState.transfer?.let { transfer ->
                    TransferProgressBar(progress = transfer, onCancel = { onUiEvent(UiEvent.CancelTransfer) })
                }
                SectionPath(
                    modifier = Modifier.fillMaxWidth(),
                    path = uiState.currentSectionPath,
                    onSectionClicked = { section -> onUiEvent(UiEvent.PathItemClicked(section)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
            ExpandableFab(
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
        }
    ) { paddingValues ->
        val state = rememberPullToRefreshState()
        val folderScrollStates = rememberSaveableStateHolder()

        Box(
            Modifier.padding(paddingValues)
        ) {
            PullToRefreshBox(
                modifier = Modifier.align(Alignment.TopCenter),
                state = state,
                isRefreshing = uiState.isLoading,
                onRefresh = {
                    onUiEvent(UiEvent.Refresh)
                }
            ) {
                rememberTransition(folderTransition, label = "folder").AnimatedContent(
                    // a refresh of the same folder does not animate
                    contentKey = { it?.sectionId },
                    transitionSpec = {
                        folderTransition(
                            from = initialState?.sectionId,
                            to = targetState?.sectionId,
                            path = uiState.currentSectionPath,
                        )
                    },
) { folder ->
                    // each folder keeps its own position. It follows the content and not the path,
                    // because the content of the last folder stays until the next one has loaded
                    folderScrollStates.SaveableStateProvider(folder?.sectionId ?: NO_FOLDER) {
                        val folderError = uiState.folderError.takeIf { folder == null }
                        if (folderError != null) {
                            FolderErrorView(
                                message = stringResource(folderError),
                                onRetry = { onUiEvent(UiEvent.Refresh) },
                            )
                        } else {
                            FileBrowserList(
                                modifier = Modifier.fillMaxSize(),
                                state = rememberLazyListState(),
                                content = folder,
                                isLoading = uiState.isLoading,
                                unsyncedDocumentIds = uiState.unsyncedDocumentIds,
                                onDocumentClicked = { onUiEvent(UiEvent.DocumentClicked(it)) },
                                onDocumentLongClicked = { onUiEvent(UiEvent.DocumentLongClicked(it)) },
                                onResourceClicked = { onUiEvent(UiEvent.ResourceClicked(it)) },
                                onResourceLongClicked = { onUiEvent(UiEvent.ResourceLongClicked(it)) },
                                onSectionClicked = { onUiEvent(UiEvent.SectionClicked(it)) },
                                onSectionLongClicked = { onUiEvent(UiEvent.SectionLongClicked(it)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nothing to read here yet, so the failure takes the place of the content. It scrolls, for pull to refresh. */
@Composable
private fun FolderErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = message, textAlign = TextAlign.Center)
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

/** A back gesture in a subfolder, from its start until the parent is open */
private data class BackGesture(
    /** the folder that was open when the gesture started */
    val fromId: String?,
    /** the gesture was released, and the parent opens */
    val released: Boolean = false,
)

/**
 * The folder that shows, with its animation. A change of [UiState.folderContent] animates to it. While the back
 * gesture is dragged, the stored content of the parent follows the finger instead, with the same animation.
 */
@Composable
private fun rememberFolderTransition(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
): SeekableTransitionState<FolderContent?> {
    val transition = remember { SeekableTransitionState(uiState.folderContent) }
    var backGesture by remember { mutableStateOf<BackGesture?>(null) }
    val scope = rememberCoroutineScope()
    val content by rememberUpdatedState(uiState.folderContent)
    val parentPreview by rememberUpdatedState(uiState.parentPreview)

    LaunchedEffect(uiState.folderContent, backGesture) {
        val gesture = backGesture
        when {
            gesture == null -> transition.animateTo(uiState.folderContent)
            // the gesture moves the content
            !gesture.released -> Unit
            // the parent shows already, until it is open
            uiState.folderContent?.sectionId == gesture.fromId -> Unit
            else -> backGesture = null
        }
    }

    PredictiveBackHandler(enabled = uiState.canGoUp) { progress ->
        val parent = parentPreview
        // without a stored parent there is nothing to show, so the folder only changes once the gesture is released
        if (parent != null) backGesture = BackGesture(fromId = content?.sectionId)
        var released = false
        try {
            progress.collect { event -> if (parent != null) transition.seekTo(event.progress, parent) }
            released = true
        } finally {
            if (!released && parent != null) {
                scope.launch {
                    transition.seekBackToStart()
                    transition.snapTo(content)
                    backGesture = null
                }
            }
        }
        onUiEvent(UiEvent.NavigateUp)
        if (parent != null) {
            backGesture = backGesture?.copy(released = true)
            // the rest of the way, from where the finger let go
            scope.launch { transition.animateTo(parent) }
        }
    }

    return transition
}

/** Plays a seek back to the start after a cancelled gesture, as fast as the same part of the animation */
private suspend fun SeekableTransitionState<*>.seekBackToStart() {
    val start = fraction
    val startNanos = withFrameNanos { it }
    do {
        val elapsedMs = (withFrameNanos { it } - startNanos) / 1_000_000
        val next = fractionWhileReturning(start, elapsedMs)
        seekTo(next)
    } while (next > 0F)
}

/**
 * @param start how far the gesture had come when it was cancelled
 * @return how far the transition is, [elapsedMs] after the cancel; at the speed of the folder animation, so a short drag
 * returns quickly
 */
internal fun fractionWhileReturning(start: Float, elapsedMs: Long): Float =
    (start - elapsedMs.toFloat() / FOLDER_ANIMATION_MS).coerceIn(0F, start)

/**
 * @return whether opening [to] goes deeper: the path of the open folder still contains the folder that was shown, and
 * [to] is not above it. While the back gesture is dragged, the path still ends at [from], and [to] is its parent.
 */
internal fun opensDeeper(from: String?, to: String?, path: List<SectionItem>): Boolean {
    if (from == to) return false
    val fromIndex = path.indexOfFirst { it.id == from }
    if (fromIndex < 0) return false
    val toIndex = path.indexOfFirst { it.id == to }
    return toIndex < 0 || toIndex > fromIndex
}

/** The shared axis of Material: a deeper folder comes in from the end, and going back reverses that */
private fun folderTransition(from: String?, to: String?, path: List<SectionItem>): ContentTransform {
    val fade = tween<Float>(FOLDER_ANIMATION_MS)
    if (from == null || to == null) return fadeIn(fade) togetherWith fadeOut(fade)
    val offset = tween<IntOffset>(FOLDER_ANIMATION_MS)
    val direction = if (opensDeeper(from, to, path)) 1 else -1
    return (fadeIn(fade) + slideInHorizontally(offset) { direction * it / FOLDER_SLIDE_FRACTION }) togetherWith
        (fadeOut(fade) + slideOutHorizontally(offset) { -direction * it / FOLDER_SLIDE_FRACTION })
}

internal const val FOLDER_ANIMATION_MS = 250
private const val FOLDER_SLIDE_FRACTION = 10

/** the scroll state while the content of the folder is unknown, so the state of a folder keeps its position */
private const val NO_FOLDER = ""

@CombinedPreview
@Composable
private fun FileBrowserScreenContentPreview() {
    MkDocsEditorTheme {
        FileBrowserScreenContent(
            uiState = UiState(
                currentSectionPath = listOf(SectionItem("root", "/"), SectionItem("1", "folder")),
                folderContent = FolderContent(
                    sections = listOf(SectionData(1, "s", "Subsection", emptyList(), emptyList(), emptyList())),
                ),
            ),
            onUiEvent = {}
        )
    }
}