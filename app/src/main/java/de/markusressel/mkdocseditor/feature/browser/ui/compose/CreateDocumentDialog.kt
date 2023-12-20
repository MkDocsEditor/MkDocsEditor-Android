package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.ui.DialogState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
fun CreateDocumentDialog(
    uiState: DialogState.CreateDocument,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                var text by remember { mutableStateOf(uiState.currentDocumentName) }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.Center),
                    text = stringResource(id = R.string.create_document),
                    style = MaterialTheme.typography.headlineMedium
                )

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(id = R.string.create_document_hint)) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun CreateDocumentDialogPreview() {
    MkDocsEditorTheme {
        CreateDocumentDialog(
            uiState = DialogState.CreateDocument(
                currentSectionId = "",
                currentDocumentName = ""
            ),
            onDismissRequest = { },
        )
    }
}