package de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule

import android.graphics.Color
import android.text.style.ForegroundColorSpan

abstract class CodeRule : HighlighterRuleBase() {

    override val styles = setOf({ ForegroundColorSpan(COLOR) })

    companion object {
        val COLOR = Color
                .parseColor("#00C853")
    }


}