package de.markusressel.mkdocseditor.view.fragment.preferences

import android.content.Context
import android.support.v4.app.Fragment
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.HasSupportFragmentInjector
import de.markusressel.kutepreferences.library.view.KutePreferencesMainFragment
import javax.inject.Inject


/**
 * Dagger 2 (dependency injection) enabled base class for a KutePreferencesMainFragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerKutePreferenceFragmentBase : KutePreferencesMainFragment(), HasSupportFragmentInjector {

    @Inject
    lateinit var childFragmentInjector: DispatchingAndroidInjector<Fragment>

    override fun onAttach(context: Context) {
        AndroidSupportInjection
                .inject(this)
        super
                .onAttach(context)
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return childFragmentInjector
    }

}