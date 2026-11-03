package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.core.rule.TokenType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MkDocsMarkdownRuleBookTest {

    private fun LanguageRule.matched(text: String): List<String> =
        findMatches(text).map { text.substring(it.startIndex, it.endIndex) }

    @Test
    fun headings() {
        assertEquals(listOf("# Title", "### Sub"), MarkdownRules.Heading.matched("# Title\ntext\n### Sub\n#nope"))
    }

    @Test
    fun `bold and italic with stars and underscores`() {
        val text = "**bold** and __bold__ and *it* and _it_ but snake_case_name and 2 * 3 * 4"
        assertEquals(listOf("**bold**", "__bold__"), MarkdownRules.Bold.matched(text))
        assertEquals(listOf("*it*", "_it_"), MarkdownRules.Italic.matched(text))
    }

    @Test
    fun `a list marker is not italic`() {
        assertEquals(emptyList(), MarkdownRules.Italic.matched("* item one\n* item *two"))
        assertEquals(listOf("*", "*", "1.", "-"), MarkdownRules.ListMarker.matched("* a\n  * b\n1. c\n    - d"))
    }

    @Test
    fun `links images and urls`() {
        val text = "[a](b.md) ![img](x.png) <https://example.com> see https://x.org/p."
        assertEquals(listOf("[a]", "![img]"), MarkdownRules.LinkText.matched(text))
        assertEquals(
            listOf("(b.md)", "(x.png)", "<https://example.com>", "https://x.org/p"),
            MarkdownRules.Url.matched(text),
        )
    }

    @Test
    fun `quotes html admonitions task boxes`() {
        assertEquals(listOf("> quote"), MarkdownRules.Quote.matched("> quote\nnot"))
        assertEquals(listOf(">", ">", ">"), MarkdownRules.QuoteMarker.matched("> a\n> > b\nc > d"))
        val html = "<b>x</b> <!-- c --> a < b <img src=\"a.png\" width=100 hidden>"
        assertEquals(listOf("<b>", "</b>", "<img src=\"a.png\" width=100 hidden>"), MarkdownRules.HtmlTag.matched(html))
        assertEquals(listOf("src", "width", "hidden"), MarkdownRules.HtmlAttribute.matched(html))
        assertEquals(listOf("\"a.png\"", "100"), MarkdownRules.HtmlAttributeValue.matched(html))
        assertEquals(listOf("<!-- c -->"), MarkdownRules.HtmlComment.matched(html))
        assertEquals(
            listOf("!!! note \"Title\"", "???+ tip"),
            MarkdownRules.Admonition.matched("!!! note \"Title\"\n    body\n???+ tip\n!!!"),
        )
        assertEquals(listOf("[ ]", "[x]"), MarkdownRules.TaskBox.matched("- [ ] open\n- [x] done\n[ ] no"))
    }

    @Test
    fun `strikethrough horizontal rule front matter`() {
        assertEquals(listOf("~~gone~~"), MarkdownRules.Strikethrough.matched("~~gone~~ ~~ no ~~"))
        assertEquals(listOf("---", "* * *"), MarkdownRules.HorizontalRule.matched("---\ntext\n* * *"))
        assertEquals(listOf("---\ntitle: x\n---"), MarkdownRules.FrontMatter.matched("---\ntitle: x\n---\n# H"))
    }

    @Test
    fun `inline code`() {
        assertEquals(listOf("`a`", "``b ` c``"), MarkdownRules.InlineCode.matched("`a` and ``b ` c``"))
    }

    @Test
    fun `fenced code blocks, indented and with tildes`() {
        val text = "a\n```python\n# not a heading\n```\nb\n    ~~~\n    x\n    ~~~\nc"
        assertEquals(
            listOf("```python\n# not a heading\n```", "    ~~~\n    x\n    ~~~"),
            MarkdownRules.FencedCode.matched(text),
        )
    }

    @Test
    fun `a fence closes only with a fence of the same kind that is long enough`() {
        val text = "````\n```\n~~~\n````\nafter"
        assertEquals(listOf("````\n```\n~~~\n````"), MarkdownRules.FencedCode.matched(text))
    }

    @Test
    fun `fence lines are the opening and closing lines`() {
        assertEquals(
            listOf("```python", "```", "~~~"),
            MarkdownRules.FenceLine.matched("```python\nx\n```\n~~~\nopen"),
        )
    }

    @Test
    fun `an unclosed fence runs to the end`() {
        assertEquals(listOf("```\ncode\nmore"), MarkdownRules.FencedCode.matched("text\n```\ncode\nmore"))
    }

    @Test
    fun `inline backticks are not a fence`() {
        assertEquals(emptyList(), MarkdownRules.FencedCode.matched("```inline``` text"))
    }

    @Test
    fun `code comes last so it wins over markdown inside it`() {
        val rules = MkDocsMarkdownRuleBook().getRules()
        assertEquals(MarkdownRules.FencedCode, rules.last())
        assertTrue(rules.indexOf(MarkdownRules.InlineCode) > rules.indexOf(MarkdownRules.Bold))
    }

    private fun highlighted(text: String): List<Pair<LanguageRule, String>> = runBlocking {
        MkDocsMarkdownRuleBook().createHighlighting(text).flatMap { found ->
            found.matches.map { found.rule to text.substring(it.startIndex, it.endIndex) }
        }
    }

    private fun List<Pair<LanguageRule, String>>.of(rule: LanguageRule) = filter { it.first === rule }.map { it.second }

    @Test
    fun `nothing matches inside of code and comments`() {
        val found = highlighted("`**a**` <!-- **b** *c* --> **d**\n```\n**e** # f\n```\n")
        assertEquals(listOf("**d**"), found.of(MarkdownRules.Bold))
        assertEquals(emptyList(), found.of(MarkdownRules.Italic))
        assertEquals(emptyList(), found.of(MarkdownRules.Heading))
    }

    @Test
    fun `bold around code stays bold`() {
        val found = highlighted("**a `b` c**")
        assertEquals(listOf("**a `b` c**"), found.of(MarkdownRules.Bold))
        assertEquals(listOf("`b`"), found.of(MarkdownRules.InlineCode))
    }

    @Test
    fun `the fence lines are reported after the code block`() {
        val found = highlighted("```\nx\n```\n")
        assertEquals(listOf("```", "```"), found.of(MarkdownRules.FenceLine))
        assertTrue(found.indexOfFirst { it.first === MarkdownRules.FenceLine } > found.indexOfFirst { it.first === MarkdownRules.FencedCode })
    }

    @Test
    fun `a code block is highlighted in its language`() {
        val text = "text\n```python title=\"x\"\n# note\nprint(\"a\")\n```\n"
        val found = highlighted(text)
        val comment = found.single { it.first.tokenType == TokenType.COMMENT }
        assertEquals("# note", comment.second)
        assertEquals("\"a\"", found.single { it.first.tokenType == TokenType.STRING }.second)
    }

    @Test
    fun `an indented code block is highlighted too`() {
        val found = highlighted("- item\n\n    ```yaml\n    key: value\n    ```\n")
        assertEquals(1, found.count { it.first.tokenType == TokenType.PROPERTY })
    }

    @Test
    fun `a code block without a known language stays plain`() {
        listOf("```\n# x\n```", "```brainfuck\n# x\n```").forEach { text ->
            assertTrue(highlighted(text).none { it.first.tokenType != null && it.first !== MarkdownRules.FencedCode }, text)
        }
    }

    @Test
    fun `an unclosed code block is highlighted while it is being typed`() {
        assertEquals("# x", highlighted("```python\n# x").single { it.first.tokenType == TokenType.COMMENT }.second)
    }

    @Test
    fun `the front matter is highlighted as yaml`() {
        val found = highlighted("---\ntitle: x\n---\n# H\n")
        assertEquals(listOf("---\ntitle: x\n---"), found.of(MarkdownRules.FrontMatter))
        assertEquals(1, found.count { it.first.tokenType == TokenType.PROPERTY })
        assertEquals(listOf("# H"), found.of(MarkdownRules.Heading))
    }

    @Test
    fun `the language is found in all the ways of writing it`() {
        listOf("python", "PYTHON", "py", "python title=\"x\"", "{.python}", "{ .python .annotate }").forEach {
            assertTrue(FenceLanguages.ruleBookFor(it) != null, it)
        }
        listOf("", "{ .annotate", "unknown").forEach { assertEquals(null, FenceLanguages.ruleBookFor(it)) }
    }

    @Test
    fun `long input does not overflow the stack`() {
        val inputs = listOf(
            "-".repeat(200_000),
            "**a ".repeat(50_000),
            "*a ".repeat(50_000),
            "`".repeat(200_000),
            "```\n" + "line\n".repeat(50_000),
            "<!-- " + "x\n".repeat(50_000),
            "---\n" + "key: value\n".repeat(20_000),
            " ".repeat(200_000),
        )
        inputs.forEach { highlighted(it) }
    }

    @Test
    fun `every token type has a style in the color scheme`() {
        val scheme = EditorColorScheme(EditorPalette.Dark)
        TokenType.entries.forEach { type ->
            val rule = object : LanguageRule {
                override val tokenType = type
                override fun findMatches(text: CharSequence) = emptyList<RuleMatch>()
            }
            assertTrue(scheme.getStyles(rule).isNotEmpty(), "no style for $type")
        }
    }

    @Test
    fun `every rule has a style in the color scheme`() {
        val scheme = EditorColorScheme(EditorPalette.Dark)
        (MkDocsMarkdownRuleBook().getRules() + MarkdownRules.FenceLine).forEach {
            assertTrue(scheme.getStyles(it).isNotEmpty(), "no style for $it")
        }
    }
}
