package de.markusressel.mkdocseditor.feature.backendconfigselection.domain

import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendConfig
import javax.inject.Inject

internal class GetBackendConfigItemsUseCase @Inject constructor(

) {
    suspend operator fun invoke(): List<BackendConfig> {
        return listOf()
    }
}