package de.markusressel.mkdocseditor.view.component

import androidx.lifecycle.Lifecycle
import androidx.annotation.MenuRes
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import de.markusressel.mkdocseditor.view.fragment.base.LifecycleFragmentBase
import io.reactivex.rxkotlin.subscribeBy

/**
 * Created by Markus on 15.02.2018.
 */
class OptionsMenuComponent(hostFragment: LifecycleFragmentBase,
                           /**
                            * The layout resource for this Activity
                            */
                           @get:MenuRes val optionsMenuRes: Int, val onOptionsMenuItemClicked: ((item: MenuItem) -> Boolean)? = null, val onCreateOptionsMenu: ((menu: Menu?, inflater: MenuInflater?) -> Unit)? = null) : FragmentComponent(hostFragment) {

    init {
        hostFragment
                .lifecycle()
                .filter {
                    setOf(FragmentEvent.CREATE, FragmentEvent.RESUME, FragmentEvent.DESTROY)
                            .contains(it)
                }
                .bindUntilEvent(hostFragment, Lifecycle.Event.ON_DESTROY)
                .subscribeBy(onNext = {
                    when (it) {
                        FragmentEvent.CREATE -> {
                            hostFragment
                                    .setHasOptionsMenu(true)
                        }
                    }
                })
    }

    fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater
                ?.inflate(optionsMenuRes, menu)

        onCreateOptionsMenu
                ?.let {
                    it(menu, inflater)
                }
    }

    fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item == null) {
            return false
        }

        onOptionsMenuItemClicked
                ?.let {
                    return it(item)
                }

        return false
    }

}