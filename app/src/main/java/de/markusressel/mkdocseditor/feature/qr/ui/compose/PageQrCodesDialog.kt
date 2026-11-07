package de.markusressel.mkdocseditor.feature.qr.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.QrCode
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.DialogState
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.QrKeyProblem
import de.markusressel.mkdocseditor.feature.qr.domain.QrKey
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The QR codes of a page: their stickers, and adding or removing codes. Printing stays with the web client.
 *
 * @param stickerUrl the URL a sticker holds for a key
 * @param readOnly the page can't be changed right now
 */
@Composable
internal fun PageQrCodesDialog(
    state: DialogState.QrCodes,
    readOnly: Boolean,
    stickerUrl: (String) -> String,
    onKeyChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        modifier = Modifier.testTag("page-qr-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_codes_action)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.qr_codes_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.keys.isEmpty()) {
                    Text(stringResource(R.string.qr_codes_none))
                }
                state.keys.forEach { code ->
                    QrKeyRow(
                        code = code,
                        url = stickerUrl(code.key),
                        readOnly = readOnly,
                        onCopy = { context.copyLink(stickerUrl(code.key)) },
                        onRemove = { onRemove(code.key) },
                    )
                }
                if (!readOnly) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("page-qr-key"),
                        value = state.key,
                        onValueChange = onKeyChanged,
                        label = { Text(stringResource(R.string.qr_codes_key)) },
                        supportingText = { Text(state.problem?.let { problemText(it) } ?: stringResource(R.string.qr_codes_key_hint)) },
                        isError = state.problem != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    )
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("page-qr-label"),
                        value = state.label,
                        onValueChange = onLabelChanged,
                        label = { Text(stringResource(R.string.qr_codes_label)) },
                        singleLine = true,
                    )
                    if (state.checking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        modifier = Modifier.testTag("page-qr-add"),
                        enabled = !state.checking && state.key.isNotBlank() && state.problem.let { it == null || it == QrKeyProblem.Unchecked },
                        onClick = onAdd,
                    ) {
                        Text(stringResource(R.string.qr_codes_add))
                    }
                }
                if (state.keys.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.qr_codes_print_on_web),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun QrKeyRow(code: QrKey, url: String, readOnly: Boolean, onCopy: () -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.testTag("page-qr-code"), verticalAlignment = Alignment.CenterVertically) {
        QrCode(text = url, contentDescription = stringResource(R.string.qr_codes_image, code.key), size = 96.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = code.key, fontFamily = FontFamily.Monospace)
            code.label?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onCopy) {
            Image(
                asset = GoogleMaterial.Icon.gmd_content_copy,
                contentDescription = stringResource(R.string.qr_codes_copy, code.key),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        if (!readOnly) {
            IconButton(modifier = Modifier.testTag("page-qr-remove"), onClick = onRemove) {
                Image(
                    asset = GoogleMaterial.Icon.gmd_delete,
                    contentDescription = stringResource(R.string.qr_codes_remove, code.key),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun problemText(problem: QrKeyProblem): String = when (problem) {
    QrKeyProblem.Invalid -> stringResource(R.string.qr_codes_error_invalid)
    QrKeyProblem.OnPage -> stringResource(R.string.qr_codes_error_on_page)
    is QrKeyProblem.Elsewhere -> stringResource(R.string.qr_codes_error_elsewhere, problem.title)
    QrKeyProblem.Unchecked -> stringResource(R.string.qr_codes_error_unchecked)
}

private fun Context.copyLink(url: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
    Toast.makeText(this, getString(R.string.qr_codes_copied), Toast.LENGTH_SHORT).show()
}

@CombinedPreview
@Composable
private fun PageQrCodesDialogPreview() {
    MkDocsEditorTheme {
        PageQrCodesDialog(
            state = DialogState.QrCodes(
                keys = listOf(QrKey("waschmaschine", "Keller, an der Maschine")),
                key = "trockner",
            ),
            readOnly = false,
            stickerUrl = { "https://wiki.example.com/q/$it" },
            onKeyChanged = {},
            onLabelChanged = {},
            onAdd = {},
            onRemove = {},
            onDismiss = {},
        )
    }
}
