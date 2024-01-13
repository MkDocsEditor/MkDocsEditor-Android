package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetCurrentBackendConfigFlowUseCase @Inject constructor(
    private val backendManager: BackendManager
) {
    suspend operator fun invoke() = backendManager.selectedBackendFlow
}

