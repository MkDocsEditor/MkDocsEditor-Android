package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.theme.LocalKuteColors
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * Shows when the offline cache was updated last. Laid out like the default items of KutePreferences, so it fits in
 * between them.
 */
@Composable
internal fun LastOfflineCacheUpdatePreferenceItemView(
    title: String,
    icon: Drawable?,
    lastUpdate: String,
    isUpdating: Boolean,
) {
    val colors = LocalKuteColors.current.defaultItem
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                modifier = Modifier
                    .size(28.dp)
                    .padding(4.dp),
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconColor),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.titleColor,
            )
            AnimatedContent(
                targetState = isUpdating,
                label = "lastUpdate",
            ) { isUpdating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isUpdating) {
                        val lineHeightDp: Dp = with(LocalDensity.current) {
                            MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.size(lineHeightDp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = when {
                            isUpdating -> stringResource(R.string.last_offline_cache_update_running)
                            else -> lastUpdate
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtitleColor,
                    )
                }
            }
        }
    }
}

@CombinedPreview
@Composable
private fun LastOfflineCacheUpdatePreferenceItemViewPreview() {
    MkDocsEditorTheme {
        KutePreferencesTheme(colors = settingsColors()) {
            LastOfflineCacheUpdatePreferenceItemView(
                title = "Last offline cache update",
                icon = null,
                lastUpdate = "2021-08-01 12:00:00",
                isUpdating = false,
            )
        }
    }
}

@CombinedPreview
@Composable
private fun LastOfflineCacheUpdatePreferenceItemViewUpdatingPreview() {
    MkDocsEditorTheme {
        KutePreferencesTheme(colors = settingsColors()) {
            LastOfflineCacheUpdatePreferenceItemView(
                title = "Last offline cache update",
                icon = null,
                lastUpdate = "2021-08-01 12:00:00",
                isUpdating = true,
            )
        }
    }
}
