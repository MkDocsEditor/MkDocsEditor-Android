package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth.AuthConfigSection
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth.AuthConfigUiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun MkDocsWebSection(
    modifier: Modifier = Modifier,
    uiState: BackendConfigEditViewModel.UiState,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit,
    currentDomain: String,
    onDomainChanged: (String) -> Unit,
    currentPort: String,
    onPortChanged: (String) -> Unit,
    useSsl: Boolean,
    onUseSslCheckedChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.edit_mkdocsweb_config_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = stringResource(R.string.edit_mkdocsweb_config_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Card(modifier = modifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DomainInputField(
                    currentDomain = currentDomain,
                    onDomainChanged = onDomainChanged,
                )

                PortInputField(
                    currentPort = currentPort,
                    onPortChanged = onPortChanged,
                )

                SslCheckbox(
                    checked = useSsl,
                    onCheckedChanged = onUseSslCheckedChanged,
                )
            }
        }

        AuthConfigSection(
            modifier = Modifier
                .fillMaxWidth(),
            editMode = uiState.authConfigEditMode,
            authConfigs = uiState.authConfigs,
            authConfig = uiState.currentMkDocsWebAuthConfig,
            saveButtonEnabled = uiState.authConfigSaveButtonEnabled,
            currentAuthConfigUsername = uiState.currentAuthConfigUsername,
            currentAuthConfigPassword = uiState.currentAuthConfigPassword,
            onUiEvent = { event ->
                onUiEvent(
                    when (event) {
                        is AuthConfigUiEvent.SelectionChanged -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigSelectionChanged(
                                event.authConfig
                            )
                        }

                        is AuthConfigUiEvent.AddButtonClicked -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigAddButtonClicked
                        }

                        is AuthConfigUiEvent.AbortButtonClicked ->
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigAbortButtonClicked

                        is AuthConfigUiEvent.DeleteButtonClicked -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigDeleteButtonClicked(
                                event.authConfig
                            )
                        }

                        is AuthConfigUiEvent.PasswordInputChanged -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigPasswordInputChanged(
                                event.password
                            )
                        }

                        is AuthConfigUiEvent.UsernameInputChanged -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebAuthConfigUsernameInputChanged(
                                event.username
                            )
                        }

                        is AuthConfigUiEvent.SaveButtonClicked -> {
                            BackendConfigEditViewModel.UiEvent.MkDocsWebSaveButtonClicked
                        }
                    }
                )
            }
        )
    }
}


@CombinedPreview
@Composable
private fun MkDocsWebSectionPreview() {
    MkDocsEditorTheme {
        MkDocsWebSection(
            uiState = BackendConfigEditViewModel.UiState(),
            onUiEvent = {},
            currentDomain = "domain.com",
            onDomainChanged = {},
            currentPort = "443",
            onPortChanged = {},
            useSsl = true,
            onUseSslCheckedChanged = {},
        )
    }
}