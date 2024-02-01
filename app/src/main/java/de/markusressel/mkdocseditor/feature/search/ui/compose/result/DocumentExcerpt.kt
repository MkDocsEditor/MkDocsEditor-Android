package de.markusressel.mkdocseditor.feature.search.ui.compose.result

import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.core.ui.KodeText
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

private val QuoteEllipsis = "[…]"

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

        val text = listOfNotNull(
            QuoteEllipsis.takeIf { excerpt.charsBefore > 0 },
            excerpt.excerpt,
            QuoteEllipsis.takeIf { excerpt.charsAfter > 0 },
        ).joinToString(separator = " ")

        val ruleBook by remember(searchTerm) {
            derivedStateOf { SearchHighlightingRuleBook(searchTerm) }
        }

        val searchTermBackgroundColor = MaterialTheme.colorScheme.primary
        val searchTermTextColor = MaterialTheme.colorScheme.onPrimary
        val colorScheme by remember(
            ruleBook, searchTermBackgroundColor, searchTermTextColor
        ) {
            derivedStateOf {
                SearchHighlightingColorScheme(
                    backgroundColor = searchTermBackgroundColor,
                    textColor = searchTermTextColor
                )
            }
        }

        KodeText(
            text = text,
            languageRuleBook = ruleBook,
            colorScheme = colorScheme,
            textColor = MaterialTheme.colorScheme.onBackground,
            // TODO: add support for text style
//                        textStyle = TextStyle(
//                            fontFamily = FontFamily.Monospace,
//                        ),
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
                excerpt = "This is a test excerpt",
                charsBefore = 20,
                charsAfter = 50
            )
        )
    }
}


private class SearchHighlightingColorScheme(
    @ColorInt val color: Color = Color(0xFF0091EA),
    val backgroundColor: Color,
    val textColor: Color
) :
    ColorScheme<SpanStyle> {

    val markdownColorScheme = DarkBackgroundColorSchemeWithSpanStyle()

    override fun getStyles(type: LanguageRule): Set<StyleFactory<SpanStyle>> {
        return when (type) {
            is SearchTermRule -> {
                setOf {
                    SpanStyle(
                        background = backgroundColor,
                        color = textColor
                    )
                }
            }

            is QuoteEllipsisRule -> {
                setOf {
                    SpanStyle(
                        color = Color.Gray
                    )
                }
            }

            else -> markdownColorScheme.getStyles(type)
        }
    }
}

private class SearchTermRule(val searchTerm: String) : LanguageRule {
    override fun findMatches(text: CharSequence): List<RuleMatch> {
        return RuleHelper.findRegexMatches(
            text,
            searchTerm.toRegex(setOf(RegexOption.LITERAL, RegexOption.IGNORE_CASE))
        )
    }
}

private class SearchHighlightingRuleBook(searchTerm: String) : LanguageRuleBook {

    private val markdownRules = MarkdownRuleBook().getRules()

    private val quoteEllipsisRule = QuoteEllipsisRule()
    private val highlightingRule = SearchTermRule(searchTerm)

    override fun getRules(): List<LanguageRule> {
        return listOf(quoteEllipsisRule, highlightingRule) + markdownRules
    }
}

private class QuoteEllipsisRule : LanguageRule {
    override fun findMatches(text: CharSequence): List<RuleMatch> {
        return RuleHelper.findRegexMatches(
            text,
            QuoteEllipsis.toRegex(RegexOption.LITERAL)
        )
    }
}