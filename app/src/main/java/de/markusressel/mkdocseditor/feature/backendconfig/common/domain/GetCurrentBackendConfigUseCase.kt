package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import javax.inject.Inject

internal class GetCurrentBackendConfigUseCase @Inject constructor(
    private val backendManager: BackendManager
) {
    suspend operator fun invoke() = backendManager.currentBackend
}

