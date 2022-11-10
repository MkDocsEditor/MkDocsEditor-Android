package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity

@Composable
internal fun FileBrowserList(
    items: List<IdentifiableListItem>
) {
    Column {
        items.forEach {
            FileBrowserListEntry(
                items = items
            )
        }

    }
}

@Composable
internal fun FileBrowserListEntry(
    item: IdentifiableListItem
) {
    when (item) {
        is DocumentEntity -> DocumentListEntry(item)
    }
}

@Composable
internal fun DocumentListEntry(
    item: DocumentEntity
) {

}