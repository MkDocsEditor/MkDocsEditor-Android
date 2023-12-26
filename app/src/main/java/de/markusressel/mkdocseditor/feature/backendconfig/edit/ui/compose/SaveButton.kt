package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R

@Composable
internal fun SaveButton(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick
    ) {
        Text(stringResource(R.string.save))
    }
}