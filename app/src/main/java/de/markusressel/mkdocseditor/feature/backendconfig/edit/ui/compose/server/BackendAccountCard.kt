package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * Shows whether this device has a session on the backend
 */
@Composable
internal fun BackendAccountCard(
    modifier: Modifier = Modifier,
    signedInUsername: String?,
    onSignInClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_account_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (signedInUsername) {
                    null -> stringResource(R.string.auth_signed_out)
                    else -> stringResource(R.string.auth_signed_in_as, signedInUsername)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (signedInUsername == null) {
                    Button(onClick = onSignInClicked) {
                        Text(stringResource(R.string.auth_sign_in))
                    }
                } else {
                    OutlinedButton(onClick = onSignOutClicked) {
                        Text(stringResource(R.string.auth_sign_out))
                    }
                }
            }
        }
    }
}

@CombinedPreview
@Composable
private fun BackendAccountCardPreview() {
    MkDocsEditorTheme {
        BackendAccountCard(
            signedInUsername = "markus",
            onSignInClicked = {},
            onSignOutClicked = {},
        )
    }
}
