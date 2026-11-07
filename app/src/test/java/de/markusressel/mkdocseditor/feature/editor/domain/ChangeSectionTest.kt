package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeSectionTest {

    private val page = """
        # Shopping

        ## Fruit
        - apples

        ## Drinks
        - water
        - juice
    """.trimIndent()

    @Test
    fun `a heading of the change is the section up to the next heading`() {
        assertEquals(ChangeSection(startLine = 5, endLine = 8), ChangeSection.find(page, listOf("Drinks")))
        assertEquals(ChangeSection(startLine = 2, endLine = 5), ChangeSection.find(page, listOf("Fruit")))
    }

    @Test
    fun `the first heading that the text still has wins`() {
        assertEquals(ChangeSection(5, 8), ChangeSection.find(page, listOf("Vegetables", "Drinks", "Fruit")))
    }

    @Test
    fun `without a heading that the text has the page stays at the top`() {
        assertNull(ChangeSection.find(page, listOf("Vegetables")))
        assertNull(ChangeSection.find(page, emptyList()))
        assertNull(ChangeSection.find("no headings at all", listOf("Fruit")))
    }

    @Test
    fun `of several headings with the same name the first one counts`() {
        val text = "# A\n## Notes\none\n# B\n## Notes\ntwo"
        assertEquals(ChangeSection(1, 3), ChangeSection.find(text, listOf("Notes")))
    }

    @Test
    fun `a heading in a code fence is no heading`() {
        val text = "# Setup\n```bash\n# Drinks\necho\n```\n## Drinks\nwater"
        assertEquals(ChangeSection(5, 7), ChangeSection.find(text, listOf("Drinks")))
        assertEquals(ChangeSection(0, 5), ChangeSection.find(text, listOf("Setup")))
    }

    @Test
    fun `a heading matches as the backend names it, with its Markdown, and as plain text`() {
        val text = "# Intro\n## See [the guide](guide.md) {#guide}\ntext"
        assertEquals(ChangeSection(1, 3), ChangeSection.find(text, listOf("See [the guide](guide.md) {#guide}")))
        assertEquals(ChangeSection(1, 3), ChangeSection.find(text, listOf("See the guide")))
    }
}
