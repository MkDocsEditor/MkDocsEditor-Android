package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity

@Composable
internal fun FileBrowserList(
    items: List<IdentifiableListItem>
) {
    Column {
        items.forEachIndexed { index, item ->
            FileBrowserListEntry(
                item = item
            )

            if (index < items.size) {
                Divider()
            }
        }
    }
}

@Composable
internal fun FileBrowserListEntry(
    item: IdentifiableListItem
) {
    when (item) {
        is DocumentEntity -> DocumentListEntry(item)
        is ResourceEntity -> ResourceListEntry(item)
        is SectionEntity -> SectionListEntry(item)
    }
}

@Preview
@Composable
fun DocumentListEntry() {
    DocumentListEntry(item = DocumentEntity(
        name = "Sample File.md"
    ))
}

@Composable
internal fun DocumentListEntry(
    item: DocumentEntity
) {
    Card {
        Text(text = item.name)
    }
}

@Composable
internal fun ResourceListEntry(
    item: ResourceEntity
) {
    Card {
        Text(text = item.name)
    }
}

@Composable
internal fun SectionListEntry(
    item: SectionEntity
) {
    Card {
        Text(text = item.name)
    }
}