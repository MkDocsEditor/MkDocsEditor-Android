package de.markusressel.mkdocseditor.ui.fragment.base

import androidx.fragment.app.Fragment
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.ui.IconHandler
import javax.inject.Inject


/**
 * Base class for implementing a fragment
 *
 * Created by Markus on 07.01.2018.
 */
abstract class DaggerSupportFragmentBase : Fragment() {

    @Inject
    internal lateinit var iconHandler: IconHandler

    @Inject
    internal lateinit var preferencesHolder: KutePreferencesHolder

}