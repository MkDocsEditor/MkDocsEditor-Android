package de.markusressel.mkdocseditor.network.domain

import de.markusressel.mkdocseditor.network.OfflineModeManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsOfflineModeEnabledFlowUseCase @Inject constructor(
    private val offlineModeManager: OfflineModeManager
) {
    operator fun invoke(): StateFlow<Boolean> {
        return offlineModeManager.isEnabled
    }
}