package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodeeditor.library.compose.FoldRegion
import kotlin.test.Test
import kotlin.test.assertEquals

class MkDocsFoldProviderTest {

    private fun regionsOf(vararg lines: String) =
        MkDocsFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }

    @Test
    fun `a section runs from its heading to the next heading of the same or a higher level`() {
        val regions = regionsOf(
            "# Title",
            "intro",
            "## One",
            "text",
            "### Deeper",
            "more",
            "",
            "## Two",
            "text",
            "# Next",
            "end",
        )

        assertEquals(
            listOf(
                FoldRegion(0, 8),
                FoldRegion(2, 5),
                FoldRegion(4, 5),
                FoldRegion(7, 8),
                FoldRegion(9, 10),
            ),
            regions,
        )
    }

    @Test
    fun `the front matter folds, and a comment in it is no heading`() {
        val regions = regionsOf(
            "---",
            "title: Page",
            "# a comment of the YAML",
            "tags: [a]",
            "---",
            "# Page",
            "text",
        )

        assertEquals(listOf(FoldRegion(0, 4), FoldRegion(5, 6)), regions)
    }

    @Test
    fun `front matter that ends the text, or is not closed, still keeps the lines in place`() {
        assertEquals(listOf(FoldRegion(0, 2)), regionsOf("---", "title: Page", "..."))
        // without a closing line it is no front matter but a thematic break
        assertEquals(listOf(FoldRegion(2, 3)), regionsOf("---", "text", "# Heading", "text"))
    }

    @Test
    fun `a fenced code block folds, and the lines in it are no headings`() {
        val regions = regionsOf(
            "# Code",
            "```python",
            "# a comment",
            "print(1)",
            "```",
            "~~~~",
            "x",
            "~~~",
            "~~~~",
            "after",
        )

        assertEquals(listOf(FoldRegion(0, 9), FoldRegion(1, 4), FoldRegion(5, 8)), regions)
    }

    @Test
    fun `a fence with nothing in it or that is not closed folds nothing`() {
        assertEquals(emptyList(), regionsOf("```", "```", "text"))
        assertEquals(emptyList(), regionsOf("```kotlin", "val a = 1", "val b = 2"))
    }

    @Test
    fun `an admonition folds its indented body, with blank lines and nested blocks in it`() {
        val regions = regionsOf(
            "!!! note \"Title\"",
            "    first",
            "",
            "    ??? tip",
            "        inner",
            "    ```kotlin",
            "    val a = 1",
            "    ```",
            "",
            "after",
            "???+ warning",
            "\tbody",
        )

        assertEquals(
            listOf(FoldRegion(0, 7), FoldRegion(3, 4), FoldRegion(5, 7), FoldRegion(10, 11)),
            regions,
        )
    }

    @Test
    fun `an admonition without a body or in code folds nothing`() {
        assertEquals(emptyList(), regionsOf("!!! note", "not indented"))
        assertEquals(listOf(FoldRegion(0, 3)), regionsOf("```", "!!! note", "    body", "```"))
    }

    @Test
    fun `a fence in an admonition ends with it`() {
        val regions = regionsOf(
            "!!! note",
            "    ```",
            "    code",
            "after",
            "```",
            "x",
            "```",
        )

        assertEquals(listOf(FoldRegion(0, 2), FoldRegion(4, 6)), regions)
    }

    @Test
    fun `lines that end in a carriage return fold the same`() {
        val regions = MkDocsFoldProvider.foldRegions("---\r\ntitle: a\r\n---\r\n# A\r\ntext\r\n")
            .sortedBy { it.startLine }

        assertEquals(listOf(FoldRegion(0, 2), FoldRegion(3, 4)), regions)
    }
}
