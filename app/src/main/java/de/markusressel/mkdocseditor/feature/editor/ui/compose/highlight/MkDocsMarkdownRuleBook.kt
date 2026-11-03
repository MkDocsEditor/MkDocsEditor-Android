package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.RuleMatches
import de.markusressel.kodehighlighter.core.region.DelimitedRegion
import de.markusressel.kodehighlighter.core.region.PatternRegion
import de.markusressel.kodehighlighter.core.region.RegionRuleBook
import de.markusressel.kodehighlighter.core.region.RegionScanner
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.language.markdown.rule.BoldRule
import de.markusressel.kodehighlighter.language.markdown.rule.HorizontalRuleRule
import de.markusressel.kodehighlighter.language.markdown.rule.ItalicRule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Highlighting rules for MkDocs flavoured markdown.
 *
 * Code, HTML comments and the front matter are regions: no other rule matches inside of them. Code blocks and the
 * front matter are highlighted in their own language on top of that. Indented code (4 spaces) is not highlighted on
 * purpose, because MkDocs uses 4 spaces for nested lists and the content of admonitions.
 */
class MkDocsMarkdownRuleBook : RegionRuleBook() {
    override val scanner: RegionScanner get() = MarkdownRules.regions

    override fun getRules(): List<LanguageRule> = rules

    override suspend fun createHighlighting(charSequence: CharSequence): List<RuleMatches> {
        val text = charSequence.toString()
        val markdown = super.createHighlighting(text)
        val blocks = markdown.firstOrNull { it.rule === MarkdownRules.FencedCode }?.matches.orEmpty()
        val frontMatter = markdown.firstOrNull { it.rule === MarkdownRules.FrontMatter }?.matches.orEmpty()
        val fenceLines = blocks.flatMap { MarkdownRules.fenceLinesOf(text, it) }

        val embedded = coroutineScope {
            val code = blocks.map { block -> async { highlightCodeBlock(text, block) } }
            val yaml = frontMatter.map { block -> async { highlightWith(FenceLanguages.frontMatter, text, block.startIndex, block.endIndex) } }
            (code + yaml).awaitAll().flatten()
        }
        // later ones win, and a color of the embedded language keeps the background of the code block
        return markdown + listOfNotNull(fenceLines.takeIf { it.isNotEmpty() }?.let { RuleMatches(MarkdownRules.FenceLine, it) }) + embedded
    }

    private suspend fun highlightCodeBlock(text: String, block: RuleMatch): List<RuleMatches> {
        val code = MarkdownRules.codeOf(text, block)
        val book = FenceLanguages.ruleBookFor(code.infoString) ?: return emptyList()
        return highlightWith(book, text, code.startIndex, code.endIndex)
    }

    private suspend fun highlightWith(book: LanguageRuleBook, text: String, start: Int, end: Int): List<RuleMatches> {
        if (start >= end) return emptyList()
        return book.createHighlighting(text.substring(start, end)).map { found ->
            RuleMatches(found.rule, found.matches.map { RuleMatch(it.startIndex + start, it.endIndex + start) })
        }
    }

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

    val HorizontalRule: LanguageRule = HorizontalRuleRule

    val Quote: LanguageRule = RegexRule("(?m)^[ \\t]*>.*$".toRegex())

    /** the `>` of a quote, also nested ones */
    val QuoteMarker: LanguageRule = InsideRule("(?m)^[ \\t]*(?:>[ \\t]?)+".toRegex(), ">".toRegex())

    val Bold: LanguageRule = BoldRule

    val Italic: LanguageRule = ItalicRule

    val Strikethrough: LanguageRule = RegexRule("~~(?!\\s)(?:[^~\\n]+?)(?<!\\s)~~".toRegex())

    private val htmlTag = "</?[A-Za-z][A-Za-z0-9-]*(?:\\s[^<>\\n]*)?/?>".toRegex()
    private val htmlAttribute = "(?<=\\s)[A-Za-z_:][\\w:.-]*(?=\\s*=|\\s|/?>)".toRegex()
    private val htmlAttributeValue = "(?<==)\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s\"'=<>`]+)".toRegex()

    /** HTML tags; the attributes inside get their own colors */
    val HtmlTag: LanguageRule = RegexRule(htmlTag)

    val HtmlAttribute: LanguageRule = InsideRule(htmlTag, htmlAttribute)

    val HtmlAttributeValue: LanguageRule = InsideRule(htmlTag, htmlAttributeValue)

    val HtmlComment: LanguageRule = RegionRule()

    /** `!!! note "Title"`, `??? tip` and `???+ warning` */
    val Admonition: LanguageRule = RegexRule("(?m)^[ \\t]*(?:!!!|\\?\\?\\?\\+?)[ \\t]+\\S.*$".toRegex())

    /** the box of a task list item: `- [ ]` and `- [x]` */
    val TaskBox: LanguageRule = RegexRule("(?m)(?<=^[ \\t]{0,32}(?:[*+-]|\\d{1,9}[.)])[ \\t])\\[[ xX]](?=[ \\t])".toRegex())

    /** a YAML front matter block at the start of the document */
    val FrontMatter: LanguageRule = RegionRule()

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

    /** a rule whose matches come from [regions] */
    private class RegionRule : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> = regions.findMatches(text, this)
    }

    val InlineCode: LanguageRule = RegionRule()

    /**
     * Fenced code blocks, opened by 3 or more backticks or tildes (also indented, inside lists
     * and admonitions) and closed by a fence of the same kind that is at least as long.
     * A block that is not closed runs to the end of the text, like in MkDocs.
     */
    val FencedCode: LanguageRule = RegionRule()

    /** the opening and closing lines of fenced code blocks */
    val FenceLine: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> =
            regions.findMatches(text, FencedCode).flatMap { fenceLinesOf(text.toString(), it) }
    }

    /** what no other rule looks into. A fence comes first, because three backticks could also be inline code. */
    internal val regions: RegionScanner by lazy {
        RegionScanner(
            listOf(
                PatternRegion(FrontMatter, "-", "\\A---\\r?\\n[\\s\\S]*?\\r?\\n(?:---|\\.\\.\\.)(?=\\r?\\n|\\z)".toRegex()),
                DelimitedRegion(HtmlComment, "<!--", "-->", multiLine = true),
                PatternRegion(FencedCode, " \t`~", fencedCode),
                PatternRegion(InlineCode, "`", "(?<!`)(`+)(?!`)[^\\n]*?(?<!`)\\1(?!`)".toRegex()),
            )
        )
    }

    private val fencedCode = (
        "(?m)^[ \\t]*+(?:" +
            // a backtick fence must not contain backticks in its info string
            "(`{3,}+)(?![^\\n]*`)[^\\n]*+\\n?[\\s\\S]*?(?:^[ \\t]*+\\1`*+[ \\t\\r]*+\$|\\z)" +
            "|(~{3,}+)[^\\n]*+\\n?[\\s\\S]*?(?:^[ \\t]*+\\2~*+[ \\t\\r]*+\$|\\z)" +
            ")"
        ).toRegex()

    /** the code of a fenced block, between its fence lines */
    internal class FencedBlockCode(val infoString: String, val startIndex: Int, val endIndex: Int)

    internal fun codeOf(text: String, block: RuleMatch): FencedBlockCode {
        val firstLineEnd = firstLineEnd(text, block)
        return FencedBlockCode(
            infoString = text.substring(block.startIndex, firstLineEnd).trim().trimStart('`', '~'),
            startIndex = (firstLineEnd + 1).coerceAtMost(block.endIndex),
            endIndex = closingLineStart(text, block) ?: block.endIndex,
        )
    }

    internal fun fenceLinesOf(text: String, block: RuleMatch): List<RuleMatch> = listOfNotNull(
        RuleMatch(block.startIndex, firstLineEnd(text, block)),
        closingLineStart(text, block)?.let { RuleMatch(it, block.endIndex) },
    )

    private fun firstLineEnd(text: String, block: RuleMatch): Int =
        text.indexOf('\n', block.startIndex).let { if (it < 0 || it > block.endIndex) block.endIndex else it }

    /** @return where the closing fence is, null if the block is not closed */
    private fun closingLineStart(text: String, block: RuleMatch): Int? {
        if (block.endIndex <= block.startIndex) return null
        val lineStart = text.lastIndexOf('\n', block.endIndex - 1).let { if (it < block.startIndex) block.startIndex else it + 1 }
        val closed = lineStart > block.startIndex && text.substring(lineStart, block.endIndex).trim().let {
            it.length >= 3 && (it.all { c -> c == '`' } || it.all { c -> c == '~' })
        }
        return lineStart.takeIf { closed }
    }
}
