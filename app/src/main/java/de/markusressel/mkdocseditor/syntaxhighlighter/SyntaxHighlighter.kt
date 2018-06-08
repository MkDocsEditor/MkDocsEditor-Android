package de.markusressel.mkdocseditor.syntaxhighlighter

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
                .flatMap {
                    it
                            .getStyleTypes()
                }
                .distinct()
                .forEach {
                    clearSpan(editable, it.javaClass)
                }

        // reapply
        getRules()
                .forEach {
                    it
                            .apply(editable)
                }
    }

    /**
     * Clear spans of the specified type
     */
    private fun clearSpan(editable: Editable, clazz: Class<*>) {
        editable
                .getSpans(0, editable.length, clazz)
                .forEach {
                    editable
                            .removeSpan(it)
                }
    }

}