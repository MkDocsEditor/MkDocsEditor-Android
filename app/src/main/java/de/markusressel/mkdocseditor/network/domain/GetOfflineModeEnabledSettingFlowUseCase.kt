package de.markusressel.mkdocseditor.network.domain

import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetOfflineModeEnabledSettingFlowUseCase @Inject constructor(
    private val kutePreferencesHolder: KutePreferencesHolder,
) {
    operator fun invoke() = kutePreferencesHolder.offlineModePreference.getPersistedValueFlow()
}