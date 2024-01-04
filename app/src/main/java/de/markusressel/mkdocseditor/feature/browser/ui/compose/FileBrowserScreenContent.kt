package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.fengdai.compose.pulltorefresh.PullToRefresh
import com.github.fengdai.compose.pulltorefresh.rememberPullToRefreshState
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ErrorCard
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun FileBrowserScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
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

                PullToRefresh(
                    //modifier = modifier,
                    state = rememberPullToRefreshState(
                        isRefreshing = uiState.isLoading
                    ),
                    onRefresh = { onUiEvent(UiEvent.Refresh) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize()
                    ) {
                        Column {
                            FileBrowserList(
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
                        }
                    }
                }
            }
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