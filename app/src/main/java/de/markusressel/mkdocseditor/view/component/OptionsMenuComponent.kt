package de.markusressel.mkdocseditor.view.component

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * Created by Markus on 15.02.2018.
 */
class OptionsMenuComponent(val hostFragment: Fragment,
                           /**
                            * The layout resource for this Activity
                            */
                           @get:MenuRes val optionsMenuRes: Int,
                           val onOptionsMenuItemClicked: ((item: MenuItem) -> Boolean)? = null,
                           val onCreateOptionsMenu: ((menu: Menu?, inflater: MenuInflater?) -> Unit)? = null,
                           val onPrepareOptionsMenu: ((menu: Menu?) -> Unit)? = null)
    : FragmentComponent(hostFragment), LifecycleObserver {

    init {
        hostFragment.lifecycle.addObserver(this)
    }

    fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(optionsMenuRes, menu)
        onCreateOptionsMenu?.let {
            it(menu, inflater)
        }
    }

    fun onPrepareOptionsMenu(menu: Menu) {
        onPrepareOptionsMenu?.let {
            it(menu)
        }
    }

    fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item == null) {
            return false
        }

        onOptionsMenuItemClicked?.let {
            return it(item)
        }

        return false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onHostCreate() {
        hostFragment.setHasOptionsMenu(true)
    }


}