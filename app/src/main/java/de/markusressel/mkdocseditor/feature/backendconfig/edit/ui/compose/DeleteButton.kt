package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun DeleteButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = onClick
    ) {
        Text(stringResource(R.string.delete))
    }
}

@CombinedPreview
@Composable
private fun DeleteButtonPreview() {
    MkDocsEditorTheme {
        SaveButton(
            modifier = Modifier,
            enabled = true,
            onClick = {}
        )
    }
}