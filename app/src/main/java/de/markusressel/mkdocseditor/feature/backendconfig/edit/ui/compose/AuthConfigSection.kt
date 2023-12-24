package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun AuthConfigSection(
    modifier: Modifier = Modifier,
    authConfig: BackendAuthConfig?,
    onValueChanged: (BackendAuthConfig?) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(text = stringResource(R.string.edit_auth_config_title))

            Text(text = authConfig?.username.orEmpty())
            Text(text = authConfig?.password.orEmpty())
        }
    }
}

@Preview
@Composable
private fun AuthConfigSectionPreview() {
    MkDocsEditorTheme {
        AuthConfigSection(
            authConfig = BackendAuthConfig(
                username = "username",
                password = "password"
            ),
            onValueChanged = {}
        )
    }
}