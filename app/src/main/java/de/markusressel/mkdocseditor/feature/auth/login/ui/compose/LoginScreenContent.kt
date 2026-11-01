package de.markusressel.mkdocseditor.feature.auth.login.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.domain.SecondFactorMethod
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.OneTimeCodeField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.PasswordField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.RecoveryCodeField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.UsernameField
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel.Companion.isPasskeyOnly
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun LoginScreenContent(
    uiState: LoginViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.auth_sign_in_to, uiState.backendName),
                canGoBack = true,
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

            uiState.error?.let {
                Text(
                    text = it.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when {
                uiState.authDisabled -> AuthDisabledHint(onUiEvent)
                uiState.step == LoginViewModel.Step.SecondFactor -> SecondFactorStep(uiState, onUiEvent)
                else -> CredentialsStep(uiState, onUiEvent)
            }

            HorizontalDivider()

            LinkSection(uiState, onUiEvent)
        }
    }
}

@Composable
private fun AuthDisabledHint(onUiEvent: (UiEvent) -> Unit) {
    Text(stringResource(R.string.auth_disabled_hint))
    Button(onClick = { onUiEvent(UiEvent.ContinueWithoutLoginClicked) }) {
        Text(stringResource(R.string.auth_continue))
    }
}

@Composable
private fun CredentialsStep(
    uiState: LoginViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val enabled = !uiState.loading

    if (uiState.setupRequired) {
        Card {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.auth_setup_required_hint),
            )
        }
    }

    if (uiState.passkeysEnabled) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onClick = { onUiEvent(UiEvent.PasskeyClicked) },
        ) {
            Icon(Icons.Filled.Key, contentDescription = null)
            Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.auth_sign_in_with_passkey))
        }
    } else {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onClick = { onUiEvent(UiEvent.SavedPasswordClicked) },
        ) {
            Icon(Icons.Filled.Password, contentDescription = null)
            Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.auth_use_saved_password))
        }
    }

    Text(
        text = stringResource(R.string.auth_or_with_password),
        style = MaterialTheme.typography.labelLarge,
    )

    UsernameField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.username,
        onValueChange = { onUiEvent(UiEvent.UsernameChanged(it)) },
        enabled = enabled,
    )

    PasswordField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.password,
        onValueChange = { onUiEvent(UiEvent.PasswordChanged(it)) },
        enabled = enabled,
        onImeAction = { onUiEvent(UiEvent.LoginClicked) },
    )

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && uiState.username.isNotBlank() && uiState.password.isNotEmpty(),
        onClick = { onUiEvent(UiEvent.LoginClicked) },
    ) {
        Text(stringResource(R.string.auth_sign_in))
    }
}

@Composable
private fun SecondFactorStep(
    uiState: LoginViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val enabled = !uiState.loading
    val focusRequester = remember { FocusRequester() }

    Text(
        text = stringResource(R.string.auth_second_factor_title),
        style = MaterialTheme.typography.titleMedium,
    )

    if (uiState.methods.isPasskeyOnly()) {
        Text(stringResource(R.string.auth_passkey_second_factor_hint))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onClick = { onUiEvent(UiEvent.PasskeyClicked) },
        ) {
            Text(stringResource(R.string.auth_use_passkey))
        }
        TextButton(onClick = { onUiEvent(UiEvent.BackToPasswordClicked) }) {
            Text(stringResource(R.string.auth_start_over))
        }
        return
    }

    if (uiState.useRecoveryCode) {
        Text(stringResource(R.string.auth_recovery_code_hint))
        RecoveryCodeField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = uiState.code,
            onValueChange = { onUiEvent(UiEvent.CodeChanged(it)) },
            enabled = enabled,
            onImeAction = { onUiEvent(UiEvent.SubmitCodeClicked) },
        )
    } else {
        Text(stringResource(R.string.auth_totp_hint))
        OneTimeCodeField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = uiState.code,
            onValueChange = { onUiEvent(UiEvent.CodeChanged(it)) },
            enabled = enabled,
            onImeAction = { onUiEvent(UiEvent.SubmitCodeClicked) },
        )
    }

    LaunchedEffect(uiState.useRecoveryCode) {
        focusRequester.requestFocus()
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && uiState.code.isNotBlank(),
        onClick = { onUiEvent(UiEvent.SubmitCodeClicked) },
    ) {
        Text(stringResource(R.string.auth_verify))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (SecondFactorMethod.Totp in uiState.methods && SecondFactorMethod.RecoveryCode in uiState.methods) {
            TextButton(onClick = { onUiEvent(UiEvent.UseRecoveryCodeChanged(!uiState.useRecoveryCode)) }) {
                Text(
                    stringResource(
                        if (uiState.useRecoveryCode) R.string.auth_use_authenticator_code
                        else R.string.auth_use_recovery_code
                    )
                )
            }
        }
        if (SecondFactorMethod.Passkey in uiState.methods) {
            TextButton(enabled = enabled, onClick = { onUiEvent(UiEvent.PasskeyClicked) }) {
                Text(stringResource(R.string.auth_use_passkey))
            }
        }
    }

    TextButton(onClick = { onUiEvent(UiEvent.BackToPasswordClicked) }) {
        Text(stringResource(R.string.auth_start_over))
    }
}

@Composable
private fun LinkSection(
    uiState: LoginViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_have_a_link),
        style = MaterialTheme.typography.titleSmall,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.linkInput,
        onValueChange = { onUiEvent(UiEvent.LinkInputChanged(it)) },
        singleLine = true,
        label = { Text(stringResource(R.string.auth_link_input_label)) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
    )
    OutlinedButton(
        enabled = uiState.linkInput.isNotBlank(),
        onClick = { onUiEvent(UiEvent.OpenLinkClicked) },
    ) {
        Text(stringResource(R.string.auth_open_link))
    }
}

@CombinedPreview
@Composable
private fun LoginScreenContentPreview() {
    MkDocsEditorTheme {
        LoginScreenContent(
            uiState = LoginViewModel.UiState(
                backendName = "Wiki",
                username = "markus",
                passkeysEnabled = true,
            ),
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}

@CombinedPreview
@Composable
private fun LoginScreenSecondFactorPreview() {
    MkDocsEditorTheme {
        LoginScreenContent(
            uiState = LoginViewModel.UiState(
                backendName = "Wiki",
                step = LoginViewModel.Step.SecondFactor,
                methods = setOf(SecondFactorMethod.Totp, SecondFactorMethod.RecoveryCode, SecondFactorMethod.Passkey),
            ),
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}
