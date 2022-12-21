package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import java.util.*
import javax.inject.Inject

internal data class SectionItem(
    val id: String,
    val name: String,
)

internal class GetCurrentSectionPathUseCase @Inject constructor() {
    operator fun invoke(
        backstack: Stack<SectionBackstackItem>
    ) = backstack.filter {
        it.sectionName.isNullOrEmpty().not()
    }.map {
        SectionItem(
            id = it.sectionId,
            name = it.sectionName ?: "/",
        )
    }
}