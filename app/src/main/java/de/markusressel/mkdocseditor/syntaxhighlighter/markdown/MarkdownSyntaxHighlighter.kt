package de.markusressel.mkdocseditor.syntaxhighlighter.markdown

import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighter
import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighterRule
import de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule.*

class MarkdownSyntaxHighlighter : SyntaxHighlighter {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(HeadingRule(), ItalicRule(), BoldRule(), CodeInlineRule(), CodeLineRule(), LinkRule(), ImageLinkRule())
    }

}