package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** the same cases as `src/markdown/headings.spec.ts` of the web client */
class HeadingsTest {

    @Test
    fun slugify() {
        mapOf(
            "Hello World" to "hello-world",
            "Äpfel & Birnen" to "apfel-birnen",
            "  Step 2: install  " to "step-2-install",
            "C++ / Rust" to "c-rust",
            "日本語" to "",
        ).forEach { (text, slug) -> assertEquals(slug, Headings.slugify(text), text) }
    }

    @Test
    fun `finds headings with unique anchors and skips code`() {
        val text = listOf(
            "---",
            "# not: a heading",
            "---",
            "# Title",
            "",
            "## Use `code` and **bold** ##",
            "```",
            "# comment in code",
            "```",
            "## Title",
            "### Custom { #my-id }",
            "#hashtag",
        ).joinToString("\n")
        assertEquals(
            listOf(
                Heading(1, "Title", "title", 4),
                Heading(2, "Use code and bold", "use-code-and-bold", 6),
                Heading(2, "Title", "title_1", 10),
                Heading(3, "Custom", "my-id", 11),
            ),
            Headings.of(text),
        )
    }

    @Test
    fun `links in headings become their text`() {
        assertEquals(listOf(Heading(2, "See docs", "see-docs", 1)), Headings.of("## See [docs](a.md)"))
    }
}
