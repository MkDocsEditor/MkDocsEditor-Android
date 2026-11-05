package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * Picks the color of the own cursor and selection for now, the profile keeps the color of the account
 *
 * @param current the color chosen so far as `#rrggbb`, null if none
 * @param onColorChosen gets `#rrggbb`, or null to go back to the color of the account
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CursorColorDialog(
    current: String?,
    onColorChosen: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cursor_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.cursor_color_explanation))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    fallbackPeerColors.forEach { color ->
                        val value = color.toPeerColorString()
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(color, CircleShape)
                                .then(
                                    if (value == current) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(onClickLabel = value) { onColorChosen(value) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorChosen(null) }) {
                Text(stringResource(R.string.cursor_color_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@CombinedPreview
@Composable
private fun CursorColorDialogPreview() {
    MkDocsEditorTheme {
        CursorColorDialog(current = "#3d8fe0", onColorChosen = {}, onDismiss = {})
    }
}
