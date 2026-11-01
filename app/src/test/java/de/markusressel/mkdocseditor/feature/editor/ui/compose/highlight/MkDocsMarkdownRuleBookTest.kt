package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodehighlighter.core.rule.LanguageRule
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
        assertEquals(listOf(MarkdownRules.FencedCode, MarkdownRules.FenceLine), rules.takeLast(2))
        assertTrue(rules.indexOf(MarkdownRules.InlineCode) > rules.indexOf(MarkdownRules.Bold))
    }

    @Test
    fun `every rule has a style in the color scheme`() {
        val scheme = EditorColorScheme(EditorPalette.Dark)
        MkDocsMarkdownRuleBook().getRules().forEach {
            assertTrue(scheme.getStyles(it).isNotEmpty(), "no style for $it")
        }
    }
}
