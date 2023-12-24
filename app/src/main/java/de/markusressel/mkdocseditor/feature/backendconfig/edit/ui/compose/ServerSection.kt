package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        ) {
            Text(
                text = stringResource(R.string.edit_server_config_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(text = serverConfig?.domain.orEmpty())
            Text(text = serverConfig?.port.toString())
            Text(text = serverConfig?.useSsl.toString())
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