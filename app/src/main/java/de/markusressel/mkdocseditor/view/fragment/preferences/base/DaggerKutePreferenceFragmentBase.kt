package de.markusressel.mkdocseditor.view.fragment.preferences.base

import android.content.Context
import dagger.android.support.AndroidSupportInjection
import de.markusressel.kutepreferences.core.view.KutePreferencesMainFragment


/**
 * Dagger 2 (dependency injection) enabled base class for a KutePreferencesMainFragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerKutePreferenceFragmentBase : KutePreferencesMainFragment() {

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

}