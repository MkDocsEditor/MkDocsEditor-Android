package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.SignInRequiredBanner
import de.markusressel.mkdocseditor.feature.common.ui.compose.ErrorCard
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun FileBrowserScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Box {
        AnimatedVisibility(
            modifier = Modifier
                .zIndex(100F)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            visible = uiState.error.isNullOrBlank().not(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ErrorCard(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .wrapContentSize(),
                text = uiState.error ?: "Error",
                onRetry = { onUiEvent(UiEvent.Refresh) },
                onDismiss = { onUiEvent(UiEvent.DismissError) }
            )
        }

        TabContentScaffold(
            topBar = {
                Column {
                    MkDocsEditorTopAppBar<TopAppBarAction.FileBrowser>(
                        canGoBack = false,
                        title = stringResource(id = R.string.screen_files_title),
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
                            onSignInClicked = { onUiEvent(UiEvent.SignInClicked) }
                        )
                    }
                }

            },
            bottomBar = {
                SectionPath(
                    modifier = Modifier.fillMaxWidth(),
                    path = uiState.currentSectionPath,
                    onSectionClicked = { section -> onUiEvent(UiEvent.PathItemClicked(section)) },
                )
            },
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
                    // each folder keeps its own position. It follows the content and not the path,
                    // because the content of the last folder stays until the next one has loaded
                    folderScrollStates.SaveableStateProvider(uiState.folderContent?.sectionId ?: NO_FOLDER) {
                        FileBrowserList(
                            modifier = Modifier.fillMaxSize(),
                            state = rememberLazyListState(),
                            content = uiState.folderContent,
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