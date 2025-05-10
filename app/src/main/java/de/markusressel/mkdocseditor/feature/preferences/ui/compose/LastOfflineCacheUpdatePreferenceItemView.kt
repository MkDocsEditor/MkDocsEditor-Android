package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.markusressel.kutepreferences.ui.theme.LocalKuteColors
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun LastOfflineCacheUpdatePreferenceItemView(
    lastUpdate: String,
    isUpdating: Boolean,
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
        AnimatedContent(
            targetState = isUpdating,
            label = "lastUpdate",
        ) { isUpdating ->
            Row {
                if (isUpdating) {
                    val lineHeightDp: Dp = with(LocalDensity.current) {
                        MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                    }

                    CircularProgressIndicator(
                        modifier = Modifier.size(lineHeightDp),
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                    text = when {
                        isUpdating -> "Updating..."
                        else -> lastUpdate
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = LocalKuteColors.current.defaultItem.subtitleColor
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LastOfflineCacheUpdatePreferenceItemViewPreview() {
    MkDocsEditorTheme {
        LastOfflineCacheUpdatePreferenceItemView(
            lastUpdate = "2021-08-01 12:00:00",
            isUpdating = false
        )
    }
}

@Preview
@Composable
private fun LastOfflineCacheUpdatePreferenceItemViewUpdatingPreview() {
    MkDocsEditorTheme {
        LastOfflineCacheUpdatePreferenceItemView(
            lastUpdate = "2021-08-01 12:00:00",
            isUpdating = true
        )
    }
}