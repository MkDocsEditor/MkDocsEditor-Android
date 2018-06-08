package de.markusressel.mkdocseditor.syntaxhighlighter

import android.text.Editable
import android.text.style.CharacterStyle

interface SyntaxHighlighterRule {

    /**
     * Apply this rule to the given editable
     */
    fun apply(editable: Editable)

    /**
     * Returns a collection of all style types used by this rule
     */
    fun getStyleTypes(): Collection<CharacterStyle>

    /**
     * Clear any modifications this rule may have made to a given editable
     * Note that this might also clear styles of other rules
     */
    fun clear(editable: Editable)

}