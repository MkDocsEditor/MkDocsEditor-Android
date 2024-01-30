package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.ui.KodeText
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun DocumentSearchResultItem(
    modifier: Modifier = Modifier,
    item: SearchResultItem.Document,
    onItemClicked: (SearchResultItem.Document) -> Unit,
    onItemLongClicked: (SearchResultItem.Document) -> Unit
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
            Text(text = item.documentName)

            if (item.documentExcerptData != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.inverseOnSurface)
                        .padding(8.dp)
                ) {
                    val excerptData = item.documentExcerptData

                    val text = listOfNotNull(
                        "[…]".takeIf { excerptData.charsBefore > 0 },
                        excerptData.excerpt,
                        "[…]".takeIf { excerptData.charsAfter > 0 },
                    ).joinToString(separator = " ")

                    KodeText(
                        text = text,
                        languageRuleBook = MarkdownRuleBook(),
                        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
                        textColor = MaterialTheme.colorScheme.onBackground,
                        // TODO: add support for text style
//                        textStyle = TextStyle(
//                            fontFamily = FontFamily.Monospace,
//                        ),
                    )
                }
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
                documentExcerptData = SearchResultItem.Document.ExcerptData(
                    charsBefore = 10,
                    excerpt = "Excerpt 1",
                    charsAfter = 10,
                ),
            ),
            onItemClicked = {},
            onItemLongClicked = {},
        )
    }
}