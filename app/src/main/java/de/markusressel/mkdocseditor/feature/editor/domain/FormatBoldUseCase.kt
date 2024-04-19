package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.extensions.common.safeSubstring
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FormatBoldUseCase @Inject constructor(
) {
    operator fun invoke(text: String, selectionStart: Int, selectionEnd: Int): FormatResult {

        // check if the current selection is already bold
        val selectionIsBold = run {
            val selectedText =
                text.safeSubstring((selectionStart - 2), (selectionEnd + 2))
            selectedText.startsWith("**") && selectedText.endsWith("**")
        }

        return if (selectionIsBold) {
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
                "**" +
                text.substring(selectionStart, selectionEnd) +
                "**" +
                text.substring(selectionEnd)

            Triple(
                newText,
                selectionStart + 2,
                selectionEnd + 2
            )
        }
    }
}