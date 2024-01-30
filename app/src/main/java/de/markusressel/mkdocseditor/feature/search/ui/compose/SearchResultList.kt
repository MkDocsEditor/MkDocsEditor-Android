package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    onItemLongClicked: (SearchResultItem) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            when (item) {
                is SearchResultItem.Document -> {
                    DocumentSearchResultItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        item = item,
                        onItemClicked = onItemClicked,
                        onItemLongClicked = onItemLongClicked,
                    )
                }

                is SearchResultItem.Section -> {
                    SectionSearchResultItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        item = item,
                        onItemClicked = onItemClicked,
                        onItemLongClicked = onItemLongClicked,
                    )
                }

                is SearchResultItem.Resource -> {
                    ResourceSearchResultItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        item = item,
                        onItemClicked = onItemClicked,
                        onItemLongClicked = onItemLongClicked,
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
                    documentExcerptData = SearchResultItem.Document.ExcerptData(
                        charsBefore = 10,
                        excerpt = "Excerpt 1",
                        charsAfter = 10,
                    ),
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
            onItemLongClicked = {},
        )
    }
}