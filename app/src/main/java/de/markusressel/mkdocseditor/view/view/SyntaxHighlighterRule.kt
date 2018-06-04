package de.markusressel.mkdocseditor.view.view

import android.text.Editable

interface SyntaxHighlighterRule {

    /**
     * Apply this rule to the given editable
     */
    fun apply(editable: Editable)

    /**
     * Clear any modifications this rule may have made to a given editable
     */
    fun clear(editable: Editable)

}