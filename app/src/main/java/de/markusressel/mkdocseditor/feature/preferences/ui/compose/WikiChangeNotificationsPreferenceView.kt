package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.markusressel.kutepreferences.core.preference.bool.BooleanPreferenceBehavior
import de.markusressel.kutepreferences.ui.views.listitems.BooleanPreference
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The switch for notifications about changes, and a hint while Android blocks them. Turning it on asks for the
 * permission. The hint opens the notification settings of the app, because Android stops showing the question after
 * the user said no twice.
 */
@Composable
internal fun WikiChangeNotificationsPreferenceView(
    behavior: BooleanPreferenceBehavior,
    isGranted: () -> Boolean,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isGranted()) }
    // the user may come back from the system settings
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = isGranted() }

    val enabled by behavior.persistedValue.collectAsState()
    var enabledBefore by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        if (enabled && !enabledBefore && !granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        enabledBefore = enabled
    }

    Column {
        BooleanPreference(behavior)
        if (enabled && !granted) {
            NotificationsBlockedHint(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            })
        }
    }
}

@Composable
private fun NotificationsBlockedHint(onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        text = stringResource(R.string.wiki_change_notifications_blocked),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@CombinedPreview
@Composable
private fun NotificationsBlockedHintPreview() {
    MkDocsEditorTheme {
        NotificationsBlockedHint(onClick = {})
    }
}
