package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.common.ui.compose.ErrorCard
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
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
                }

            },
            bottomBar = {
                var sectionPathVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    sectionPathVisible = true
                }
                AnimatedContent(
                    label = "sectionPath",
                    targetState = sectionPathVisible,
                    transitionSpec = {
                        slideInVertically().togetherWith(slideOutVertically())
                    },
                ) { visible ->
                    if (visible) {
                        SectionPath(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 4.dp),
                            path = uiState.currentSectionPath,
                            onSectionClicked = { section ->
                                onUiEvent(UiEvent.NavigateUpToSection(section))
                            }
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.dp)
                        )
                    }
                }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            FileBrowserLoadingIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                isLoading = uiState.isLoading
                            )

                            FileBrowserList(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(
                                        vertical = 16.dp,
                                        horizontal = 16.dp,
                                    ),
                                items = uiState.listItems,
                                onDocumentClicked = {
                                    onUiEvent(UiEvent.DocumentClicked(it))
                                },
                                onDocumentLongClicked = {
                                    onUiEvent(UiEvent.DocumentLongClicked(it))
                                },
                                onResourceClicked = {
                                    onUiEvent(UiEvent.ResourceClicked(it))
                                },
                                onResourceLongClicked = {
                                    onUiEvent(UiEvent.ResourceLongClicked(it))
                                },
                                onSectionClicked = {
                                    onUiEvent(UiEvent.SectionClicked(it))
                                },
                                onSectionLongClicked = {
                                    onUiEvent(UiEvent.SectionLongClicked(it))
                                },
                            )

                            Spacer(modifier = Modifier.height(128.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FileBrowserLoadingIndicator(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha: Float by animateFloatAsState(
        label = "progressAlphaAnimation",
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearOutSlowInEasing,
        ),
    )

    LinearProgressIndicator(
        modifier = Modifier
            .alpha(alpha)
            .then(modifier)
    )
}


@CombinedPreview
@Composable
private fun FileBrowserScreenContentPreview() {
    MkDocsEditorTheme {
        FileBrowserScreenContent(
            uiState = UiState(
                listItems = listOf(
                    SectionEntity(),
                    DocumentEntity().apply {

                    },
                    ResourceEntity(),
                )
            ),
            onUiEvent = {}
        )
    }
}