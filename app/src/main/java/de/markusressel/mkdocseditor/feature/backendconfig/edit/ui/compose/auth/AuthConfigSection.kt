package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

sealed class AuthConfigUiEvent {
    data object AbortButtonClicked : AuthConfigUiEvent()
    data object AddButtonClicked : AuthConfigUiEvent()
    data class DeleteButtonClicked(val authConfig: AuthConfig) : AuthConfigUiEvent()
    data class SelectionChanged(val authConfig: AuthConfig) : AuthConfigUiEvent()
    data class UsernameInputChanged(val username: String) : AuthConfigUiEvent()
    data class PasswordInputChanged(val password: String) : AuthConfigUiEvent()

    data object SaveButtonClicked : AuthConfigUiEvent()
}

@Composable
internal fun AuthConfigSection(
    modifier: Modifier = Modifier,
    editMode: Boolean,
    authConfigs: List<AuthConfig>,
    authConfig: AuthConfig?,
    saveButtonEnabled: Boolean,
    currentAuthConfigUsername: String,
    currentAuthConfigPassword: String,
    onUiEvent: (AuthConfigUiEvent) -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.edit_auth_config_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(targetState = editMode, label = "editMode") { editMode ->
                    Row {
                        if (editMode) {
                            AuthAbortButton(
                                modifier = Modifier,
                                onClick = { onUiEvent(AuthConfigUiEvent.AbortButtonClicked) }
                            )
                            AuthSaveButton(
                                modifier = Modifier,
                                enabled = saveButtonEnabled,
                                onUiEvent = onUiEvent,
                            )
                        } else {
                            AuthAddButton(
                                modifier = Modifier,
                                onClick = { onUiEvent(AuthConfigUiEvent.AddButtonClicked) }
                            )
                            if (authConfig != null) {
                                AuthDeleteButton(
                                    modifier = Modifier,
                                    onClick = {
                                        onUiEvent(
                                            AuthConfigUiEvent.DeleteButtonClicked(
                                                authConfig
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedContent(targetState = editMode, label = "editMode") { editMode ->
                Column {
                    if (editMode) {
                        AuthConfigEditLayout(
                            currentUsername = currentAuthConfigUsername,
                            currentPassword = currentAuthConfigPassword,
                            onUsernameChanged = {
                                onUiEvent(AuthConfigUiEvent.UsernameInputChanged(it))
                            },
                            onPasswordChanged = {
                                onUiEvent(AuthConfigUiEvent.PasswordInputChanged(it))
                            }
                        )
                    } else {
                        if (authConfigs.isEmpty()) {
                            AuthConfigEmptyLayout()
                        } else {
                            AuthConfigSelectionLayout(
                                authConfigs = authConfigs,
                                authConfig = authConfig,
                                onUiEvent = onUiEvent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuthConfigSelectionLayout(
    authConfigs: List<AuthConfig>,
    authConfig: AuthConfig?,
    onUiEvent: (AuthConfigUiEvent) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.padding(end = 16.dp),
            text = stringResource(R.string.edit_auth_config_username),
            style = MaterialTheme.typography.titleMedium
        )

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Row(
                modifier = Modifier.menuAnchor(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    text = authConfig?.username ?: "",
                )

                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                authConfigs.sortedBy { it.username.lowercase() }.forEach { item ->
                    DropdownMenuItem(text = {
                        Text(item.username)
                    }, onClick = {
                        onUiEvent(AuthConfigUiEvent.SelectionChanged(item))
                        expanded = false
                    })
                }
            }
        }
    }
}


@CombinedPreview
@Composable
private fun AuthConfigSectionPreviewNoData() {
    MkDocsEditorTheme {
        AuthConfigSection(
            modifier = Modifier.fillMaxWidth(),
            editMode = false,
            authConfigs = emptyList(),
            authConfig = AuthConfig(
                username = "username",
                password = "password"
            ),
            saveButtonEnabled = false,
            onUiEvent = {},
            currentAuthConfigUsername = "",
            currentAuthConfigPassword = "",
        )
    }

}

@CombinedPreview
@Composable
private fun AuthConfigSectionPreviewEditing() {
    MkDocsEditorTheme {
        AuthConfigSection(
            modifier = Modifier.fillMaxWidth(),
            editMode = true,
            authConfigs = emptyList(),
            authConfig = null,
            saveButtonEnabled = false,
            onUiEvent = {},

            currentAuthConfigUsername = "Test",
            currentAuthConfigPassword = "abc",
        )
    }
}

@CombinedPreview
@Composable
private fun AuthConfigSectionPreviewSelected() {
    MkDocsEditorTheme {
        val authConfig = AuthConfig(
            username = "username",
            password = "password"
        )
        val authConfigs = listOf(authConfig)
        AuthConfigSection(
            modifier = Modifier.fillMaxWidth(),
            editMode = false,
            authConfigs = authConfigs,
            authConfig = authConfig,
            saveButtonEnabled = false,
            onUiEvent = {},
            currentAuthConfigUsername = "",
            currentAuthConfigPassword = "",
        )
    }
}
