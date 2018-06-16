package de.markusressel.mkdocseditor.syntaxhighlighter.markdown

import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighterBase
import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighterRule
import de.markusressel.mkdocseditor.syntaxhighlighter.colorscheme.SyntaxColorScheme
import de.markusressel.mkdocseditor.syntaxhighlighter.markdown.colorscheme.DarkBackgroundColorScheme
import de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule.*

class MarkdownSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(HeadingRule(), ItalicRule(), BoldRule(), CodeInlineRule(), CodeLineRule(), TextLinkRule(), ImageLinkRule(), StrikeRule())
    }

    override fun getDefaultColorScheme(): SyntaxColorScheme {
        return DarkBackgroundColorScheme()
    }

}