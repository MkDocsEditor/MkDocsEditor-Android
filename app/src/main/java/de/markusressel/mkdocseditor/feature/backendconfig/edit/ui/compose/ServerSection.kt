package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun ServerSection(
    modifier: Modifier = Modifier,
    serverConfig: BackendServerConfig?,
    onUiEvent: (BackendConfigEditViewModel.UiEvent) -> Unit
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.edit_server_config_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.edit_server_config_domain),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = serverConfig?.domain.orEmpty())
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.edit_server_config_port),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = serverConfig?.port?.toString().orEmpty())
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.edit_server_config_use_ssl),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Checkbox(
                    checked = serverConfig?.useSsl ?: false,
                    enabled = false,
                    onCheckedChange = {})
            }
        }
    }
}


@Preview
@Composable
private fun ServerSectionSectionPreview() {
    MkDocsEditorTheme {
        ServerSection(
            serverConfig = BackendServerConfig(
                domain = "domain.com",
                port = 443,
                useSsl = true,
            ),
            onUiEvent = {}
        )
    }
}