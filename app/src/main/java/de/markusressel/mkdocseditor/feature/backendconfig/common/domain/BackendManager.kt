package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendConfig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackendManager @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {

    val selectedBackendFlow: StateFlow<BackendConfig?> = backendConfigRepository
        .selectedBackendConfigFlow()
        .map { it?.toBackendConfig() }
        .stateIn(MainScope(), SharingStarted.Eagerly, null)

    fun setBackend(backendConfig: BackendConfig) {
        backendConfigRepository.selectBackendConfig(backendConfig)
    }

    fun getSelectedBackend(): BackendConfig? {
        return backendConfigRepository.selectedBackendConfig()?.toBackendConfig()
    }

}