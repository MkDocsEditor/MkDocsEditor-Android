package de.markusressel.mkdocseditor.syntaxhighlighter

import android.text.style.CharacterStyle
import de.markusressel.mkdocseditor.syntaxhighlighter.colorscheme.SyntaxColorScheme

abstract class SyntaxHighlighterBase : SyntaxHighlighter {

    override val appliedStyles: MutableSet<CharacterStyle> = mutableSetOf()

    override var colorScheme: SyntaxColorScheme = getDefaultColorScheme()

}