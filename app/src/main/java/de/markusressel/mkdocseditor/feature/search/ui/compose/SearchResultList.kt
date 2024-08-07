package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.DocumentSearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.ResourceSearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.result.SectionSearchResultItem
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            item {
                when (item) {
                    is SearchResultItem.Document -> {
                        DocumentSearchResultItem(
                            modifier = Modifier.fillMaxWidth(),
                            searchTerm = searchTerm,
                            item = item,
                            onItemClicked = onItemClicked,
                            onItemLongClicked = onItemLongClicked,
                        )
                    }

                    is SearchResultItem.Section -> {
                        SectionSearchResultItem(
                            modifier = Modifier.fillMaxWidth(),
                            item = item,
                            onItemClicked = onItemClicked,
                            onItemLongClicked = onItemLongClicked,
                        )
                    }

                    is SearchResultItem.Resource -> {
                        ResourceSearchResultItem(
                            modifier = Modifier.fillMaxWidth(),
                            item = item,
                            onItemClicked = onItemClicked,
                            onItemLongClicked = onItemLongClicked,
                        )
                    }
                }
            }
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
                            excerpt = AnnotatedString("Excerpt 1"),
                            charsBefore = 10,
                            charsAfter = 10,
                            charBeforeExcerptIsNewline = false,
                            charAfterExcerptIsNewline = false,
                        )
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
            searchTerm = "Term",
        )
    }
}