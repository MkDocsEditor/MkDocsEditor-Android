package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun AuthSaveButton(
    modifier: Modifier,
    enabled: Boolean,
    onUiEvent: (AuthConfigUiEvent) -> Unit
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            onUiEvent(AuthConfigUiEvent.SaveButtonClicked)
        }
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = ""
        )
    }
}
