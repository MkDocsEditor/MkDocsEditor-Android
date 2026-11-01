package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule

/**
 * Maps the rules of [MkDocsMarkdownRuleBook] to the colors of an [EditorPalette]
 */
class EditorColorScheme(private val palette: EditorPalette) : ColorScheme<SpanStyle> {

    private val styles: Map<LanguageRule, SpanStyle> = with(palette) {
        mapOf(
            MarkdownRules.Heading to SpanStyle(color = heading, fontWeight = FontWeight.Bold),
            MarkdownRules.ListMarker to SpanStyle(color = keyword, fontWeight = FontWeight.Bold),
            MarkdownRules.HorizontalRule to SpanStyle(color = comment, fontWeight = FontWeight.Bold),
            MarkdownRules.Quote to SpanStyle(color = comment, fontStyle = FontStyle.Italic),
            MarkdownRules.QuoteMarker to SpanStyle(color = keyword, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal),
            MarkdownRules.Italic to SpanStyle(fontStyle = FontStyle.Italic),
            MarkdownRules.Bold to SpanStyle(fontWeight = FontWeight.Bold),
            MarkdownRules.Strikethrough to SpanStyle(textDecoration = TextDecoration.LineThrough),
            MarkdownRules.HtmlTag to SpanStyle(color = tag),
            MarkdownRules.HtmlAttribute to SpanStyle(color = attribute),
            MarkdownRules.HtmlAttributeValue to SpanStyle(color = string),
            MarkdownRules.HtmlComment to SpanStyle(color = comment, fontStyle = FontStyle.Italic),
            MarkdownRules.Admonition to SpanStyle(color = annotation, fontWeight = FontWeight.Bold),
            MarkdownRules.TaskBox to SpanStyle(color = keyword, fontWeight = FontWeight.Bold),
            MarkdownRules.FrontMatter to SpanStyle(color = annotation),
            MarkdownRules.LinkText to SpanStyle(color = link),
            MarkdownRules.Url to SpanStyle(color = url, textDecoration = TextDecoration.Underline),
            // code resets everything else, markdown inside code is plain text
            MarkdownRules.InlineCode to codeStyle(string),
            MarkdownRules.FencedCode to codeStyle(text),
            MarkdownRules.FenceLine to SpanStyle(color = comment),
        )
    }

    private fun codeStyle(color: Color) = SpanStyle(
        color = color,
        background = palette.codeBackground,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        textDecoration = TextDecoration.None,
    )

    override fun getStyles(type: LanguageRule): Set<StyleFactory<SpanStyle>> {
        val style = styles[type] ?: return emptySet()
        return setOf { style }
    }
}
