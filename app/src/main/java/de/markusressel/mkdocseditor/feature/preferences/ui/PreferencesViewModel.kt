package de.markusressel.mkdocseditor.feature.preferences.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.kutepreferences.core.KuteNavigator
import de.markusressel.kutepreferences.ui.vm.KutePreferencesViewModel
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.feature.preferences.domain.LastOfflineCacheUpdatePreferenceItem
import de.markusressel.mkdocseditor.feature.preferences.ui.compose.LastOfflineCacheUpdatePreferenceItemView
import de.markusressel.mkdocseditor.network.domain.IsOfflineCacheUpdateInProgressUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
internal class PreferencesViewModel @Inject constructor(
    preferencesHolder: KutePreferencesHolder,
    private val isOfflineCacheUpdateInProgressUseCase: IsOfflineCacheUpdateInProgressUseCase,
    private val kuteNavigator: KuteNavigator,
) : KutePreferencesViewModel(
    navigator = kuteNavigator
) {

    sealed interface Event {
        data object OpenAbout : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        registry.register<LastOfflineCacheUpdatePreferenceItem> {
            val value by preferencesHolder.lastOfflineCacheUpdate.persistedValue.collectAsState()
            val isUpdating by isOfflineCacheUpdateInProgressUseCase().collectAsState()
            LastOfflineCacheUpdatePreferenceItemView(
                title = preferencesHolder.lastOfflineCacheUpdate.title,
                icon = preferencesHolder.lastOfflineCacheUpdate.icon,
                lastUpdate = preferencesHolder.lastOfflineCacheUpdate.createDescription(value),
                isUpdating = isUpdating
            )
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
            } + preferencesHolder.aboutAction(onClick = { _events.trySend(Event.OpenAbout) })
        )
    }

    fun navigateUp() = kuteNavigator.goBack()

}
