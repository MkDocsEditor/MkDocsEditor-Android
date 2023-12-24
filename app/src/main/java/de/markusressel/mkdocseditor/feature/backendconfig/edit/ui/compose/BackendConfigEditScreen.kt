package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

data class BackendConfigEditScreen(
    val id: Long? = null
) : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current

        val viewModel: BackendConfigEditViewModel = hiltViewModel()
        LaunchedEffect(viewModel, id) {
            viewModel.initialize(id)
        }

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

        BackendConfigEditScreenContent(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}

@Composable
private fun BackendConfigEditScreenContent(
    uiState: BackendConfigEditViewModel.UiState,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenTitle(
            modifier = Modifier
                .fillMaxWidth(),
            title = "Backend Configuration"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BackendConfigNameEditField(
                modifier = Modifier
                    .fillMaxWidth(),
                name = uiState.name,
                onValueChanged = { text ->
                    onUiEvent(
                        BackendConfigEditViewModel.UiEvent.NameChanged(text)
                    )
                }
            )

            AuthConfigSection(
                modifier = Modifier
                    .fillMaxWidth(),
                authConfig = uiState.authConfig,
                onAuthConfigChanged = {
                    onUiEvent(
                        BackendConfigEditViewModel.UiEvent.AuthConfigChanged(
                            it
                        )
                    )
                }
            )
        }
    }
}

@Composable
internal fun BackendConfigNameEditField(
    modifier: Modifier,
    name: String,
    onValueChanged: (String) -> Unit
) {
    OutlinedTextField(value = name,
        modifier = modifier,
        onValueChange = { value ->
            onValueChanged(value)
        },
        label = { Text(stringResource(R.string.edit_backend_config_name_label)) }
    )
}

@CombinedPreview
@Composable
private fun BackendConfigEditScreenContentPreview() {
    MkDocsEditorTheme {
        BackendConfigEditScreenContent(
            uiState = BackendConfigEditViewModel.UiState(
                isLoading = false,
                error = null,
                name = "Example",
                description = "The wiki",
                serverConfig = BackendServerConfig(
                    domain = "https://example.com",
                    port = 443,
                    useSsl = true,
                ),
                authConfig = BackendAuthConfig(
                    username = "user",
                    password = "password"
                )
            ),
            onUiEvent = {}
        )
    }
}