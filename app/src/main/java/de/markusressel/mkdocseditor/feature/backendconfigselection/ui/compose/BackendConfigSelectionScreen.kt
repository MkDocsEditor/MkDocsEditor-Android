package de.markusressel.mkdocseditor.feature.backendconfigselection.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.BackendSelectionViewModel
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.UiEvent
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.main.ui.NavigationEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendConfigSelectionScreen(
    onNavigationEvent: (NavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackendSelectionViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

//    BackHandler(
//        enabled = uiState.canGoUp,
//        onBack = {
//            val consumed = viewModel.navigateUp()
//            if (consumed.not()) {
//                onBack()
//            }
//        },
//    )

    BackendSelectionScreenContent(
        modifier = modifier,
        uiState = uiState,
        onUiEvent = viewModel::onUiEvent
    )
}


@Composable
private fun BackendSelectionScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        BackendConfigList(
            uiState = uiState,
            onUiEvent = onUiEvent,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0F)
        )

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}

@Composable
internal fun BackendConfigList(
    modifier: Modifier = Modifier,
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,

    ) {
    TODO("Not yet implemented")
}


@CombinedPreview
@Composable
private fun BackendSelectionScreenContentPreview() {
    MkDocsEditorTheme {
        BackendSelectionScreenContent(
            uiState = UiState(
                listItems = listOf(
                    BackendConfig(
                        id = "wiki",
                        name = "Wiki",
                        description = "The wiki",
                        serverConfiguration = BackendServerConfig(
                            domain = "mkdocksrest.backend.com",
                            port = 443,
                            useSsl = true,
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