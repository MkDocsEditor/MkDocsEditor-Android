package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server.MkDocsBackendSection
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server.MkDocsWebSection
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendConfigEditScreenContent(
    uiState: BackendConfigEditViewModel.UiState,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = "Backend Configuration",
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
        ) {
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

                MkDocsBackendSection(
                    modifier = Modifier.fillMaxWidth(),
                    uiState = uiState,
                    onUiEvent = onUiEvent,
                    currentDomain = uiState.currentDomain,
                    onDomainChanged = { text ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.DomainChanged(text)
                        )
                    },
                    currentPort = uiState.currentPort,
                    onPortChanged = { text ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.PortChanged(text)
                        )
                    },
                    useSsl = uiState.currentUseSsl,
                    onUseSslCheckedChanged = { checked ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.UseSslChanged(checked)
                        )
                    },
                )

                MkDocsWebSection(
                    modifier = Modifier.fillMaxWidth(),
                    uiState = uiState,
                    onUiEvent = onUiEvent,
                    currentDomain = uiState.currentMkDocsWebDomain,
                    onDomainChanged = { text ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.MkDocsWebDomainChanged(text)
                        )
                    },
                    currentPort = uiState.currentMkDocsWebPort,
                    onPortChanged = { text ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.MkDocsWebPortChanged(text)
                        )
                    },
                    useSsl = uiState.currentMkDocsWebUseSsl,
                    onUseSslCheckedChanged = { checked ->
                        onUiEvent(
                            BackendConfigEditViewModel.UiEvent.MkDocsWebUseSslChanged(checked)
                        )
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                SaveButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.saveButtonEnabled,
                    onClick = { onUiEvent(BackendConfigEditViewModel.UiEvent.SaveClicked) }
                )

                if (uiState.isDeleteButtonEnabled) {
                    DeleteButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onUiEvent(BackendConfigEditViewModel.UiEvent.DeleteClicked) }
                    )
                }

            }
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
                currentDomain = "domain.com",
                currentPort = "443",
                currentUseSsl = true,
                currentAuthConfig = AuthConfig(
                    username = "user",
                    password = "password"
                )
            ),
            onUiEvent = {}
        )
    }
}