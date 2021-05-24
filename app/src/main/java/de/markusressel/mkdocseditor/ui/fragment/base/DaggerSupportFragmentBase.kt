package de.markusressel.mkdocseditor.ui.fragment.base

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import de.markusressel.mkdocseditor.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.ui.IconHandler
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
        get() = findNavController()

}