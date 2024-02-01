package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchResultCard
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.feature.theme.documentBackgroundColor
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun DocumentSearchResultItem(
    modifier: Modifier = Modifier,
    searchTerm: String,
    item: SearchResultItem.Document,
    onItemClicked: (SearchResultItem.Document) -> Unit,
    onItemLongClicked: (SearchResultItem.Document) -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                    asset = MaterialDesignIconic.Icon.gmi_file,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.documentBackgroundColor),
                )
                Text(text = item.documentName)
            }

            if (item.excerpts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val excerptLimit = 3
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.excerpts.take(excerptLimit).forEach { excerpt ->
                        DocumentExcerpt(
                            searchTerm = searchTerm,
                            excerpt = excerpt
                        )
                    }

                    if (item.excerpts.size > excerptLimit) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.additional_search_term_matches_within_document,
                                item.excerpts.size - excerptLimit,
                                item.excerpts.size - excerptLimit
                            ),
                        )
                    }
                }
            }
        }
    }
}


@CombinedPreview
@Composable
private fun DocumentSearchResultItemPreview() {
    MkDocsEditorTheme {
        DocumentSearchResultItem(
            item = SearchResultItem.Document(
                documentId = "1",
                documentName = "Document 1",
                excerpts = listOf(
                    SearchResultItem.Document.ExcerptData(
                        excerpt = AnnotatedString("Excerpt 1"),
                        charsBefore = 10,
                        charsAfter = 10,
                        charBeforeExcerptIsNewline = false,
                        charAfterExcerptIsNewline = false,
                    ),
                    SearchResultItem.Document.ExcerptData(
                        excerpt = AnnotatedString("Excerpt 2"),
                        charsBefore = 10,
                        charsAfter = 10,
                        charBeforeExcerptIsNewline = false,
                        charAfterExcerptIsNewline = false,
                    ),
                    SearchResultItem.Document.ExcerptData(
                        excerpt = AnnotatedString("Excerpt 3"),
                        charsBefore = 10,
                        charsAfter = 10,
                        charBeforeExcerptIsNewline = false,
                        charAfterExcerptIsNewline = false,
                    ),
                    SearchResultItem.Document.ExcerptData(
                        excerpt = AnnotatedString("Excerpt 4"),
                        charsBefore = 10,
                        charsAfter = 10,
                        charBeforeExcerptIsNewline = false,
                        charAfterExcerptIsNewline = false,
                    ),
                ),
            ),
            onItemClicked = {},
            onItemLongClicked = {},
            searchTerm = "Exc",
        )
    }
}