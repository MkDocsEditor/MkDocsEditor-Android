package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ServerSection(
    modifier: Modifier = Modifier,
    currentDomain: String,
    onDomainChanged: (String) -> Unit,
    currentPort: String,
    onPortChanged: (String) -> Unit,
    useSsl: Boolean,
    onUseSslCheckedChanged: (Boolean) -> Unit,
    currentWebBaseUri: String,
    onWebBaseUriChanged: (String) -> Unit,
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

            DomainInputField(
                currentDomain = currentDomain,
                onDomainChanged = onDomainChanged,
            )

            PortInputField(
                currentPort = currentPort,
                onPortChanged = onPortChanged,
            )

            SslCheckbox(
                checked = useSsl,
                onCheckedChanged = onUseSslCheckedChanged,
            )

            WebBaseUriInputField(
                currentWebBaseUri = currentWebBaseUri,
                onWebBaseUriChanged = onWebBaseUriChanged,
            )
        }
    }
}


@CombinedPreview
@Composable
private fun ServerSectionSectionPreview() {
    MkDocsEditorTheme {
        ServerSection(
            currentDomain = "domain.com",
            onDomainChanged = {},
            currentPort = "443",
            onPortChanged = {},
            useSsl = true,
            onUseSslCheckedChanged = {},
            currentWebBaseUri = "https://domain.com",
            onWebBaseUriChanged = {},
        )
    }
}