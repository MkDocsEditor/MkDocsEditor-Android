package de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule

import android.text.Editable
import de.markusressel.mkdocseditor.syntaxhighlighter.colorscheme.SectionTypeEnum

class TextLinkRule : HighlighterRuleBase() {

    override fun getSectionType(): SectionTypeEnum {
        return SectionTypeEnum
                .Link
    }

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(?<!!)\\[.*?]\\(.*?\\)"
                .toRegex()
    }

}