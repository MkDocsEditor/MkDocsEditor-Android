package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun DomainInputField(
    currentDomain: String,
    onDomainChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = currentDomain,
        onValueChange = onDomainChanged,
        label = { Text(stringResource(R.string.edit_server_config_domain_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
}


@CombinedPreview
@Composable
private fun DomainInputFieldPreview() {
    MkDocsEditorTheme {
        DomainInputField(
            currentDomain = "domain.com",
            onDomainChanged = {}
        )
    }
}