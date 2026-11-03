package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.domain.NewItemNameProblem
import de.markusressel.mkdocseditor.feature.filebrowser.domain.newItemNameProblem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.NewItemKind
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * Asks for the name of a new document or section. The dialog stays open until the name can work, so that what was typed
 * is not lost.
 *
 * @param kind what is created
 * @param folder what is in the folder it goes into, to find a name that is taken
 */
@Composable
internal fun CreateItemDialog(
    kind: NewItemKind,
    folder: FolderContent?,
    onDismissRequest: () -> Unit,
    onCreateClicked: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismissRequest) {
        CreateItemForm(
            kind = kind,
            folder = folder,
            nameFieldModifier = Modifier.focusRequester(focusRequester),
            onDismissRequest = onDismissRequest,
            onCreateClicked = onCreateClicked,
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
internal fun CreateItemForm(
    kind: NewItemKind,
    folder: FolderContent?,
    onDismissRequest: () -> Unit,
    onCreateClicked: (String) -> Unit,
    nameFieldModifier: Modifier = Modifier,
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
            var text by rememberSaveable { mutableStateOf("") }
            val problem = newItemNameProblem(text, folder)

            Text(
                text = stringResource(
                    when (kind) {
                        NewItemKind.Document -> R.string.create_document
                        NewItemKind.Section -> R.string.create_section
                    }
                ),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(nameFieldModifier),
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = problem != null && problem != NewItemNameProblem.Blank,
                label = {
                    Text(
                        stringResource(
                            when (kind) {
                                NewItemKind.Document -> R.string.create_document_hint
                                NewItemKind.Section -> R.string.create_section_hint
                            }
                        )
                    )
                },
                supportingText = when (problem) {
                    NewItemNameProblem.NotAllowed -> {
                        { Text(stringResource(R.string.new_item_name_not_allowed)) }
                    }

                    NewItemNameProblem.AlreadyExists -> {
                        { Text(stringResource(R.string.new_item_name_exists)) }
                    }

                    NewItemNameProblem.Blank, null -> null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (problem == null) onCreateClicked(text) }),
            )

            Row(horizontalArrangement = Arrangement.End) {
                Spacer(modifier = Modifier.weight(1f, fill = true))

                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(id = R.string.cancel))
                }

                TextButton(
                    enabled = problem == null,
                    onClick = { onCreateClicked(text) },
                ) {
                    Text(stringResource(id = R.string.create))
                }
            }
        }
    }
}

@CombinedPreview
@Composable
private fun CreateItemDialogPreview() {
    MkDocsEditorTheme {
        CreateItemDialog(
            kind = NewItemKind.Document,
            folder = FolderContent(),
            onDismissRequest = { },
            onCreateClicked = { },
        )
    }
}
