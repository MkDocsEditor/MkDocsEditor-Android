package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.toSearchRegex
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.feature.theme.documentBackgroundColor
import de.markusressel.mkdocseditor.feature.theme.resourceBackgroundColor
import de.markusressel.mkdocseditor.feature.theme.sectionBackgroundColor
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date

private const val EXCERPT_LIMIT = 2
private const val HEADING_LENGTH_LIMIT = 24

/**
 * A search result: what it is and its name, where it is, and for pages the lines that match
 */
@Composable
internal fun SearchResultRow(
    modifier: Modifier = Modifier,
    searchTerm: String,
    item: SearchResultItem,
    onItemClicked: (SearchResultItem) -> Unit,
    onItemLongClicked: (SearchResultItem) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val icon: IIcon
    val details: String?
    var matchCount = 0
    var excerpts = emptyList<SearchResultItem.Document.ExcerptData>()
    val iconColor = when (item) {
        is SearchResultItem.Document -> {
            icon = MaterialDesignIconic.Icon.gmi_file
            details = item.modtime?.relativeToNow()
            matchCount = item.matchCount
            excerpts = item.excerpts
            colors.documentBackgroundColor
        }

        is SearchResultItem.Section -> {
            icon = MaterialDesignIconic.Icon.gmi_folder
            details = item.summary()
            colors.sectionBackgroundColor
        }

        is SearchResultItem.Resource -> {
            icon = MaterialDesignIconic.Icon.gmi_attachment
            details = item.summary()
            colors.resourceBackgroundColor
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onItemClicked(item) },
                onLongClick = { onItemLongClicked(item) },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            asset = icon,
            colorFilter = ColorFilter.tint(iconColor),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = highlighted(item.name, searchTerm.toSearchRegex().findAll(item.name).map { it.range }.toList()),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (matchCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.search_match_count, matchCount, matchCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // the start is what gets cut: the folder right above the result tells the most
                Text(
                    modifier = Modifier.weight(1f),
                    text = item.path.joinToString(" › ").ifEmpty { stringResource(R.string.search_location_root) },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
                details?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            excerpts.take(EXCERPT_LIMIT).forEach { excerpt ->
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = excerptText(excerpt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun excerptText(excerpt: SearchResultItem.Document.ExcerptData): AnnotatedString {
    val headingStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    val highlightedText = highlighted(excerpt.text, excerpt.matches)
    return buildAnnotatedString {
        excerpt.heading?.let { heading ->
            withStyle(headingStyle) {
                append(
                    if (heading.length > HEADING_LENGTH_LIMIT) heading.take(HEADING_LENGTH_LIMIT).trimEnd() + "…" else heading
                )
            }
            append(" · ")
        }
        if (excerpt.truncatedStart) append("…")
        append(highlightedText)
        if (excerpt.truncatedEnd) append("…")
    }
}

@Composable
private fun highlighted(text: String, matches: List<IntRange>): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    return buildAnnotatedString {
        append(text)
        matches.forEach { addStyle(style, it.first, it.last + 1) }
    }
}

@Composable
private fun SearchResultItem.Section.summary(): String? = listOfNotNull(
    documentCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.search_section_documents, it, it) },
    sectionCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.search_section_sections, it, it) },
).joinToString(" · ").ifEmpty { null }

@Composable
private fun SearchResultItem.Resource.summary(): String? {
    val context = LocalContext.current
    return listOfNotNull(
        filesize.takeIf { it >= 0 }?.let { Formatter.formatShortFileSize(context, it) },
        modtime?.relativeToNow(),
    ).joinToString(" · ").ifEmpty { null }
}

private fun Date.relativeToNow(): String = DateUtils.getRelativeTimeSpanString(
    time,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE,
).toString()

@CombinedPreview
@Composable
private fun SearchResultRowPreview() {
    MkDocsEditorTheme {
        SearchResultRow(
            searchTerm = "haft",
            item = SearchResultItem.Document(
                documentId = "1",
                documentName = "Versicherungen",
                excerpts = listOf(
                    SearchResultItem.Document.ExcerptData(
                        text = "Die Haftpflicht kostet 50 € im Jahr und läuft noch bis",
                        matches = listOf(4..7),
                        heading = "Beiträge",
                        truncatedEnd = true,
                    ),
                ),
                path = listOf("Allgemeinwissen", "Haushalt"),
                modtime = Date(),
                matchCount = 4,
            ),
            onItemClicked = {},
            onItemLongClicked = {},
        )
    }
}
