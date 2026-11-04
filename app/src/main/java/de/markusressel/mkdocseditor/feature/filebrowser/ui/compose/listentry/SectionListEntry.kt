package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.lastChange
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.feature.theme.sectionBackgroundColor
import de.markusressel.mkdocseditor.util.compose.CombinedPreview


@Composable
internal fun SectionListEntry(
    item: SectionData,
    onClick: (SectionData) -> Unit,
    onLongClick: (SectionData) -> Unit,
    modifier: Modifier = Modifier
) {
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
            defaultElevation = 4.dp
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_folder,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.sectionBackgroundColor),
            )

            Column {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = item.contentSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                item.lastChange()?.let { lastChange ->
                    LastModifiedDate(
                        modifier = Modifier.padding(start = 8.dp),
                        modtime = lastChange,
                    )
                }
            }
        }
    }
}

/** only what is in the folder, so that the entry stays small */
@Composable
private fun SectionData.contentSummary(): String = listOfNotNull(
    subsections.size.takeIf { it > 0 }?.let { stringResource(R.string.section_entry_subsections_count, it) },
    documents.size.takeIf { it > 0 }?.let { stringResource(R.string.section_entry_documents_count, it) },
    resources.size.takeIf { it > 0 }?.let { stringResource(R.string.section_entry_resources_count, it) },
).joinToString(" · ").ifEmpty { stringResource(R.string.file_browser_empty) }

@CombinedPreview
@Composable
private fun SectionListEntryPreview() {
    MkDocsEditorTheme {
        SectionListEntry(
            item = SectionData(
                entityId = 1,
                id = "1",
                name = "Sample Section",
                subsections = listOf(),
                documents = listOf(),
                resources = listOf()
            ),
            onClick = {},
            onLongClick = {}
        )
    }
}
