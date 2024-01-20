package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem

@Composable
internal fun SearchResultList(
    modifier: Modifier = Modifier,
    items: List<SearchResultItem>,
    onItemClicked: (SearchResultItem) -> Unit,
) {
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

@Composable
internal fun SectionSearchResultItem(
    modifier: Modifier,
    item: SearchResultItem.Section,
    onItemClicked: (SearchResultItem.Section) -> Unit
) {
    Card(modifier = modifier, onClick = { onItemClicked(item) }) {
        Column {
            Text(text = item.sectionName)
        }
    }
}

@Composable
internal fun DocumentSearchResultItem(
    modifier: Modifier,
    item: SearchResultItem.Document,
    onItemClicked: (SearchResultItem.Document) -> Unit
) {
    Card(modifier = modifier, onClick = { onItemClicked(item) }) {
        Column {
            Text(text = item.documentName)
        }
    }
}

@Composable
internal fun ResourceSearchResultItem(
    modifier: Modifier,
    item: SearchResultItem.Resource,
    onItemClicked: (SearchResultItem.Resource) -> Unit
) {
    Card(modifier = modifier, onClick = { onItemClicked(item) }) {
        Column {
            Text(text = item.resourceName)
        }
    }
}
