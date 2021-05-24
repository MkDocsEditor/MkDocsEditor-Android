package de.markusressel.mkdocseditor.ui.component

import android.content.Context
import androidx.fragment.app.Fragment

abstract class FragmentComponent(private val hostFragment: Fragment) {

    protected val fragment
        get() = hostFragment

    val context: Context?
        get() = hostFragment.context

}