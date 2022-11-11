package de.markusressel.mkdocseditor.feature.preferences

import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.kutepreferences.core.KuteNavigator
import de.markusressel.kutepreferences.ui.vm.KutePreferencesViewModel
import de.markusressel.mkdocseditor.data.KutePreferencesHolder
import javax.inject.Inject

@HiltViewModel
internal class PreferencesViewModel @Inject constructor(
    preferencesHolder: KutePreferencesHolder,
    private val kuteNavigator: KuteNavigator,
) : KutePreferencesViewModel(
    navigator = kuteNavigator
) {

    init {
        initPreferencesTree(
            listOf(
                preferencesHolder.connectionCategory,
                preferencesHolder.offlineCacheCategory,
                preferencesHolder.uxCategory,
                preferencesHolder.themePreference
            )
        )
    }

    fun navigateUp() = kuteNavigator.goBack()

}
