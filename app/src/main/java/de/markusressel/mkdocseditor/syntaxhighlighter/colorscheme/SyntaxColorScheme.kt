package de.markusressel.mkdocseditor.syntaxhighlighter.colorscheme

import android.text.style.CharacterStyle
import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighter

/**
 * A color scheme for a syntax highlighter
 */
interface SyntaxColorScheme<Language : SyntaxHighlighter> {

    /**
     * Get a set of styles to apply for a specific text/section type
     */
    fun getStyle(type: SectionTypeEnum): Set<CharacterStyle>

    /**
     * Returns a collection of all style types used in this color scheme
     */
    fun getStyleTypes(): Set<Class<CharacterStyle>> {
        return SectionTypeEnum
                .values()
                .map {
                    getStyle(it)
                }
                .flatten()
                .map {
                    it
                            .javaClass
                }
                .toSet()
    }

}
