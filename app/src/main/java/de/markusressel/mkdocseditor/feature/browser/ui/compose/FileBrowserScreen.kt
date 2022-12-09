package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.fengdai.compose.pulltorefresh.PullToRefresh
import com.github.fengdai.compose.pulltorefresh.rememberPullToRefreshState
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab

@Composable
internal fun FileBrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.events.observeForever { event ->
            when (event) {
                is FileBrowserEvent.ErrorEvent -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // TODO: handle other events
                }
            }
        }
    }

    FileBrowserScreenContent(
        modifier = modifier,
        uiState = uiState,
        onUiEvent = viewModel::onUiEvent
    )
}


@Preview
@Composable
private fun FileBrowserScreenContentPreview() {
    FileBrowserScreenContent(
        uiState = UiState(
            listItems = listOf(

            )
        ),
        onUiEvent = {}
    )
}

@Composable
private fun FileBrowserScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box {
        PullToRefresh(
            modifier = modifier,
            state = rememberPullToRefreshState(
                isRefreshing = uiState.isLoading
            ),
            onRefresh = { onUiEvent(UiEvent.Refresh) },
        ) {
            FileBrowserList(
                items = uiState.listItems,
                onDocumentClicked = {
                    onUiEvent(UiEvent.DocumentClicked(it))
                },
                onResourceClicked = {
                    onUiEvent(UiEvent.ResourceClicked(it))
                },
                onSectionClicked = {
                    onUiEvent(UiEvent.SectionClicked(it))
                },
            )
        }

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}