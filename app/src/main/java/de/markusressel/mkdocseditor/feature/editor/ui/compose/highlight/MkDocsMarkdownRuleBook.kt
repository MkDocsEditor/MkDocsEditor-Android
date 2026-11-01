package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch

/**
 * Highlighting rules for MkDocs flavoured markdown.
 *
 * Later rules win where matches overlap, so code comes last: markdown inside code is not
 * highlighted. Indented code (4 spaces) is not highlighted on purpose, because MkDocs uses
 * 4 spaces for nested lists and the content of admonitions.
 */
class MkDocsMarkdownRuleBook : LanguageRuleBook {
    override fun getRules(): List<LanguageRule> = rules

    private companion object {
        val rules = listOf(
            MarkdownRules.Heading,
            MarkdownRules.ListMarker,
            MarkdownRules.HorizontalRule,
            MarkdownRules.Quote,
            MarkdownRules.QuoteMarker,
            MarkdownRules.Italic,
            MarkdownRules.Bold,
            MarkdownRules.Strikethrough,
            MarkdownRules.HtmlTag,
            MarkdownRules.HtmlAttribute,
            MarkdownRules.HtmlAttributeValue,
            MarkdownRules.HtmlComment,
            MarkdownRules.Admonition,
            MarkdownRules.TaskBox,
            MarkdownRules.FrontMatter,
            MarkdownRules.LinkText,
            MarkdownRules.Url,
            MarkdownRules.InlineCode,
            MarkdownRules.FencedCode,
            MarkdownRules.FenceLine,
        )
    }
}

/**
 * The markdown rules of [MkDocsMarkdownRuleBook]
 */
object MarkdownRules {

    private class RegexRule(private vararg val patterns: Regex) : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> =
            patterns.flatMap { RuleHelper.findRegexMatches(text, it) }
    }

    val Heading: LanguageRule = RegexRule("(?m)^ {0,3}#{1,6}(?:[ \\t].*)?$".toRegex())

    val ListMarker: LanguageRule = RegexRule("(?m)(?<=^[ \\t]{0,32})(?:[*+-]|\\d{1,9}[.)])(?=[ \\t])".toRegex())

    val HorizontalRule: LanguageRule = RegexRule("(?m)^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$".toRegex())

    val Quote: LanguageRule = RegexRule("(?m)^[ \\t]*>.*$".toRegex())

    /** the `>` of a quote, also nested ones */
    val QuoteMarker: LanguageRule = InsideRule("(?m)^[ \\t]*(?:>[ \\t]?)+".toRegex(), ">".toRegex())

    val Bold: LanguageRule = RegexRule(
        "\\*\\*(?![\\s*])(?:.+?)(?<![\\s*])\\*\\*".toRegex(),
        "(?<![\\w_])__(?![\\s_])(?:.+?)(?<![\\s_])__(?![\\w_])".toRegex(),
    )

    val Italic: LanguageRule = RegexRule(
        "(?<![*\\\\])\\*(?![\\s*])(?:[^*\\n]+?)(?<![\\s*\\\\])\\*(?!\\*)".toRegex(),
        "(?<![\\w_\\\\])_(?![\\s_])(?:[^_\\n]+?)(?<![\\s_\\\\])_(?![\\w_])".toRegex(),
    )

    val Strikethrough: LanguageRule = RegexRule("~~(?!\\s)(?:[^~\\n]+?)(?<!\\s)~~".toRegex())

    private val htmlTag = "</?[A-Za-z][A-Za-z0-9-]*(?:\\s[^<>\\n]*)?/?>".toRegex()
    private val htmlAttribute = "(?<=\\s)[A-Za-z_:][\\w:.-]*(?=\\s*=|\\s|/?>)".toRegex()
    private val htmlAttributeValue = "(?<==)\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s\"'=<>`]+)".toRegex()

    /** HTML tags; the attributes inside get their own colors */
    val HtmlTag: LanguageRule = RegexRule(htmlTag)

    val HtmlAttribute: LanguageRule = InsideRule(htmlTag, htmlAttribute)

    val HtmlAttributeValue: LanguageRule = InsideRule(htmlTag, htmlAttributeValue)

    val HtmlComment: LanguageRule = RegexRule("<!--[\\s\\S]*?(?:-->|\\z)".toRegex())

    /** `!!! note "Title"`, `??? tip` and `???+ warning` */
    val Admonition: LanguageRule = RegexRule("(?m)^[ \\t]*(?:!!!|\\?\\?\\?\\+?)[ \\t]+\\S.*$".toRegex())

    /** the box of a task list item: `- [ ]` and `- [x]` */
    val TaskBox: LanguageRule = RegexRule("(?m)(?<=^[ \\t]{0,32}(?:[*+-]|\\d{1,9}[.)])[ \\t])\\[[ xX]](?=[ \\t])".toRegex())

    /** a YAML front matter block at the start of the document */
    val FrontMatter: LanguageRule = RegexRule("\\A---\\r?\\n[\\s\\S]*?\\r?\\n(?:---|\\.\\.\\.)(?=\\r?\\n|\\z)".toRegex())

    private val inlineLink = "(!?\\[[^\\]\\n]*])(\\([^)\\n]*\\))".toRegex()

    /** the text of links and images: `[text]` and `![alt]` */
    val LinkText: LanguageRule = GroupRule(inlineLink, 1)

    /** link targets `(target)`, autolinks `<https://...>` and bare URLs */
    val Url: LanguageRule = object : LanguageRule {
        private val autolink = "<(?:https?|mailto):[^>\\s]+>".toRegex()
        private val bareUrl = "(?<![(<\\w])https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,:;!?\"']".toRegex()
        override fun findMatches(text: CharSequence): List<RuleMatch> =
            GroupRule(inlineLink, 2).findMatches(text) +
                RuleHelper.findRegexMatches(text, autolink) +
                RuleHelper.findRegexMatches(text, bareUrl)
    }

    /** matches of [inner] inside the matches of [outer] */
    private class InsideRule(private val outer: Regex, private val inner: Regex) : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> = outer.findAll(text).flatMap { o ->
            inner.findAll(o.value).map { RuleMatch(o.range.first + it.range.first, o.range.first + it.range.last + 1) }
        }.toList()
    }

    /** the given group of every match of [regex] */
    private class GroupRule(private val regex: Regex, private val group: Int) : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> = regex.findAll(text).mapNotNull { match ->
            match.groups[group]?.range?.let { RuleMatch(it.first, it.last + 1) }
        }.toList()
    }

    val InlineCode: LanguageRule = RegexRule("(`+)(?!`)[^\\n]*?(?<!`)\\1(?!`)".toRegex())

    /**
     * Fenced code blocks, opened by 3 or more backticks or tildes (also indented, inside lists
     * and admonitions) and closed by a fence of the same kind that is at least as long.
     * A block that is not closed runs to the end of the text, like in MkDocs.
     */
    val FencedCode: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> = findFencedCodeBlocks(text)
    }

    /** the opening and closing lines of fenced code blocks */
    val FenceLine: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> = findFencedCodeBlocks(text).flatMap { block ->
            val firstLineEnd = text.indexOf('\n', block.startIndex).let { if (it < 0 || it > block.endIndex) block.endIndex else it }
            val lastLineStart = text.lastIndexOf('\n', block.endIndex - 1).let { if (it < block.startIndex) block.startIndex else it + 1 }
            val closed = lastLineStart > block.startIndex && text.subSequence(lastLineStart, block.endIndex).trim().let {
                it.length >= 3 && (it.all { c -> c == '`' } || it.all { c -> c == '~' })
            }
            listOfNotNull(
                RuleMatch(block.startIndex, firstLineEnd),
                RuleMatch(lastLineStart, block.endIndex).takeIf { closed },
            )
        }
    }

    private val openingFence = "^[ \\t]*(`{3,}|~{3,})".toRegex()

    internal fun findFencedCodeBlocks(text: CharSequence): List<RuleMatch> {
        val result = mutableListOf<RuleMatch>()
        var fence: String? = null
        var blockStart = 0
        var lineStart = 0
        while (lineStart <= text.length) {
            val newline = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.subSequence(lineStart, newline)
            val currentFence = fence
            if (currentFence == null) {
                val match = openingFence.find(line)
                // a backtick fence must not contain backticks in its info string
                if (match != null && !(match.groupValues[1][0] == '`' && line.drop(match.range.last + 1).contains('`'))) {
                    fence = match.groupValues[1]
                    blockStart = lineStart
                }
            } else {
                val trimmed = line.trim()
                if (trimmed.length >= currentFence.length && trimmed.all { it == currentFence[0] }) {
                    result += RuleMatch(blockStart, newline)
                    fence = null
                }
            }
            lineStart = newline + 1
        }
        if (fence != null) {
            result += RuleMatch(blockStart, text.length)
        }
        return result
    }

    private fun CharSequence.indexOf(char: Char, startIndex: Int): Int {
        for (i in startIndex until length) {
            if (this[i] == char) return i
        }
        return -1
    }

    private fun CharSequence.lastIndexOf(char: Char, startIndex: Int): Int {
        for (i in startIndex.coerceAtMost(length - 1) downTo 0) {
            if (this[i] == char) return i
        }
        return -1
    }
}
