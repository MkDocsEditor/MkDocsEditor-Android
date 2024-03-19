package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.SectionBackstackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Stack
import javax.inject.Inject
import javax.inject.Singleton

internal data class SectionItem(
    val id: String,
    val name: String,
)

@Singleton
internal class GetCurrentSectionPathUseCase @Inject constructor() {
    suspend operator fun invoke(
        backstack: Stack<SectionBackstackItem>
    ) = withContext(Dispatchers.IO) {
        backstack.filter {
            it.sectionName.isNullOrEmpty().not()
        }.map {
            SectionItem(
                id = it.sectionId,
                name = it.sectionName ?: "/",
            )
        }
    }
}