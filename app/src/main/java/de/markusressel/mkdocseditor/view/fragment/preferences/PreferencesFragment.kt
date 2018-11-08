package de.markusressel.mkdocseditor.view.fragment.preferences

import de.markusressel.kutepreferences.core.KutePreferenceListItem
import de.markusressel.mkdocseditor.view.fragment.preferences.base.LifecyclePreferenceFragmentBase
import javax.inject.Inject

class PreferencesFragment : LifecyclePreferenceFragmentBase() {

    @Inject
    lateinit var preferenceHolder: KutePreferencesHolder

    override fun initPreferenceTree(): Array<KutePreferenceListItem> {
        return arrayOf(preferenceHolder.connectionCategory, preferenceHolder.themePreference)
    }

}