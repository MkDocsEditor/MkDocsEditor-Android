package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun AuthConfigEditLayout(
    showPassword: Boolean = false,
    currentUsername: String,
    currentPassword: String,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = currentUsername,
        onValueChange = onUsernameChanged,
        label = { Text(text = stringResource(R.string.edit_auth_config_username)) }
    )

    OutlinedTextField(
        value = currentPassword,
        onValueChange = onPasswordChanged,
        label = { Text(text = stringResource(R.string.edit_auth_config_password)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

@Preview
@Composable
private fun AuthEditLayoutPreview() {
    MkDocsEditorTheme {
        AuthConfigEditLayout(
            currentUsername = "username",
            currentPassword = "password",
            onUsernameChanged = {},
            onPasswordChanged = {}
        )
    }
}