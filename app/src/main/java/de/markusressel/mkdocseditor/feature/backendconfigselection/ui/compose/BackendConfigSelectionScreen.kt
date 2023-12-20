package de.markusressel.mkdocseditor.feature.backendconfigselection.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.BackendSelectionViewModel
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.UiEvent
import de.markusressel.mkdocseditor.feature.backendconfigselection.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendConfigSelectionScreen(
//    onNavigationEvent: (NavigationEvent) -> Unit,
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.listItems.forEach { item ->
            BackendConfigListItem(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable {
                        onUiEvent(UiEvent.BackendConfigClicked(item))
                    }
            )
        }
    }
}

@Composable
internal fun BackendConfigListItem(
    item: BackendConfig,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = item.serverConfiguration.domain,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
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