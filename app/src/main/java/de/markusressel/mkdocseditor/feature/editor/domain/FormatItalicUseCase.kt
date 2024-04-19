package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.extensions.common.safeSubstring
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FormatItalicUseCase @Inject constructor(
) {
    operator fun invoke(text: String, selectionStart: Int, selectionEnd: Int): FormatResult {
        // check if the current selection is already italic
        val selectionIsItalic = run {
            val selectedText =
                text.safeSubstring((selectionStart - 1), (selectionEnd + 1))
            selectedText.startsWith("*") && selectedText.endsWith("*")
        }

        return if (selectionIsItalic) {
            val newText = text.substring(0, selectionStart - 1) +
                text.substring(selectionStart, selectionEnd) +
                text.substring(selectionEnd + 1)

            Triple(
                newText,
                selectionStart - 1,
                selectionEnd - 1
            )
        } else {
            val newText = text.substring(0, selectionStart) +
                "*" +
                text.substring(selectionStart, selectionEnd) +
                "*" +
                text.substring(selectionEnd)
            Triple(
                newText,
                selectionStart + 1,
                selectionEnd + 1
            )
        }
    }
}