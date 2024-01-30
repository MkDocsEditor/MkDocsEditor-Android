package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchResultCard
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ResourceSearchResultItem(
    modifier: Modifier = Modifier,
    item: SearchResultItem.Resource,
    onItemClicked: (SearchResultItem.Resource) -> Unit,
    onItemLongClicked: (SearchResultItem.Resource) -> Unit
) {
    SearchResultCard(
        modifier = modifier,
        item = item,
        onItemClicked = onItemClicked,
        onItemLongClicked = onItemLongClicked,
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = item.resourceName)
        }
    }
}

@CombinedPreview
@Composable
private fun ResourceSearchResultItemPreview() {
    MkDocsEditorTheme {
        ResourceSearchResultItem(
            item = SearchResultItem.Resource(
                resourceId = "1",
                resourceName = "Resource 1",
            ),
            onItemClicked = {},
            onItemLongClicked = {},
        )
    }
}