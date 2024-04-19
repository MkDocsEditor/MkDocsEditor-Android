package de.markusressel.mkdocseditor.feature.editor.domain

import javax.inject.Inject
import javax.inject.Singleton

typealias FormatResult = Triple<String, Int, Int>

@Singleton
internal class FormatCodeBlockUseCase @Inject constructor(
) {
    operator fun invoke(text: String, selectionStart: Int, selectionEnd: Int): FormatResult {
        return Triple(
            text.substring(0, selectionStart) +
                "```\n" +
                text.substring(selectionStart, selectionEnd) +
                "\n```\n" +
                text.substring(selectionEnd),
            selectionStart + 4,
            selectionEnd + 4
        )
    }
}