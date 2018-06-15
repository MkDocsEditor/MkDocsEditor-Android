package de.markusressel.mkdocseditor.syntaxhighlighter

import android.text.style.CharacterStyle

abstract class SyntaxHighlighterBase : SyntaxHighlighter {
    override val appliedStyles: MutableSet<CharacterStyle> = mutableSetOf()
}