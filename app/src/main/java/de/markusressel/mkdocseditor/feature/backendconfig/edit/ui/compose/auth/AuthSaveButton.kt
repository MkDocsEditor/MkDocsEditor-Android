package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent.AuthConfigSaveButtonClicked

@Composable
internal fun AuthSaveButton(
    modifier: Modifier,
    enabled: Boolean,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            onUiEvent(
                AuthConfigSaveButtonClicked
            )
        }
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = ""
        )
    }
}
