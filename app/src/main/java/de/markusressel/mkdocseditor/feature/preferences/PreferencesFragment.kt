package de.markusressel.mkdocseditor.feature.preferences

import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.kutepreferences.core.KutePreferenceListItem
import de.markusressel.kutepreferences.core.view.KutePreferencesMainFragment
import de.markusressel.mkdocseditor.data.KutePreferencesHolder
import javax.inject.Inject

@AndroidEntryPoint
class PreferencesFragment : KutePreferencesMainFragment() {

    @Inject
    lateinit var preferenceHolder: KutePreferencesHolder

    override fun initPreferenceTree(): Array<KutePreferenceListItem> {
        return arrayOf(
            preferenceHolder.connectionCategory,
            preferenceHolder.offlineCacheCategory,
            preferenceHolder.uxCategory,
            preferenceHolder.themePreference
        )
    }

}