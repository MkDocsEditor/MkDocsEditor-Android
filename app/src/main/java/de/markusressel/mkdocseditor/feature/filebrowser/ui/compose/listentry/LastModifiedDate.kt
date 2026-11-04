package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry

import androidx.annotation.PluralsRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date

/**
 * Shows how long ago something changed, and nothing if that was long ago
 */
@Composable
fun LastModifiedDate(
    modtime: Date,
    modifier: Modifier = Modifier
) {
    val age = changeAge(modtime, Date()) ?: return
    Text(
        modifier = modifier,
        text = pluralStringResource(age.text, age.count, age.count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal data class ChangeAge(@param:PluralsRes val text: Int, val count: Int)

/** @return how long ago [modtime] was, null if that is too long ago to be of interest */
internal fun changeAge(modtime: Date, now: Date): ChangeAge? {
    // the clocks of the server and the device can differ a bit
    val minutes = ((now.time - modtime.time) / MINUTE_MS).coerceAtLeast(1)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= HIDDEN_AFTER_DAYS -> null
        days >= 30 -> ChangeAge(R.plurals.change_age_months, (days / 30).toInt())
        days >= 7 -> ChangeAge(R.plurals.change_age_weeks, (days / 7).toInt())
        days >= 1 -> ChangeAge(R.plurals.change_age_days, days.toInt())
        hours >= 1 -> ChangeAge(R.plurals.change_age_hours, hours.toInt())
        else -> ChangeAge(R.plurals.change_age_minutes, minutes.toInt())
    }
}

private const val MINUTE_MS = 60_000L
private const val HIDDEN_AFTER_DAYS = 365

@CombinedPreview
@Composable
private fun LastModifiedDatePreview() {
    MkDocsEditorTheme {
        LastModifiedDate(Date())
    }
}