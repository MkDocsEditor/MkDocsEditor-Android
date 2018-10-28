package de.markusressel.mkdocseditor.view.fragment.preferences.base

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.HasSupportFragmentInjector
import de.markusressel.kutepreferences.core.view.KutePreferencesMainFragment
import javax.inject.Inject


/**
 * Dagger 2 (dependency injection) enabled base class for a KutePreferencesMainFragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerKutePreferenceFragmentBase : KutePreferencesMainFragment(), HasSupportFragmentInjector {

    @Inject
    lateinit var childFragmentInjector: DispatchingAndroidInjector<androidx.fragment.app.Fragment>

    override fun onAttach(context: Context) {
        AndroidSupportInjection
                .inject(this)
        super
                .onAttach(context)
    }

    override fun supportFragmentInjector(): AndroidInjector<androidx.fragment.app.Fragment> {
        return childFragmentInjector
    }

}