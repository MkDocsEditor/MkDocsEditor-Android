package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
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
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(BackendSelectionViewModel.UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            BackendConfigList(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                uiState = uiState,
                onUiEvent = onUiEvent
            )

            Spacer(modifier = Modifier.height(128.dp))
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
                        ),
                        backendAuthConfig = AuthConfig(
                            username = "test",
                            password = "test",
                        ),
                        mkDocsWebConfig = MkDocsWebConfig(
                            domain = "mkdocksweb.backend.com",
                            port = 443,
                            useSsl = true,
                        ),
                        mkDocsWebAuthConfig = AuthConfig(
                            username = "test",
                            password = "test",
                        ),
                    )
                )
            ),
            onUiEvent = {}
        )
    }
}