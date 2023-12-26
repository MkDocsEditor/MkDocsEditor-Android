package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent.DomainChanged
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth.AuthConfigSection
import de.markusressel.mkdocseditor.feature.common.ui.compose.ScreenTitle
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendConfigEditScreenContent(
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
                currentDomain = uiState.currentDomain,
                onDomainChanged = { text ->
                    onUiEvent(DomainChanged(text))
                },
                currentPort = uiState.currentPort,
                onPortChanged = { text ->
                    onUiEvent(BackendConfigEditViewModel.UiEvent.PortChanged(text))
                },
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
                enabled = uiState.saveButtonEnabled,
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