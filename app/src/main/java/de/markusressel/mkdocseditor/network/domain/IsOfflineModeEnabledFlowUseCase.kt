package de.markusressel.mkdocseditor.network.domain

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsOfflineModeEnabledFlowUseCase @Inject constructor(
    private val getOfflineModeEnabledSettingFlowUseCase: GetOfflineModeEnabledSettingFlowUseCase,
) {
    operator fun invoke(): StateFlow<Boolean> {
        return getOfflineModeEnabledSettingFlowUseCase()
    }
}