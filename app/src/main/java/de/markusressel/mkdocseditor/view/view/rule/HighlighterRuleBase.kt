package de.markusressel.mkdocseditor.view.view.rule

import android.text.Editable
import android.text.Spannable
import android.text.style.CharacterStyle
import de.markusressel.mkdocseditor.view.view.SyntaxHighlighterRule

abstract class HighlighterRuleBase : SyntaxHighlighterRule {

    /**
     * Set of styles to apply to matching text blocks
     */
    abstract val styles: Set<() -> CharacterStyle>

    /**
     * Find segments in the editable that are affected by this rule
     */
    abstract fun findMatches(editable: Editable): Sequence<MatchResult>

    /**
     * Highlight a specific part
     *
     * @param editable the editable to highlight
     * @param start the starting position
     * @param end the end position (inclusive)
     */
    private fun highlight(editable: Editable, start: Int, end: Int) {
        styles
                .forEach {
                    editable
                            .setSpan(it(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
    }

    override fun apply(editable: Editable) {
        findMatches(editable)
                .forEach {
                    val start = it
                            .range
                            .start
                    val end = it.range.endInclusive + 1

                    highlight(editable, start, end)
                }
    }

    override fun clear(editable: Editable) {
        // WARNING
        // don't use editable.clearSpans() because it will
        // remove too much and cause weird bugs like:
        // - blinking cursor wont follow
        // - typing is very slow
        // - some typed (or deleted) characters wont render at all (no redraw)
        // WARNING

        styles
                .forEach {
                    clearSpan(editable, it().javaClass)
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