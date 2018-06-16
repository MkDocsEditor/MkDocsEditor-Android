package de.markusressel.mkdocseditor.view.fragment.preferences

import de.markusressel.kutepreferences.library.preference.KutePreferencesTree
import javax.inject.Inject

class PreferencesFragment : LifecyclePreferenceFragmentBase() {

    @Inject
    lateinit var preferenceHolder: KutePreferencesHolder

    override fun initPreferenceTree(): KutePreferencesTree {
        return KutePreferencesTree(preferenceHolder.connectionCategory, preferenceHolder.themePreference)
    }

}