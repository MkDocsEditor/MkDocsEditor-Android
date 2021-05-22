package de.markusressel.mkdocseditor.view.fragment.base

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.IconHandler
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import javax.inject.Inject


/**
 * Base class for implementing a fragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerSupportFragmentBase : Fragment() {

    @Inject
    protected lateinit var iconHandler: IconHandler

    @Inject
    protected lateinit var preferencesHolder: KutePreferencesHolder

    protected val navController: NavController
        get() {
            val navHostFragment =
                childFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            return navHostFragment.navController
        }

}