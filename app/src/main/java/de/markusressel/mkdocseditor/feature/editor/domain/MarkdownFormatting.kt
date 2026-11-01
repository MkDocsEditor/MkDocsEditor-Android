package de.markusressel.mkdocseditor.feature.editor.domain

typealias FormatResult = Triple<String, Int, Int>

/**
 * A formatting action of the editor toolbar. The web client offers the same set.
 */
sealed interface FormatAction {
    data object Bold : FormatAction
    data object Italic : FormatAction
    data object Strikethrough : FormatAction
    data object InlineCode : FormatAction
    data object CodeBlock : FormatAction
    data class Heading(val level: Int) : FormatAction
    data object BulletList : FormatAction
    data object NumberedList : FormatAction
    data object TaskList : FormatAction
    data object Quote : FormatAction
    data object Link : FormatAction
    data object Image : FormatAction
    data object Table : FormatAction
    data class Admonition(val type: String) : FormatAction
    data object HorizontalRule : FormatAction
}

/**
 * The admonition types of MkDocs Material, offered by the toolbar
 */
val AdmonitionTypes = listOf(
    "note", "abstract", "info", "tip", "success", "question",
    "warning", "failure", "danger", "bug", "example", "quote",
)

/**
 * The result of a formatting action: the new text and the new selection
 */
data class FormatEdit(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/**
 * Pure markdown formatting functions. They take the text and the selection and return the
 * new text and the new selection. Inline formats toggle, line formats toggle on all selected
 * lines, and block formats are put on lines of their own.
 */
object MarkdownFormatting {

    fun apply(action: FormatAction, text: String, selectionStart: Int, selectionEnd: Int): FormatEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        return when (action) {
            FormatAction.Bold -> FormatBoldUseCase()(text, start, end).toEdit()
            FormatAction.Italic -> FormatItalicUseCase()(text, start, end).toEdit()
            FormatAction.Strikethrough -> FormatStrikethroughUseCase()(text, start, end).toEdit()
            FormatAction.InlineCode -> toggleInline(text, start, end, "`")
            FormatAction.CodeBlock -> codeBlock(text, start, end)
            is FormatAction.Heading -> heading(text, start, end, action.level)
            FormatAction.BulletList -> toggleLinePrefix(text, start, end, bulletPrefix) { "- " }
            FormatAction.NumberedList -> toggleLinePrefix(text, start, end, numberedPrefix) { "${it + 1}. " }
            FormatAction.TaskList -> toggleLinePrefix(text, start, end, taskPrefix) { "- [ ] " }
            FormatAction.Quote -> toggleLinePrefix(text, start, end, quotePrefix) { "> " }
            FormatAction.Link -> link(text, start, end, image = false)
            FormatAction.Image -> link(text, start, end, image = true)
            FormatAction.Table -> table(text, start, end)
            is FormatAction.Admonition -> admonition(text, start, end, action.type)
            FormatAction.HorizontalRule -> insertBlock(text, start, end, "---", cursorOffset = 3)
        }
    }

    private fun FormatResult.toEdit() = FormatEdit(first, second, third)

    private val bulletPrefix = "^([ \\t]*)[-*+] (?!\\[[ xX]] )".toRegex()
    private val numberedPrefix = "^([ \\t]*)\\d+[.)] ".toRegex()
    private val taskPrefix = "^([ \\t]*)[-*+] \\[[ xX]] ".toRegex()
    private val quotePrefix = "^([ \\t]*)> ?".toRegex()
    private val anyListPrefix = "^([ \\t]*)(?:[-*+] \\[[ xX]] |[-*+] |\\d+[.)] |> ?)".toRegex()
    private val headingPrefix = "^ {0,3}(#{1,6})[ \\t]+".toRegex()

    /**
     * Wraps the selection in [marker], or removes the marker if the selection is wrapped already
     */
    fun toggleInline(text: String, start: Int, end: Int, marker: String): FormatEdit {
        val m = marker.length
        val wrapped = start >= m && end + m <= text.length &&
            text.regionMatches(start - m, marker, 0, m) && text.regionMatches(end, marker, 0, m)
        return if (wrapped) {
            FormatEdit(text.removeRange(end, end + m).removeRange(start - m, start), start - m, end - m)
        } else {
            FormatEdit(text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end), start + m, end + m)
        }
    }

    /** the start of the line that contains [index] */
    private fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

    /** the end of the line that contains [index], without the line break */
    private fun lineEnd(text: String, index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    /**
     * The range of full lines touched by the selection. A selection that ends right at the
     * start of a line does not include that line.
     */
    private fun selectedLines(text: String, start: Int, end: Int): IntRange {
        val first = lineStart(text, start)
        val lastIndex = if (end > start && end > 0 && text[end - 1] == '\n') end - 1 else end
        val last = lineEnd(text, maxOf(lastIndex, first))
        return first until last
    }

    /**
     * Replaces the lines touched by the selection with [transform] of them, and keeps the
     * selection on the same text where possible
     */
    private fun replaceLines(
        text: String,
        start: Int,
        end: Int,
        transform: (List<String>) -> List<String>,
    ): FormatEdit {
        val range = selectedLines(text, start, end)
        val oldLines = text.substring(range.first, range.last + 1).split('\n')
        val newLines = transform(oldLines)
        val replacement = newLines.joinToString("\n")
        val newText = text.substring(0, range.first) + replacement + text.substring(range.last + 1)

        // shift start by the change in its line, end by the change of all lines
        val firstLineDelta = newLines.first().length - oldLines.first().length
        val newStart = (start + firstLineDelta).coerceIn(range.first, range.first + replacement.length)
        val newEnd = if (start == end) newStart else (end + replacement.length - (range.last + 1 - range.first))
            .coerceIn(newStart, newText.length)
        return FormatEdit(newText, newStart, newEnd)
    }

    /**
     * Adds a line prefix to all selected lines, or removes it if all lines have it already.
     * Other list prefixes are replaced, so a bullet list turns into a numbered list.
     */
    private fun toggleLinePrefix(
        text: String,
        start: Int,
        end: Int,
        existing: Regex,
        prefix: (index: Int) -> String,
    ): FormatEdit = replaceLines(text, start, end) { lines ->
        val contentLines = lines.filter { it.isNotBlank() }.ifEmpty { lines }
        val allHavePrefix = contentLines.all { existing.containsMatchIn(it) }
        var index = 0
        lines.map { line ->
            when {
                allHavePrefix -> line.replaceFirst(existing, "$1")
                line.isBlank() && lines.size > 1 -> line
                else -> {
                    val indent = anyListPrefix.find(line)?.groupValues?.get(1)
                        ?: line.takeWhile { it == ' ' || it == '\t' }
                    val content = line.replaceFirst(anyListPrefix, "").trimStart(' ', '\t')
                    indent + prefix(index++) + content
                }
            }
        }
    }

    /**
     * Sets the heading level of the selected lines; the same level again removes the heading
     */
    fun heading(text: String, start: Int, end: Int, level: Int): FormatEdit {
        val marker = "#".repeat(level.coerceIn(1, 6)) + " "
        return replaceLines(text, start, end) { lines ->
            val allAtLevel = lines.all { headingPrefix.find(it)?.groupValues?.get(1)?.length == level }
            lines.map { line ->
                val content = line.replaceFirst(headingPrefix, "")
                if (allAtLevel) content else marker + content
            }
        }
    }

    /**
     * Inserts [block] on lines of its own, separated from other content by blank lines
     *
     * @param cursorOffset where to put the cursor, relative to the start of [block]
     */
    private fun insertBlock(text: String, start: Int, end: Int, block: String, cursorOffset: Int, selectionLength: Int = 0): FormatEdit {
        val before = text.substring(0, start).trimEnd(' ', '\t')
        val after = text.substring(end).trimStart(' ', '\t')
        val leading = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trailing = when {
            after.startsWith("\n\n") -> ""
            after.startsWith("\n") -> "\n"
            after.isEmpty() -> "\n"
            else -> "\n\n"
        }
        val blockStart = before.length + leading.length
        return FormatEdit(
            before + leading + block + trailing + after,
            blockStart + cursorOffset,
            blockStart + cursorOffset + selectionLength,
        )
    }

    fun codeBlock(text: String, start: Int, end: Int): FormatEdit {
        val selected = text.substring(start, end).trimEnd('\n')
        val block = "```\n$selected\n```"
        // with a selection, select the code; without one, put the cursor into the empty block
        return insertBlock(text, start, end, block, cursorOffset = 4, selectionLength = selected.length)
    }

    fun table(text: String, start: Int, end: Int): FormatEdit {
        val block = """
            | Column 1 | Column 2 |
            | -------- | -------- |
            | Cell     | Cell     |
        """.trimIndent()
        // select "Column 1", so the user can type the first header right away
        return insertBlock(text, start, end, block, cursorOffset = 2, selectionLength = "Column 1".length)
    }

    fun admonition(text: String, start: Int, end: Int, type: String): FormatEdit {
        val selected = text.substring(start, end).trimEnd('\n')
        val body = if (selected.isEmpty()) "" else selected.lines().joinToString("\n") { if (it.isBlank()) "" else "    $it" }
        val header = "!!! $type"
        val block = if (body.isEmpty()) "$header\n    " else "$header\n$body"
        val cursor = if (body.isEmpty()) block.length else header.length + 1
        return insertBlock(text, start, end, block, cursorOffset = cursor, selectionLength = if (body.isEmpty()) 0 else body.length)
    }

    /**
     * `[selection](url)` with "url" selected. If the selection is a URL, it becomes the target
     * and the cursor goes into the empty link text.
     */
    fun link(text: String, start: Int, end: Int, image: Boolean): FormatEdit {
        val selected = text.substring(start, end)
        val prefix = if (image) "![" else "["
        val isUrl = selected.matches("^(?:https?://|mailto:)\\S+$".toRegex())
        return if (isUrl) {
            val inserted = "$prefix]($selected)"
            FormatEdit(text.replaceRange(start, end, inserted), start + prefix.length)
        } else {
            val placeholder = "url"
            val inserted = "$prefix$selected]($placeholder)"
            val urlStart = start + prefix.length + selected.length + 2
            FormatEdit(text.replaceRange(start, end, inserted), urlStart, urlStart + placeholder.length)
        }
    }

    /**
     * Inserts a finished link or image (from a picker) for [target], with the selected text
     * (or [defaultLabel]) as its text
     */
    fun insertLink(text: String, start: Int, end: Int, target: String, defaultLabel: String, image: Boolean): FormatEdit {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val label = text.substring(s, e).ifEmpty { defaultLabel }.replace("\n", " ")
        val inserted = (if (image) "![" else "[") + label + "](" + escapeLinkTarget(target) + ")"
        return FormatEdit(text.replaceRange(s, e, inserted), s + inserted.length)
    }

    /**
     * Makes a link target safe to put into `(...)`: spaces and parentheses would end it
     */
    fun escapeLinkTarget(target: String): String = buildString {
        target.forEach {
            when (it) {
                ' ' -> append("%20")
                '(' -> append("%28")
                ')' -> append("%29")
                else -> append(it)
            }
        }
    }
}
