package de.markusressel.mkdocseditor.feature.browser.ui.compose.listentry

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.feature.theme.documentBackgroundColor
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date


@Composable
internal fun DocumentListEntry(
    item: DocumentData,
    onClick: (DocumentData) -> Unit,
    onLongClick: (DocumentData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.elevatedShape)
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Image(
                    modifier = Modifier
                        .size(32.dp),
                    asset = MaterialDesignIconic.Icon.gmi_file,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.documentBackgroundColor),
                )

                if (item.isOfflineAvailable) {
                    Image(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        asset = MaterialDesignIconic.Icon.gmi_save,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }

            Column {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = item.name,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(
                        R.string.document_list_entry_filesize,
                        item.formattedDocumentSize(context)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                LastModifiedDate(
                    modifier = Modifier.padding(start = 8.dp),
                    modtime = item.modtime
                )
            }
        }
    }
}


@CombinedPreview
@Composable
private fun DocumentListEntryPreview() {
    MkDocsEditorTheme {
        DocumentListEntry(
            item = DocumentData(
                entityId = 1,
                id = "1",
                name = "Sample File",
                filesize = 1234,
                modtime = Date(),
                url = "https://example.com/sample-file.md",
                content = null,
                isOfflineAvailable = true,
            ),
            onClick = {},
            onLongClick = {}
        )
    }
}
