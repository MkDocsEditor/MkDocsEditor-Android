package de.markusressel.mkdocseditor.feature.common.ui.compose

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import kotlinx.coroutines.delay

/** after this long, a long action says that the server is still working on it */
internal const val TAKES_LONG_AFTER_MS = 5_000L

internal const val STILL_WORKING_TAG = "still-working"
internal const val TRANSFER_PROGRESS_TAG = "transfer-progress"

/**
 * True once [working] has lasted [afterMs]. A long action never fails just for taking long, so it says so instead.
 */
@Composable
internal fun rememberTakesLong(working: Boolean, afterMs: Long = TAKES_LONG_AFTER_MS): Boolean {
    var takesLong by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        takesLong = false
        if (working) {
            delay(afterMs)
            takesLong = true
        }
    }
    return takesLong && working
}

/** Below the progress of a long action, once it takes longer than usual */
@Composable
internal fun StillWorkingNote(working: Boolean, modifier: Modifier = Modifier) {
    if (!rememberTakesLong(working)) return
    Text(
        modifier = modifier.testTag(STILL_WORKING_TAG),
        text = stringResource(R.string.long_action_still_working),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What a transfer has moved, with a way to stop it
 *
 * @param onCancel null when it can't be stopped
 */
@Composable
internal fun TransferProgressBar(
    progress: TransferProgress,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag(TRANSFER_PROGRESS_TAG)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(
                    if (progress.upload) R.string.transfer_uploading else R.string.transfer_downloading,
                    progress.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        val fraction = progress.fraction
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        val done = Formatter.formatShortFileSize(context, progress.doneBytes)
        Text(
            text = if (progress.totalBytes > 0) {
                stringResource(R.string.transfer_size, done, Formatter.formatShortFileSize(context, progress.totalBytes))
            } else {
                done
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@CombinedPreview
@Composable
private fun TransferProgressBarPreview() {
    MkDocsEditorTheme {
        TransferProgressBar(
            progress = TransferProgress(1, "holiday.mp4", upload = true, doneBytes = 12_000_000, totalBytes = 50_000_000),
            onCancel = {},
        )
    }
}
