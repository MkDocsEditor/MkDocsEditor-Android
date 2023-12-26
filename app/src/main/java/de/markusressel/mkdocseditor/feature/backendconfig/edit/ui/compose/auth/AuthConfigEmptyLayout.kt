package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R

@Composable
internal fun AuthConfigEmptyLayout() {
    Text(
        text = stringResource(R.string.edit_auth_config_empty)
    )
}