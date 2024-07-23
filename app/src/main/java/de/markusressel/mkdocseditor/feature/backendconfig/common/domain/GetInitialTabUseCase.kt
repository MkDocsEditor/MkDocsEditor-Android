package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetInitialTabUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
) {
    suspend operator fun invoke() = when {
        getCurrentBackendConfigUseCase() != null -> NavItem.FileBrowser
        else -> NavItem.BackendSelection
    }
}

