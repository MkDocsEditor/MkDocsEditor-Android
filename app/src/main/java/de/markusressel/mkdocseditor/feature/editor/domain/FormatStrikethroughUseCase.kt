package de.markusressel.mkdocseditor.feature.editor.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FormatStrikethroughUseCase @Inject constructor(
) {
    operator fun invoke(text: String, selectionStart: Int, selectionEnd: Int): FormatResult {
        // check if the current selection is already striked through
        val selectionIsStrikethrough = run {
            val selectedText =
                text.substring((selectionStart - 2).coerceAtLeast(0), (selectionEnd + 2).coerceAtMost(text.length))
            selectedText.startsWith("~~") && selectedText.endsWith("~~")
        }

        return if (selectionIsStrikethrough) {
            val newText = text.substring(0, selectionStart - 2) +
                text.substring(selectionStart, selectionEnd) +
                text.substring(selectionEnd + 2)

            Triple(
                newText,
                selectionStart - 2,
                selectionEnd - 2
            )
        } else {
            val newText = text.substring(0, selectionStart) +
                "~~" +
                text.substring(selectionStart, selectionEnd) +
                "~~" +
                text.substring(selectionEnd)

            Triple(
                newText,
                selectionStart + 2,
                selectionEnd + 2
            )
        }
    }
}