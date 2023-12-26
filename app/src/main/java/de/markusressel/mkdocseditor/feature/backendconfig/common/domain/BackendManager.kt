package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class BackendManager @Inject constructor() {

    val currentBackend = MutableStateFlow<BackendConfig?>(null)

    fun setBackend(backendConfig: BackendConfig) {
        currentBackend.value = backendConfig
    }

}