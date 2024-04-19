package de.markusressel.mkdocseditor.feature.editor.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FormatListBulletedUseCase @Inject constructor(
) {
    operator fun invoke(text: String, selectionStart: Int, selectionEnd: Int): FormatResult {
        return Triple(
            text.substring(0, selectionStart) +
                "- " +
                text.substring(selectionStart, selectionEnd) +
                text.substring(selectionEnd),
            selectionStart + 2,
            selectionEnd + 2
        )
    }
}