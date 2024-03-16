package de.markusressel.mkdocseditor.feature.editor.ui.compose.dialog

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun SelectLinkTargetDialog(
    uiState: CodeEditorViewModel.DialogState.SelectLinkTarget,
    onItemSelected: (ResourceData) -> Unit,
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.insert_link),
                    style = MaterialTheme.typography.headlineSmall
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    uiState.items.forEach {
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { onItemSelected(it) }
                        )
                    }
                }

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
                }
            }
        }
    }
}


@CombinedPreview
@Composable
private fun SelectLinkTargetDialogPreview() {
    MkDocsEditorTheme {
        SelectLinkTargetDialog(
            uiState = CodeEditorViewModel.DialogState.SelectLinkTarget(
                items = listOf(
                    ResourceData(
                        entityId = 0,
                        id = "id",
                        name = "Image.jpg",
                        filesize = 0,
                        modtime = java.util.Date(),
                        isOfflineAvailable = true
                    ),
                    ResourceData(
                        entityId = 0,
                        id = "id",
                        name = "Some other file.txt",
                        filesize = 0,
                        modtime = java.util.Date(),
                        isOfflineAvailable = true
                    )
                ),
            ),
            onDismissRequest = { },
            onItemSelected = { }
        )
    }
}