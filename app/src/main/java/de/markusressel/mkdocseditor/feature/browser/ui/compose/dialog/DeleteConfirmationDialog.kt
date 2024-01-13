package de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aghajari.compose.text.fromHtml
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: AnnotatedString,
    onConfirmClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row {
                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = { onDismissRequest() },
                        content = {
                            Text(stringResource(id = R.string.cancel))
                        }
                    )

                    TextButton(
                        onClick = { onConfirmClicked() },
                        content = {
                            Text(stringResource(id = R.string.delete))
                        }
                    )
                }
            }
        }
    }
}

@CombinedPreview
@Composable
private fun DeleteConfirmationDialogPreview() {
    MkDocsEditorTheme {
        DeleteConfirmationDialog(
            title = "Delete file?",
            message = "The file will be <b>permanently</b> removed for all users on this backend.".fromHtml().annotatedString,
            onConfirmClicked = {},
            onDismissRequest = {}
        )
    }
}