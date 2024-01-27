package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun ResourceSearchResultItem(
    modifier: Modifier = Modifier,
    item: SearchResultItem.Resource,
    onItemClicked: (SearchResultItem.Resource) -> Unit
) {
    Card(modifier = modifier, onClick = { onItemClicked(item) }) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = item.resourceName)
        }
    }
}

@Preview
@Composable
private fun ResourceSearchResultItemPreview() {
    MkDocsEditorTheme {
        ResourceSearchResultItem(
            item = SearchResultItem.Resource(
                resourceId = "1",
                resourceName = "Resource 1",
            ),
            onItemClicked = {},
        )
    }
}