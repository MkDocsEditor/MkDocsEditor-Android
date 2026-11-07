package de.markusressel.mkdocseditor.feature.notifications.domain

import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import javax.inject.Inject

/**
 * Opens the page of a tapped notification. The user may have selected another server since, so its server is
 * selected again first. The page opens at the first of the changed headings it still has.
 */
internal class OpenNotifiedDocumentUseCase @Inject constructor(
    private val backends: AuthBackends,
    private val navigation: MainNavigationRequests,
) {
    suspend operator fun invoke(backendId: Long, documentId: String, changedHeadings: List<String> = emptyList()) {
        // a removed server has nothing left to open
        val config = backends.get(backendId) ?: return
        if (backends.selected()?.id != backendId) backends.select(config)
        navigation.openDocument(documentId, changedHeadings)
    }
}
