package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.SearchResultRow
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun SearchResultList(
    modifier: Modifier = Modifier,
    searchTerm: String,
    items: List<SearchResultItem>,
    onItemClicked: (SearchResultItem) -> Unit,
    onItemLongClicked: (SearchResultItem) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(items) { index, item ->
            if (index > 0) HorizontalDivider()
            SearchResultRow(
                searchTerm = searchTerm,
                item = item,
                onItemClicked = onItemClicked,
                onItemLongClicked = onItemLongClicked,
            )
        }
        item {
            Spacer(modifier = Modifier.imePadding())
        }
    }
}

@CombinedPreview
@Composable
private fun SearchResultListPreview() {
    MkDocsEditorTheme {
        SearchResultList(
            items = listOf(
                SearchResultItem.Document(
                    documentId = "1",
                    documentName = "Document 1",
                    excerpts = listOf(
                        SearchResultItem.Document.ExcerptData(
                            text = "An excerpt with the term in it",
                            matches = listOf(20..23),
                            heading = "A heading",
                        )
                    ),
                    path = listOf("Folder", "Subfolder"),
                    matchCount = 3,
                ),
                SearchResultItem.Section(
                    sectionId = "1",
                    sectionName = "Section 1",
                    documentCount = 3,
                ),
                SearchResultItem.Resource(
                    resourceId = "1",
                    resourceName = "Resource 1",
                    path = listOf("Folder"),
                    filesize = 1234,
                ),
            ),
            onItemClicked = {},
            onItemLongClicked = {},
            searchTerm = "Term",
        )
    }
}
