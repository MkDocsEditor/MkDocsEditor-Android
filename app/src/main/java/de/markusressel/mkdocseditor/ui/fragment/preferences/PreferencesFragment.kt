package de.markusressel.mkdocseditor.ui.fragment.preferences

import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.kutepreferences.core.KutePreferenceListItem
import de.markusressel.kutepreferences.core.view.KutePreferencesMainFragment
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