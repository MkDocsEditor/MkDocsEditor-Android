package de.markusressel.mkdocseditor.view.view.rule

import android.graphics.Color
import android.text.Editable
import android.text.style.ForegroundColorSpan

class HeadingRule : HighlighterRuleBase() {

    override val styles = setOf({ ForegroundColorSpan(COLOR) })

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "#{1,6} .*"
                .toRegex()
        val COLOR = Color
                .parseColor("#FF6D00")
    }

}