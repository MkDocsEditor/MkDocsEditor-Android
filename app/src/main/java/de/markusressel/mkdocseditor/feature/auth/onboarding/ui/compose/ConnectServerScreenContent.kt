package de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.PasswordField
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ConnectServerScreenContent(
    uiState: ConnectServerViewModel.UiState,
    canGoBack: Boolean,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.onboarding_title),
                canGoBack = canGoBack,
                onBackClicked = onBackClicked,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.setupBackendId != null) {
                SetupRequired(uiState, onUiEvent)
            } else {
                AddressForm(uiState, onUiEvent)
            }

            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AddressForm(uiState: ConnectServerViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    if (BuildConfig.DEBUG) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.loading,
            onClick = { onUiEvent(UiEvent.DevBackendClicked) },
        ) {
            Text("Dev backend (${ConnectServerViewModel.DEV_BACKEND_URL})")
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.onboarding_qr_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.onboarding_qr_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Text(text = stringResource(R.string.onboarding_manual_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.address,
        onValueChange = { onUiEvent(UiEvent.AddressChanged(it)) },
        enabled = !uiState.loading,
        singleLine = true,
        label = { Text(stringResource(R.string.onboarding_address)) },
        placeholder = { Text("edit.wiki.example.com") },
        supportingText = { Text(stringResource(R.string.onboarding_address_hint)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onAny = { onUiEvent(UiEvent.ContinueClicked) }),
    )

    TextButton(onClick = { onUiEvent(UiEvent.AdvancedToggled) }) {
        Icon(if (uiState.advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.onboarding_advanced))
    }
    if (uiState.advancedExpanded) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.name,
            onValueChange = { onUiEvent(UiEvent.NameChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.onboarding_name)) },
            supportingText = { Text(stringResource(R.string.onboarding_name_hint)) },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.description,
            onValueChange = { onUiEvent(UiEvent.DescriptionChanged(it)) },
            label = { Text(stringResource(R.string.onboarding_description)) },
        )
        Text(text = stringResource(R.string.onboarding_wiki_auth_hint), style = MaterialTheme.typography.bodySmall)
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

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.loading && uiState.address.isNotBlank(),
        onClick = { onUiEvent(UiEvent.ContinueClicked) },
    ) {
        Text(stringResource(R.string.auth_continue))
    }
}

@Composable
private fun SetupRequired(uiState: ConnectServerViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    Text(text = stringResource(R.string.onboarding_setup_title, uiState.serverName), style = MaterialTheme.typography.titleMedium)
    Text(text = stringResource(R.string.onboarding_setup_hint), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.setupLink,
        onValueChange = { onUiEvent(UiEvent.SetupLinkChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.auth_link_input_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onAny = { onUiEvent(UiEvent.SetupLinkSubmitted) }),
    )
    Button(enabled = uiState.setupLink.isNotBlank(), onClick = { onUiEvent(UiEvent.SetupLinkSubmitted) }) {
        Text(stringResource(R.string.auth_open_link))
    }
    TextButton(onClick = { onUiEvent(UiEvent.BackToAddressClicked) }) {
        Text(stringResource(R.string.onboarding_other_server))
    }
}

@CombinedPreview
@Composable
private fun ConnectServerScreenContentPreview() {
    MkDocsEditorTheme {
        ConnectServerScreenContent(
            uiState = ConnectServerViewModel.UiState(address = "edit.wiki.example.com", advancedExpanded = true),
            canGoBack = false,
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}
