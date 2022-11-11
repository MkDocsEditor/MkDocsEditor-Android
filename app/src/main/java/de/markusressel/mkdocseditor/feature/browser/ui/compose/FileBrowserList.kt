package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity

@Composable
internal fun FileBrowserList(
    items: List<IdentifiableListItem>,
    onDocumentClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
) {
    Column {
        items.forEachIndexed { index, item ->
            FileBrowserListEntry(
                item = item,
                onDocumentClicked = onDocumentClicked,
                onResourceClicked = onResourceClicked,
                onSectionClicked = onSectionClicked,
            )

            if (index < items.size) {
                Divider()
            }
        }
    }
}

@Composable
internal fun FileBrowserListEntry(
    item: IdentifiableListItem,
    onDocumentClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
) {
    when (item) {
        is DocumentEntity -> DocumentListEntry(
            item = item,
            onClick = onDocumentClicked
        )
        is ResourceEntity -> ResourceListEntry(
            item = item,
            onClick = onResourceClicked
        )
        is SectionEntity -> SectionListEntry(
            item = item,
            onClick = onSectionClicked
        )
    }
}

@Preview
@Composable
fun DocumentListEntryPreview() {
    DocumentListEntry(
        item = DocumentEntity(
            name = "Sample File.md"
        ),
        onClick = {}
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun DocumentListEntry(
    item: DocumentEntity,
    onClick: (DocumentEntity) -> Unit,
) {
    Card(
        onClick = { onClick(item) }
    ) {
        Text(text = item.name)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ResourceListEntry(
    item: ResourceEntity,
    onClick: (ResourceEntity) -> Unit
) {
    Card(
        onClick = { onClick(item) }
    ) {
        Text(text = item.name)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun SectionListEntry(
    item: SectionEntity,
    onClick: (SectionEntity) -> Unit
) {
    Card(
        onClick = { onClick(item) }
    ) {
        Text(text = item.name)
    }
}