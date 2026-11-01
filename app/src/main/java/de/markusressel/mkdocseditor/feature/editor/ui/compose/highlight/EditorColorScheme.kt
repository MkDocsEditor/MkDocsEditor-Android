package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

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
            MarkdownRules.ListMarker to SpanStyle(color = heading, fontWeight = FontWeight.Bold),
            MarkdownRules.HorizontalRule to SpanStyle(color = heading),
            MarkdownRules.Quote to SpanStyle(color = quote, fontStyle = FontStyle.Italic),
            MarkdownRules.Italic to SpanStyle(color = emphasis, fontStyle = FontStyle.Italic),
            MarkdownRules.Bold to SpanStyle(color = emphasis, fontWeight = FontWeight.Bold),
            MarkdownRules.Strikethrough to SpanStyle(color = quote, textDecoration = TextDecoration.LineThrough),
            MarkdownRules.Html to SpanStyle(color = html),
            MarkdownRules.Admonition to SpanStyle(color = meta, fontWeight = FontWeight.Bold),
            MarkdownRules.TaskBox to SpanStyle(color = meta, fontWeight = FontWeight.Bold),
            MarkdownRules.FrontMatter to SpanStyle(color = meta),
            MarkdownRules.Link to SpanStyle(color = link, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
            MarkdownRules.Image to SpanStyle(color = image, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
            // code resets everything else, markdown inside code is plain text
            MarkdownRules.InlineCode to codeStyle(),
            MarkdownRules.FencedCode to codeStyle(),
        )
    }

    private fun codeStyle() = SpanStyle(
        color = palette.code,
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
