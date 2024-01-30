package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun SectionSearchResultItem(
    modifier: Modifier = Modifier,
    item: SearchResultItem.Section,
    onItemClicked: (SearchResultItem.Section) -> Unit,
    onItemLongClicked: (SearchResultItem.Section) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .combinedClickable(
                onClick = { onItemClicked(item) },
                onLongClick = { onItemLongClicked(item) }
            )
            .then(modifier),
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = item.sectionName)
        }
    }
}

@Preview
@Composable
private fun SectionSearchResultItemPreview() {
    MkDocsEditorTheme {
        SectionSearchResultItem(
            item = SearchResultItem.Section(
                sectionId = "1",
                sectionName = "Section 1",
            ),
            onItemClicked = {},
            onItemLongClicked = {},
        )
    }
}