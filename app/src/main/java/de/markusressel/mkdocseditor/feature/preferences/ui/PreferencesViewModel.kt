package de.markusressel.mkdocseditor.feature.preferences.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.kutepreferences.core.KuteNavigator
import de.markusressel.kutepreferences.ui.views.KuteStyleManager
import de.markusressel.kutepreferences.ui.vm.KutePreferencesViewModel
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.feature.preferences.domain.LastOfflineCacheUpdatePreferenceItem
import de.markusressel.mkdocseditor.feature.preferences.ui.compose.LastOfflineCacheUpdatePreferenceItemView
import de.markusressel.mkdocseditor.network.domain.IsOfflineCacheUpdateInProgressUseCase
import javax.inject.Inject

@HiltViewModel
internal class PreferencesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    preferencesHolder: KutePreferencesHolder,
    private val isOfflineCacheUpdateInProgressUseCase: IsOfflineCacheUpdateInProgressUseCase,
    private val kuteNavigator: KuteNavigator,
) : KutePreferencesViewModel(
    navigator = kuteNavigator
) {

    init {
        KuteStyleManager.registerTypeHook { listItem ->
            when (listItem) {
                is LastOfflineCacheUpdatePreferenceItem -> {
                    val value by preferencesHolder.lastOfflineCacheUpdate.persistedValue.collectAsState()
                    val isUpdating by isOfflineCacheUpdateInProgressUseCase().collectAsState()
                    LastOfflineCacheUpdatePreferenceItemView(
                        lastUpdate = preferencesHolder.lastOfflineCacheUpdate.createDescription(
                            value
                        ),
                        isUpdating = isUpdating
                    )
                    true
                }

                else -> false
            }

        }

        initPreferencesTree(
            listOf(
                preferencesHolder.offlineCacheCategory,
                preferencesHolder.uxCategory,
                preferencesHolder.uiCategory,
            ).let {
                when {
                    BuildConfig.DEBUG -> it + listOf(preferencesHolder.debugCategory)
                    else -> it
                }
            }
        )
    }

    fun navigateUp() = kuteNavigator.goBack()

}
