package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.fengdai.compose.pulltorefresh.PullToRefresh
import com.github.fengdai.compose.pulltorefresh.rememberPullToRefreshState
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ErrorCard
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchScreenContent
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
                onRetry = {
                    onUiEvent(UiEvent.Refresh)
                }
            )
        }

        Scaffold(
            topBar = {
                Column {
                    MkDocsEditorTopAppBar<TopAppBarAction.FileBrowser>(
                        canGoBack = false,
                        title = stringResource(id = R.string.screen_files_title),
                        actions = listOf(TopAppBarAction.FileBrowser.Search),
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
                    modifier = Modifier.fillMaxSize(),
                    items = uiState.fabConfig.right,
                    onItemClicked = {
                        onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues)
            ) {
                PullToRefresh(
                    //modifier = modifier,
                    state = rememberPullToRefreshState(
                        isRefreshing = uiState.isLoading
                    ),
                    onRefresh = { onUiEvent(UiEvent.Refresh) },
                ) {
                    FileBrowserList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .verticalScroll(rememberScrollState())
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

        if (uiState.isSearchExpanded) {
            SearchScreenContent(
                uiState = SearchViewModel.UiState(
                    currentSearchFilter = uiState.currentSearchFilter,
                    isSearchExpanded = uiState.isSearchExpanded,
                    currentSearchResults = uiState.currentSearchResults,
                ),
                onUiEvent = onUiEvent,
            )
        }
    }
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