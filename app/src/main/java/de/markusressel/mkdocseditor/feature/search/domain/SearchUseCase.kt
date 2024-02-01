package de.markusressel.mkdocseditor.feature.search.domain

import android.app.UiModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.core.util.AnnotatedStringHighlighter
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import de.markusressel.mkdocseditor.feature.theme.md_theme_dark_onPrimary
import de.markusressel.mkdocseditor.feature.theme.md_theme_dark_primary
import de.markusressel.mkdocseditor.feature.theme.md_theme_light_onPrimary
import de.markusressel.mkdocseditor.feature.theme.md_theme_light_primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
internal class SearchUseCase @Inject constructor(
    private val dataRepository: DataRepository,
    private val uiModeManager: UiModeManager,
) {
    suspend operator fun invoke(searchTerm: String): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            dataRepository.find(searchTerm).map { item ->
                when (item) {
                    is DocumentData -> {
                        val excerpts =
                            item.getInlineExcerpts(searchTerm).filter { it.excerpt.isNotBlank() }
                        SearchResultItem.Document(
                            documentId = item.id,
                            documentName = item.name,
                            excerpts = excerpts
                        )
                    }

                    is SectionData -> SearchResultItem.Section(
                        sectionId = item.id,
                        sectionName = item.name,
                    )

                    is ResourceData -> SearchResultItem.Resource(
                        resourceId = item.id,
                        resourceName = item.name,
                    )

                    else -> {
                        throw IllegalArgumentException("Unknown type: ${item::class.java}")
                    }
                }
            }
        }

    private suspend fun DocumentData.getInlineExcerpts(
        searchTerm: String,
        charsBeforeMatch: Int = 50,
        charsAfterMatch: Int = 50
    ): List<SearchResultItem.Document.ExcerptData> {
        val content = content?.text ?: ""

        val languageRuleBook = SearchHighlightingRuleBook(searchTerm)

        val mode = uiModeManager.nightMode
        val searchTermBackgroundColor = when (mode) {
            UiModeManager.MODE_NIGHT_YES -> md_theme_dark_primary
            else -> md_theme_light_primary
        }
        val searchTermTextColor = when (mode) {
            UiModeManager.MODE_NIGHT_YES -> md_theme_dark_onPrimary
            else -> md_theme_light_onPrimary
        }

        val colorScheme = SearchHighlightingColorScheme(
            backgroundColor = searchTermBackgroundColor,
            textColor = searchTermTextColor,
        )
        val highlighter = AnnotatedStringHighlighter(
            languageRuleBook = languageRuleBook,
            colorScheme = colorScheme,
        )

        val annotatedContent = highlighter.highlight(content)

        val matches =
            searchTerm.toRegex(setOf(RegexOption.LITERAL, RegexOption.IGNORE_CASE)).findAll(content)

        // TODO: skip match if it is already part of a previous excerpt
        //  or alternatively merge excerpts that are close to each other

        return matches.map { match ->
            val (startIndex, endIndex) = match.range.first to match.range.last

            val excerptStartIndex =
                content.goBack(from = startIndex, chars = charsBeforeMatch, until = '\n')
            val excerptEndIndex =
                (endIndex + charsAfterMatch).coerceIn(0, (content.length - 1).coerceAtLeast(0))
            val charsAfterExcerptEntIndex =
                ((content.length - 1).coerceAtLeast(0) - excerptEndIndex).coerceAtLeast(0)

            val charBeforeMatchIsNewline = content.getOrNull(excerptStartIndex - 1) == '\n'
            val charAfterMatchIsNewline = content.getOrNull(excerptEndIndex + 1) == '\n'

            SearchResultItem.Document.ExcerptData(
                excerpt = annotatedContent.subSequence(excerptStartIndex, excerptEndIndex),
                charsBefore = excerptStartIndex,
                charsAfter = charsAfterExcerptEntIndex,
                charBeforeExcerptIsNewline = charBeforeMatchIsNewline,
                charAfterExcerptIsNewline = charAfterMatchIsNewline,
            )
        }.toList()
    }


    private class SearchHighlightingColorScheme(
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

    companion object {
        internal const val QuoteEllipsis = "[…]"
    }
}

/**
 * Go back in a string until a certain character is found or a certain number of characters is reached
 *
 * @param from the index to start from
 * @param chars the number of characters to go back at most
 * @param until the character to stop at, regardless of whether [chars] is reached
 */
private fun String.goBack(from: Int, chars: Int, until: Char): Int {
    var currentIndex = from
    var charsLeft = chars
    while (charsLeft > 0 && currentIndex > 0) {
        if (this[currentIndex] == until) {
            return currentIndex + 1
        }
        currentIndex--
        charsLeft--
    }
    return currentIndex
}
