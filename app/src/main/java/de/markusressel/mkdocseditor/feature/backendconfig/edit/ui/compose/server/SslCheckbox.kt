package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun SslCheckbox(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChanged(checked.not()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.edit_server_config_use_ssl),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked,
            enabled = true,
            onCheckedChange = onCheckedChanged
        )
    }
}

@CombinedPreview
@Composable
private fun SslCheckboxPreview() {
    MkDocsEditorTheme {
        SslCheckbox(
            checked = true,
            onCheckedChanged = {}
        )
    }
}