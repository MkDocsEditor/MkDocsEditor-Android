package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetInitialTabUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val preferencesHolder: KutePreferencesHolder,
) {
    suspend operator fun invoke() = when {
        getCurrentBackendConfigUseCase() == null -> NavItem.BackendSelection
        preferencesHolder.startPagePreference.persistedValue.value == context.getString(R.string.start_page_files_value) ->
            NavItem.FileBrowser

        else -> NavItem.Dashboard
    }
}
