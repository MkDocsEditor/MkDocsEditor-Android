package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.kutepreferences.ui.theme.LocalKuteColors

@Composable
internal fun LastOfflineCacheUpdatePreferenceItemView(
    lastUpdate: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            text = "Last Offline Cache Update",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = LocalKuteColors.current.defaultItem.titleColor
            ),
        )
        Text(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            text = lastUpdate,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = LocalKuteColors.current.defaultItem.subtitleColor
            ),
        )
    }
}