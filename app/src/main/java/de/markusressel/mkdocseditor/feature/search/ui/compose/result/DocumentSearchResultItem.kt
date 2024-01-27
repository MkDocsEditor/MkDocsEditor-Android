package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun DocumentSearchResultItem(
    modifier: Modifier = Modifier,
    item: SearchResultItem.Document,
    onItemClicked: (SearchResultItem.Document) -> Unit
) {
    Card(modifier = modifier, onClick = { onItemClicked(item) }) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = item.documentName)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.inverseOnSurface)
                    .padding(8.dp)
            ) {
                Text(text = item.documentExcerpt)
            }
        }
    }
}

@Preview
@Composable
private fun DocumentSearchResultItemPreview() {
    MkDocsEditorTheme {
        DocumentSearchResultItem(
            item = SearchResultItem.Document(
                documentId = "1",
                documentName = "Document 1",
                documentExcerpt = "Excerpt 1",
            ),
            onItemClicked = {},
        )
    }
}