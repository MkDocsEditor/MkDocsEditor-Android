package de.markusressel.mkdocseditor.feature.auth.link.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.link.LinkKind
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.PasswordField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.UsernameField
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun AuthLinkScreenContent(
    uiState: AuthLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    val title = when (uiState.kind ?: uiState.kindHint) {
        LinkKind.Setup -> stringResource(R.string.auth_link_title_setup)
        LinkKind.Invite -> stringResource(R.string.auth_link_title_invite)
        LinkKind.Reset -> stringResource(R.string.auth_link_title_reset)
        null -> stringResource(R.string.auth_link_title)
    }
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(title = title, canGoBack = true, onBackClicked = onBackClicked)
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
            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error)
            }

            when (uiState.step) {
                AuthLinkViewModel.Step.Loading, AuthLinkViewModel.Step.Invalid -> {}
                AuthLinkViewModel.Step.ChooseBackend -> ChooseBackendStep(uiState, onUiEvent)
                AuthLinkViewModel.Step.Form -> FormStep(uiState, onUiEvent)
                AuthLinkViewModel.Step.Done -> DoneStep(uiState, onUiEvent)
            }
        }
    }
}

@Composable
private fun ChooseBackendStep(
    uiState: AuthLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Text(stringResource(R.string.auth_link_choose_backend))
    uiState.backends.forEach { backend ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = uiState.selectedBackendId == backend.id,
                    onClick = { onUiEvent(UiEvent.BackendSelected(backend.id)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = uiState.selectedBackendId == backend.id,
                onClick = { onUiEvent(UiEvent.BackendSelected(backend.id)) },
            )
            Text(backend.name)
        }
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.backendUrl,
        onValueChange = { onUiEvent(UiEvent.BackendUrlChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.auth_link_backend_url)) },
        placeholder = { Text("https://backend.wiki.example.com") },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
    )
    Button(
        enabled = !uiState.loading && (uiState.selectedBackendId != null || uiState.backendUrl.isNotBlank()),
        onClick = { onUiEvent(UiEvent.ContinueClicked) },
    ) {
        Text(stringResource(R.string.auth_continue))
    }
}

@Composable
private fun FormStep(
    uiState: AuthLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val enabled = !uiState.loading
    val isReset = uiState.kind == LinkKind.Reset

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(uiState.backendName, style = MaterialTheme.typography.titleMedium)
            when {
                isReset -> {
                    Text(stringResource(R.string.auth_link_reset_for, uiState.resetUsername.orEmpty()))
                    if (uiState.resetSecondFactors) {
                        Text(stringResource(R.string.auth_link_reset_second_factors))
                    }
                }

                else -> uiState.role?.let { Text(stringResource(R.string.auth_link_role, it)) }
            }
            uiState.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (!isReset) {
        UsernameField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.username,
            onValueChange = { onUiEvent(UiEvent.UsernameChanged(it)) },
            newUsername = true,
            enabled = enabled,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.PersonFullName },
            value = uiState.displayName,
            onValueChange = { onUiEvent(UiEvent.DisplayNameChanged(it)) },
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.auth_display_name)) },
            supportingText = { Text(stringResource(R.string.auth_display_name_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
    }

    if (uiState.passkeysEnabled) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onClick = { onUiEvent(UiEvent.CreatePasskeyClicked) },
        ) {
            Icon(Icons.Filled.Key, contentDescription = null)
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(
                    if (isReset) R.string.auth_link_use_passkey_reset else R.string.auth_link_create_with_passkey
                ),
            )
        }
        Text(stringResource(R.string.auth_or_with_password), style = MaterialTheme.typography.labelLarge)
    }

    PasswordField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.password,
        onValueChange = { onUiEvent(UiEvent.PasswordChanged(it)) },
        label = stringResource(R.string.auth_new_password),
        newPassword = true,
        enabled = enabled,
        supportingText = stringResource(R.string.auth_password_rules, uiState.minPasswordLength),
        imeAction = ImeAction.Next,
    )
    PasswordField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.passwordConfirmation,
        onValueChange = { onUiEvent(UiEvent.PasswordConfirmationChanged(it)) },
        label = stringResource(R.string.auth_repeat_password),
        newPassword = true,
        enabled = enabled,
        onImeAction = { onUiEvent(UiEvent.SubmitPasswordClicked) },
    )
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && uiState.password.isNotEmpty(),
        onClick = { onUiEvent(UiEvent.SubmitPasswordClicked) },
    ) {
        Text(
            stringResource(
                if (isReset) R.string.auth_link_set_password else R.string.auth_link_create_with_password
            )
        )
    }
}

@Composable
private fun DoneStep(
    uiState: AuthLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_signed_in_as, uiState.signedInUsername ?: "?"),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(
            if (uiState.secondFactorSetupRequired) R.string.auth_second_factor_setup_hint
            else R.string.auth_link_done_hint
        )
    )
    HorizontalDivider()
    Button(onClick = { onUiEvent(UiEvent.DoneClicked) }) {
        Text(stringResource(R.string.auth_done))
    }
}

@CombinedPreview
@Composable
private fun AuthLinkScreenInvitePreview() {
    MkDocsEditorTheme {
        AuthLinkScreenContent(
            uiState = AuthLinkViewModel.UiState(
                step = AuthLinkViewModel.Step.Form,
                kind = LinkKind.Invite,
                backendName = "Wiki",
                role = "editor",
                note = "for Anna",
                passkeysEnabled = true,
            ),
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}
