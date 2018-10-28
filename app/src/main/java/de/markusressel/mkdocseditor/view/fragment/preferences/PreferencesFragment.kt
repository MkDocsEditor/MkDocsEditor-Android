package de.markusressel.mkdocseditor.view.fragment.preferences

import de.markusressel.kutepreferences.core.preference.KutePreferencesTree
import de.markusressel.mkdocseditor.view.fragment.preferences.base.LifecyclePreferenceFragmentBase
import javax.inject.Inject

class PreferencesFragment : LifecyclePreferenceFragmentBase() {

    @Inject
    lateinit var preferenceHolder: KutePreferencesHolder

    override fun initPreferenceTree(): KutePreferencesTree {
        return KutePreferencesTree(preferenceHolder.connectionCategory, preferenceHolder.themePreference)
    }

}