package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFormattingTest {

    /** applies [action] to [text], where `|` marks the cursor or `[` `]` mark the selection */
    private fun format(action: FormatAction, text: String): String {
        val (plain, start, end) = parse(text)
        val edit = MarkdownFormatting.apply(action, plain, start, end)
        return render(edit)
    }

    private fun parse(marked: String): Triple<String, Int, Int> {
        return if (marked.contains('|')) {
            val i = marked.indexOf('|')
            Triple(marked.removeRange(i, i + 1), i, i)
        } else {
            val s = marked.indexOf('[')
            val e = marked.indexOf(']') - 1
            Triple(marked.removeRange(s, s + 1).let { it.removeRange(e, e + 1) }, s, e)
        }
    }

    private fun render(edit: FormatEdit): String = if (edit.selectionStart == edit.selectionEnd) {
        edit.text.substring(0, edit.selectionStart) + "|" + edit.text.substring(edit.selectionStart)
    } else {
        edit.text.substring(0, edit.selectionStart) + "[" +
            edit.text.substring(edit.selectionStart, edit.selectionEnd) + "]" + edit.text.substring(edit.selectionEnd)
    }

    @Test
    fun `inline code toggles`() {
        assertEquals("a `[b]` c", format(FormatAction.InlineCode, "a [b] c"))
        assertEquals("a [b] c", format(FormatAction.InlineCode, "a `[b]` c"))
        assertEquals("`|`", format(FormatAction.InlineCode, "|"))
    }

    @Test
    fun `bold italic strikethrough keep working through apply`() {
        assertEquals("**[x]**", format(FormatAction.Bold, "[x]"))
        assertEquals("*[x]*", format(FormatAction.Italic, "[x]"))
        assertEquals("~~[x]~~", format(FormatAction.Strikethrough, "[x]"))
    }

    @Test
    fun `heading sets, changes and removes the level`() {
        assertEquals("## Ti|tle", format(FormatAction.Heading(2), "Ti|tle"))
        assertEquals("### Ti|tle", format(FormatAction.Heading(3), "# Ti|tle"))
        assertEquals("Ti|tle", format(FormatAction.Heading(2), "## Ti|tle"))
        assertEquals("a\n# b|\nc", format(FormatAction.Heading(1), "a\nb|\nc"))
    }

    @Test
    fun `bullet list on several lines and back`() {
        assertEquals("- [a\n- b]", format(FormatAction.BulletList, "[a\nb]"))
        assertEquals("[a\nb]", format(FormatAction.BulletList, "- [a\n- b]"))
    }

    @Test
    fun `numbered list counts and replaces bullets`() {
        assertEquals("1. [a\n2. b\n3. c]", format(FormatAction.NumberedList, "- [a\n- b\nc]"))
        assertEquals("[a\nb]", format(FormatAction.NumberedList, "1. [a\n2. b]"))
    }

    @Test
    fun `task list keeps the indent`() {
        assertEquals("    - [ ] it|em", format(FormatAction.TaskList, "    - it|em"))
        assertEquals("    it|em", format(FormatAction.TaskList, "    - [x] it|em"))
    }

    @Test
    fun `bullet list does not count task items as bullets`() {
        assertEquals("- it|em", format(FormatAction.BulletList, "- [ ] it|em"))
    }

    @Test
    fun `quote skips blank lines between selected lines`() {
        assertEquals("> [a\n\n> b]", format(FormatAction.Quote, "[a\n\nb]"))
        assertEquals("[a\n\nb]", format(FormatAction.Quote, "> [a\n\n> b]"))
    }

    @Test
    fun `quote on an empty line`() {
        assertEquals("> |", format(FormatAction.Quote, "|"))
    }

    @Test
    fun `a selection ending at a line start does not include that line`() {
        assertEquals("- [a\n]b", format(FormatAction.BulletList, "[a\n]b"))
    }

    @Test
    fun `code block wraps the selection on own lines`() {
        assertEquals("text\n\n```\n[code]\n```\n\nmore", format(FormatAction.CodeBlock, "text [code] more"))
        assertEquals("```\n|\n```\n", format(FormatAction.CodeBlock, "|"))
    }

    @Test
    fun `table and horizontal rule are blocks`() {
        assertEquals(
            "a\n\n| [Column 1] | Column 2 |\n| -------- | -------- |\n| Cell     | Cell     |\n",
            format(FormatAction.Table, "a\n|"),
        )
        assertEquals("a\n\n---|\n\nb", format(FormatAction.HorizontalRule, "a|\n\nb"))
    }

    @Test
    fun `admonition indents the selection`() {
        assertEquals("!!! warning\n    |\n", format(FormatAction.Admonition("warning"), "|"))
        assertEquals("!!! note\n[    a\n\n    b]\n", format(FormatAction.Admonition("note"), "[a\n\nb]"))
    }

    @Test
    fun `link uses the selection as text and selects the url`() {
        assertEquals("see [docs]([url])", format(FormatAction.Link, "see [docs]"))
        assertEquals("[|](https://x.org)", format(FormatAction.Link, "[https://x.org]"))
        assertEquals("![alt]([url])", format(FormatAction.Image, "[alt]"))
    }

    @Test
    fun `insertLink puts a picked target in and escapes it`() {
        val edit = MarkdownFormatting.insertLink("see here", 4, 8, "../My Page (1).md", "fallback", image = false)
        assertEquals("see [here](../My%20Page%20%281%29.md)", edit.text)
        assertEquals(edit.text.length, edit.selectionStart)

        val image = MarkdownFormatting.insertLink("", 0, 0, "img/a.png", "a.png", image = true)
        assertEquals("![a.png](img/a.png)", image.text)
    }

    @Test
    fun `a reversed selection is handled`() {
        val edit = MarkdownFormatting.apply(FormatAction.Bold, "abc", 3, 0)
        assertEquals("**abc**", edit.text)
    }

    @Test
    fun `replace the selection with a picked link`() {
        assertEquals(FormatEdit("see [x](a.md) now", 13), MarkdownFormatting.replaceSelection("see it now", 4, 6, "[x](a.md)"))
        assertEquals(FormatEdit("[x](a.md)", 9), MarkdownFormatting.replaceSelection("", 0, 0, "[x](a.md)"))
    }

    @Test
    fun `uploaded files go on lines of their own`() {
        assertEquals(
            FormatEdit("text\n![a](a.png)\n![b](b.png)\nmore", 29),
            MarkdownFormatting.insertSnippets("textmore", 4, listOf("![a](a.png)", "![b](b.png)")),
        )
        assertEquals(FormatEdit("![a](a.png)", 11), MarkdownFormatting.insertSnippets("", 0, listOf("![a](a.png)")))
        assertEquals(FormatEdit("x\n![a](a.png)\n", 13), MarkdownFormatting.insertSnippets("x\n\n", 2, listOf("![a](a.png)")))
    }
}
