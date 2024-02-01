package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.SearchUseCase
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview


@Composable
internal fun DocumentExcerpt(
    searchTerm: String,
    excerpt: SearchResultItem.Document.ExcerptData
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface)
            .padding(8.dp)
    ) {

        val text = buildAnnotatedString {
            if (excerpt.charsBefore > 0) {
                withStyle(SpanStyle(color = Color.Gray)) {
                    append(SearchUseCase.QuoteEllipsis)
                    if (excerpt.charBeforeExcerptIsNewline) {
                        append("\n")
                    } else {
                        append(" ")
                    }
                }
            }
            append(excerpt.excerpt)
            if (excerpt.charsAfter > 0) {
                withStyle(SpanStyle(color = Color.Gray)) {
                    if (excerpt.charAfterExcerptIsNewline) {
                        append("\n")
                    } else {
                        append(" ")
                    }
                    append(SearchUseCase.QuoteEllipsis)
                }
            }
        }

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@CombinedPreview
@Composable
private fun DocumentExcerptPreview() {
    MkDocsEditorTheme {
        DocumentExcerpt(
            searchTerm = "searchTerm",
            excerpt = SearchResultItem.Document.ExcerptData(
                excerpt = AnnotatedString("This is a test excerpt"),
                charsBefore = 20,
                charsAfter = 50,
                charBeforeExcerptIsNewline = false,
                charAfterExcerptIsNewline = false
            )
        )
    }
}
