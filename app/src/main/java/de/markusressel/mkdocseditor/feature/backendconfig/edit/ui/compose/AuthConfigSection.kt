@file:OptIn(ExperimentalMaterial3Api::class)

package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent.AddAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent.AuthConfigChanged
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun AuthConfigSection(
    modifier: Modifier = Modifier,
    authConfigs: List<AuthConfig>,
    authConfig: AuthConfig?,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit
) {
    var currentUsername by mutableStateOf("")
    var currentPassword by mutableStateOf("")

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

                if (authConfig == null) {
                    if (authConfigs.isNotEmpty()) {
                        AuthAbortButton(
                            modifier = Modifier,
                            onClick = { onUiEvent(AuthConfigChanged(authConfigs.firstOrNull())) }
                        )
                    }
                    AuthSaveButton(
                        modifier = Modifier,
                        currentUsername = currentUsername,
                        currentPassword = currentPassword,
                        onUiEvent = onUiEvent,
                    )
                } else {
                    AuthAddButton(
                        modifier = Modifier,
                        onClick = { onUiEvent(AuthConfigChanged(null)) }
                    )
                }
            }


            if (authConfig == null) {
                OutlinedTextField(
                    value = currentUsername,
                    onValueChange = { currentUsername = it },
                    label = { Text(text = stringResource(R.string.edit_auth_config_username)) }
                )

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(text = stringResource(R.string.edit_auth_config_password)) }
                )
            } else {
                if (authConfigs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.edit_auth_config_empty)
                    )
                } else {
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
                                    text = authConfig.username
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
                                        Text(authConfig.username)
                                    }, onClick = {
                                        onUiEvent(AuthConfigChanged(item))
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthAbortButton(modifier: Modifier.Companion, onClick: () -> Unit) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = ""
        )
    }
}

@Composable
private fun AuthAddButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = ""
        )
    }
}

@Composable
private fun AuthSaveButton(
    modifier: Modifier.Companion,
    currentUsername: String,
    currentPassword: String,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = {
            onUiEvent(
                AddAuthConfig(
                    AuthConfig(username = currentUsername, password = currentPassword)
                )
            )
        }
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = ""
        )
    }
}

@CombinedPreview
@Composable
private fun AuthConfigSectionPreviewNoData() {
    MkDocsEditorTheme {
        AuthConfigSection(
            modifier = Modifier.fillMaxWidth(),
            authConfigs = emptyList(),
            authConfig = AuthConfig(
                username = "username",
                password = "password"
            ),
            onUiEvent = {}
        )
    }

}

@CombinedPreview
@Composable
private fun AuthConfigSectionPreviewEditing() {
    MkDocsEditorTheme {
        AuthConfigSection(
            modifier = Modifier.fillMaxWidth(),
            authConfigs = emptyList(),
            authConfig = null,
            onUiEvent = {}
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
            authConfigs = authConfigs,
            authConfig = authConfig,
            onUiEvent = {}
        )
    }
}
