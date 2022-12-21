package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import java.util.*
import javax.inject.Inject

internal class GetCurrentSectionPathUseCase @Inject constructor() {
    operator fun invoke(
        backstack: Stack<SectionBackstackItem>
    ) = backstack.map {
        it.sectionName
    }.filter {
        it.isNullOrEmpty().not()
    }.joinToString(prefix = "/", separator = "/")
}