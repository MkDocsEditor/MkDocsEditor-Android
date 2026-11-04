package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.PasswordField
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiState
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server.BackendAccountCard
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun BackendConfigEditScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = uiState.message?.let { stringResource(it) }
    LaunchedEffect(uiState.message) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        onUiEvent(UiEvent.MessageShown)
    }

    if (uiState.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onUiEvent(UiEvent.DeleteDismissed) },
            title = { Text(stringResource(R.string.server_delete_title, uiState.config?.name.orEmpty())) },
            text = { Text(stringResource(R.string.server_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onUiEvent(UiEvent.DeleteConfirmed) }) {
                    Text(stringResource(R.string.server_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onUiEvent(UiEvent.DeleteDismissed) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    TabContentScaffold(
        topBar = { MkDocsEditorTopAppBar(title = stringResource(R.string.server_edit_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ServerFields(uiState, onUiEvent)

            if (uiState.config?.serverConfig != null) {
                BackendAccountCard(
                    modifier = Modifier.fillMaxWidth(),
                    signedInUsername = uiState.signedInUsername,
                    onSignInClicked = { onUiEvent(UiEvent.SignInClicked) },
                    onSignOutClicked = { onUiEvent(UiEvent.SignOutClicked) },
                )
            }

            TextButton(onClick = { onUiEvent(UiEvent.AdvancedToggled) }) {
                Icon(
                    imageVector = if (uiState.advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(R.string.onboarding_advanced),
                )
            }
            if (uiState.advancedExpanded) {
                AdvancedFields(uiState, onUiEvent)
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSave,
                onClick = { onUiEvent(UiEvent.SaveClicked) },
            ) {
                Text(stringResource(R.string.save))
            }

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = { onUiEvent(UiEvent.DeleteClicked) },
            ) {
                Text(stringResource(R.string.server_delete))
            }
        }
    }
}

@Composable
private fun ServerFields(uiState: UiState, onUiEvent: (UiEvent) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.name,
        onValueChange = { onUiEvent(UiEvent.NameChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.onboarding_name)) },
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.description,
        onValueChange = { onUiEvent(UiEvent.DescriptionChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_description)) },
    )
    AddressField(
        value = uiState.address,
        onValueChange = { onUiEvent(UiEvent.AddressChanged(it)) },
        label = stringResource(R.string.server_address),
        isError = !uiState.addressIsValid,
    )
}

@Composable
private fun AdvancedFields(uiState: UiState, onUiEvent: (UiEvent) -> Unit) {
    AddressField(
        value = uiState.webAddress,
        onValueChange = { onUiEvent(UiEvent.WebAddressChanged(it)) },
        label = stringResource(R.string.server_web_address),
        isError = !uiState.webAddressIsValid,
    )
    Text(
        text = stringResource(R.string.onboarding_wiki_auth_hint),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.wikiUsername,
        onValueChange = { onUiEvent(UiEvent.WikiUsernameChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.onboarding_wiki_username)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
    PasswordField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.wikiPassword,
        onValueChange = { onUiEvent(UiEvent.WikiPasswordChanged(it)) },
        label = stringResource(R.string.onboarding_wiki_password),
    )
}

@Composable
private fun AddressField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        isError = isError,
        label = { Text(label) },
        supportingText = if (isError) {
            { Text(stringResource(R.string.server_address_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
        ),
    )
}

@CombinedPreview
@Composable
private fun BackendConfigEditScreenContentPreview() {
    MkDocsEditorTheme {
        BackendConfigEditScreenContent(
            uiState = UiState(
                name = "Example",
                description = "The wiki",
                address = "https://domain.com",
                signedInUsername = "markus",
                advancedExpanded = true,
            ),
            onUiEvent = {},
        )
    }
}
