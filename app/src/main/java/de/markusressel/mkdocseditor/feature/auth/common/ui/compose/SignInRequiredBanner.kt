package de.markusressel.mkdocseditor.feature.auth.common.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * Tells the user that the session ended, and that local data is still there
 */
@Composable
internal fun SignInRequiredBanner(
    modifier: Modifier = Modifier,
    onSignInClicked: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_session_ended_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.auth_session_ended_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSignInClicked) {
                Text(stringResource(R.string.auth_sign_in))
            }
        }
    }
}

@CombinedPreview
@Composable
private fun SignInRequiredBannerPreview() {
    MkDocsEditorTheme {
        SignInRequiredBanner(onSignInClicked = {})
    }
}
