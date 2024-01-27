package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.DocumentSearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.ResourceSearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.SectionSearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun SearchResultList(
    modifier: Modifier = Modifier,
    items: List<SearchResultItem>,
    onItemClicked: (SearchResultItem) -> Unit,
) {
    Column {
        items.forEach { item ->
            when (item) {
                is SearchResultItem.Document -> {
                    DocumentSearchResultItem(
                        modifier = modifier,
                        item = item,
                        onItemClicked = onItemClicked,
                    )
                }

                is SearchResultItem.Section -> {
                    SectionSearchResultItem(
                        modifier = modifier,
                        item = item,
                        onItemClicked = onItemClicked,
                    )
                }

                is SearchResultItem.Resource -> {
                    ResourceSearchResultItem(
                        modifier = modifier,
                        item = item,
                        onItemClicked = onItemClicked,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SearchResultListPreview() {
    MkDocsEditorTheme {
        SearchResultList(
            items = listOf(
                SearchResultItem.Document(
                    documentId = "1",
                    documentName = "Document 1",
                    documentExcerpt = "Excerpt 1",
                ),
                SearchResultItem.Section(
                    sectionId = "1",
                    sectionName = "Section 1",
                ),
                SearchResultItem.Resource(
                    resourceId = "1",
                    resourceName = "Resource 1",
                ),
            ),
            onItemClicked = {},
        )
    }
}