package de.markusressel.mkdocseditor.view.view

import android.text.Editable


interface SyntaxHighlighter {

    /**
     * Get a set of rules for this highlighter
     */
    fun getRules(): Set<SyntaxHighlighterRule>

    /**
     * Highlight the given text
     */
    fun highlight(editable: Editable) {
        // cleanup
        getRules()
                .forEach {
                    it
                            .clear(editable)
                }

        // reapply
        getRules()
                .forEach {
                    it
                            .apply(editable)
                }
    }

}