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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.ui.DialogState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
fun CreateDocumentDialog(
    uiState: DialogState.CreateDocument,
    onDismissRequest: () -> Unit,
    onSaveClicked: (String) -> Unit,
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
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
                var text by remember { mutableStateOf(uiState.initialDocumentName) }

                Text(
                    text = stringResource(id = R.string.create_document),
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(id = R.string.create_document_hint)) }
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                ) {
                    Spacer(modifier = Modifier.weight(1f, fill = true))

                    TextButton(
                        onClick = { onDismissRequest() },
                        content = {
                            Text(stringResource(id = R.string.cancel))
                        }
                    )

                    TextButton(
                        onClick = { onSaveClicked(text) },
                        content = {
                            Text(stringResource(id = R.string.create))
                        }
                    )
                }
            }
        }
    }
}

@CombinedPreview
@Composable
private fun CreateDocumentDialogPreview() {
    MkDocsEditorTheme {
        CreateDocumentDialog(
            uiState = DialogState.CreateDocument(
                sectionId = "",
                initialDocumentName = ""
            ),
            onDismissRequest = { },
            onSaveClicked = { }
        )
    }
}