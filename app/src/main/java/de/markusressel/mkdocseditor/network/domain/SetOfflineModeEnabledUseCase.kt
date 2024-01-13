package de.markusressel.mkdocseditor.network.domain

import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetOfflineModeEnabledUseCase @Inject constructor(
    private val kutePreferencesHolder: KutePreferencesHolder,
) {
    operator fun invoke(enabled: Boolean) {
        kutePreferencesHolder.offlineModePreference.persistValue(enabled)
    }
}