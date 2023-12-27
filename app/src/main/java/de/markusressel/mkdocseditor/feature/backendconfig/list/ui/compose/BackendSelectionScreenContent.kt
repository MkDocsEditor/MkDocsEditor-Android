package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.BackendSelectionViewModel
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendSelectionScreenContent(
    uiState: BackendSelectionViewModel.UiState,
    onUiEvent: (BackendSelectionViewModel.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column {
            ScreenTitle(
                modifier = Modifier
                    .fillMaxWidth(),
                title = "Backend Selection"
            )

            BackendConfigList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                uiState = uiState,
                onUiEvent = onUiEvent
            )
        }

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(BackendSelectionViewModel.UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
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