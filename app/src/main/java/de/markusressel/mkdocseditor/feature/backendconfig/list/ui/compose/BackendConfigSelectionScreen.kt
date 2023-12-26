package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.BackendConfigEditScreen
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.BackendSelectionViewModel
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

object BackendConfigSelectionScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel: BackendSelectionViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is BackendSelectionViewModel.BackendSelectionEvent.CreateBackend -> {
                        navigator.push(BackendConfigEditScreen())
                    }

                    is BackendSelectionViewModel.BackendSelectionEvent.EditBackend -> {
                        navigator.push(BackendConfigEditScreen(event.id))
                    }

                    is BackendSelectionViewModel.BackendSelectionEvent.Error -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        BackendSelectionScreenContent(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}

@Composable
private fun BackendSelectionScreenContent(
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

@Composable
internal fun BackendConfigList(
    modifier: Modifier = Modifier,
    uiState: BackendSelectionViewModel.UiState,
    onUiEvent: (BackendSelectionViewModel.UiEvent) -> Unit,
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
                        onUiEvent(BackendSelectionViewModel.UiEvent.BackendConfigClicked(item))
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
                text = item.serverConfig.domain,
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
            uiState = BackendSelectionViewModel.UiState(
                listItems = listOf(
                    BackendConfig(
                        name = "Wiki",
                        description = "The wiki",
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