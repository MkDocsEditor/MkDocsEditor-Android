package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.BackendSelectionViewModel
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendSelectionScreenContent(
    uiState: BackendSelectionViewModel.UiState,
    onUiEvent: (BackendSelectionViewModel.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MkDocsEditorTopAppBar(
                canGoBack = false,
                title = "Backend selection",
            )
        },
        floatingActionButton = {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(BackendSelectionViewModel.UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            BackendConfigList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                uiState = uiState,
                onUiEvent = onUiEvent
            )
        }
    }
}


@CombinedPreview
@Composable
private fun BackendSelectionScreenContentPreview() {
    MkDocsEditorTheme {
        BackendSelectionScreenContent(
            uiState = BackendSelectionViewModel.UiState(
                listItems = listOf(
                    BackendConfig(
                        name = "Wiki",
                        description = "The wiki",
                        isSelected = true,
                        serverConfig = BackendServerConfig(
                            domain = "mkdocksrest.backend.com",
                            port = 443,
                            useSsl = true,
                            webBaseUri = "https://mkdocksrest.backend.com",
                        ),
                        authConfig = AuthConfig(
                            username = "test",
                            password = "test",
                        )
                    )
                )
            ),
            onUiEvent = {}
        )
    }
}