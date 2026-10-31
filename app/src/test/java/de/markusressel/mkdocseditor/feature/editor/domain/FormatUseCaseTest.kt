package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUseCaseTest {

    @Test
    fun `bold wraps the selection and keeps it selected`() {
        val result = FormatBoldUseCase()("hello world", 6, 11)
        assertEquals(Triple("hello **world**", 8, 13), result)
    }

    @Test
    fun `bold on an already bold selection removes the markers`() {
        val result = FormatBoldUseCase()("hello **world**", 8, 13)
        assertEquals(Triple("hello world", 6, 11), result)
    }

    @Test
    fun `bold with an empty selection inserts markers around the cursor`() {
        val result = FormatBoldUseCase()("ab", 1, 1)
        assertEquals(Triple("a****b", 3, 3), result)
    }

    @Test
    fun `bold at the start of a text that begins with markers does not crash`() {
        val result = FormatBoldUseCase()("**", 0, 0)
        assertEquals(Triple("******", 2, 2), result)
    }

    @Test
    fun `italic wraps and unwraps the selection`() {
        val wrapped = FormatItalicUseCase()("hello world", 0, 5)
        assertEquals(Triple("*hello* world", 1, 6), wrapped)

        val unwrapped = FormatItalicUseCase()(wrapped.first, wrapped.second, wrapped.third)
        assertEquals(Triple("hello world", 0, 5), unwrapped)
    }

    @Test
    fun `italic at the start of a text that begins with a marker does not crash`() {
        val result = FormatItalicUseCase()("*", 0, 0)
        assertEquals(Triple("***", 1, 1), result)
    }

    @Test
    fun `strikethrough wraps and unwraps the selection`() {
        val wrapped = FormatStrikethroughUseCase()("a b c", 2, 3)
        assertEquals(Triple("a ~~b~~ c", 4, 5), wrapped)

        val unwrapped = FormatStrikethroughUseCase()(wrapped.first, wrapped.second, wrapped.third)
        assertEquals(Triple("a b c", 2, 3), unwrapped)
    }

    @Test
    fun `strikethrough at the start of a text that begins with markers does not crash`() {
        val result = FormatStrikethroughUseCase()("~~", 0, 0)
        assertEquals(Triple("~~~~~~", 2, 2), result)
    }

    @Test
    fun `list bullet is inserted at the selection start`() {
        val result = FormatListBulletedUseCase()("item", 0, 4)
        assertEquals(Triple("- item", 2, 6), result)
    }

    @Test
    fun `code block fences the selection`() {
        val result = FormatCodeBlockUseCase()("x = 1", 0, 5)
        assertEquals(Triple("```\nx = 1\n```\n", 4, 9), result)
    }
}
