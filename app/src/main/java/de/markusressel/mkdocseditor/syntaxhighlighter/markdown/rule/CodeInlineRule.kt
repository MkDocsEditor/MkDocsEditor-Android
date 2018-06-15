package de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule

import android.text.Editable
import de.markusressel.mkdocseditor.syntaxhighlighter.colorscheme.SectionTypeEnum

class CodeInlineRule : HighlighterRuleBase() {

    override fun getSectionType(): SectionTypeEnum {
        return SectionTypeEnum
                .SourceCode
    }

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(`{1,3})([^`]+?)\\1"
                .toRegex()
    }

}