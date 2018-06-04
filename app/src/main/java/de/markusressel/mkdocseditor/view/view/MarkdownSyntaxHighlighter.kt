package de.markusressel.mkdocseditor.view.view

import de.markusressel.mkdocseditor.view.view.rule.*

class MarkdownSyntaxHighlighter : SyntaxHighlighter {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(HeadingRule(), ItalicRule(), BoldRule(), CodeInlineRule(), CodeLineRule(), LinkRule())
    }

}