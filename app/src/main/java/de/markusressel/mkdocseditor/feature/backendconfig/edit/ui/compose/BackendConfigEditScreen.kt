package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import cafe.adriel.voyager.navigator.LocalNavigator
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth.AuthConfigSection
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

data class BackendConfigEditScreen(
    val id: Long? = null
) : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current

        val viewModel: BackendConfigEditViewModel = hiltViewModel()
        LaunchedEffect(viewModel, id) {
            viewModel.initialize(id)
        }

        LaunchedEffect(viewModel.events) {
            viewModel.events.collect { event ->
                when (event) {
                    is BackendConfigEditViewModel.BackendEditEvent.CloseScreen -> {
                        navigator?.pop()
                    }

                    is BackendConfigEditViewModel.BackendEditEvent.Error -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }

                    is BackendConfigEditViewModel.BackendEditEvent.Info -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
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
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}

@Composable
private fun BackendConfigEditScreenContent(
    uiState: BackendConfigEditViewModel.UiState,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
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

            BackendConfigDescriptionEditField(
                modifier = Modifier
                    .fillMaxWidth(),
                description = uiState.description,
                onValueChanged = { text ->
                    onUiEvent(
                        BackendConfigEditViewModel.UiEvent.DescriptionChanged(text)
                    )
                }
            )

            ServerSection(
                modifier = Modifier.fillMaxWidth(),
                serverConfig = uiState.serverConfig,
                onUiEvent = onUiEvent
            )

            AuthConfigSection(
                modifier = Modifier
                    .fillMaxWidth(),
                editMode = uiState.authConfigEditMode,
                authConfigs = uiState.authConfigs,
                authConfig = uiState.authConfig,
                saveButtonEnabled = uiState.authConfigSaveButtonEnabled,
                currentAuthConfigUsername = uiState.currentAuthConfigUsername,
                currentAuthConfigPassword = uiState.currentAuthConfigPassword,
                onUiEvent = onUiEvent
            )

            SaveButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onUiEvent(BackendConfigEditViewModel.UiEvent.SaveClicked) }
            )
        }
    }
}

@Composable
internal fun BackendConfigDescriptionEditField(
    modifier: Modifier,
    description: String,
    onValueChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = description,
        modifier = modifier,
        onValueChange = { value ->
            onValueChanged(value)
        },
        label = { Text(stringResource(R.string.edit_backend_config_description_label)) }
    )
}

@Composable
private fun SaveButton(modifier: Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier,
        onClick = onClick
    ) {
        Text(stringResource(R.string.save))
    }
}

@Composable
internal fun BackendConfigNameEditField(
    modifier: Modifier,
    name: String,
    onValueChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
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
                    domain = "domain.com",
                    port = 443,
                    useSsl = true,
                ),
                authConfig = AuthConfig(
                    username = "user",
                    password = "password"
                )
            ),
            onUiEvent = {}
        )
    }
}