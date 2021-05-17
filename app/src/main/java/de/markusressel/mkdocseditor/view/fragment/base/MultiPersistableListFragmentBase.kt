/*
 * Copyright (C) 2018 Markus Ressel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.markusressel.mkdocseditor.view.fragment.base

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit


/**
 * Created by Markus on 29.01.2018.
 */
abstract class MultiPersistableListFragmentBase : ListFragmentBase() {

    // TODO: this should be fed by a livedata observer
    private var serverUnavailableSnackbar: Snackbar? = null

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(hostFragment = this,
            optionsMenuRes = R.menu.options_menu_list,
            onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
                menu?.findItem(R.id.refresh)?.apply {
                    icon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                }

                menu?.findItem(R.id.search)?.apply {
                    icon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_search)
                    (actionView as SearchView?)?.apply {
                        RxSearchView
                            .queryTextChanges(this)
                            .skipInitialValue()
                            .bindUntilEvent(viewLifecycleOwner, Lifecycle.Event.ON_DESTROY)
                            .debounce(300, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(onNext = {
                                // TODO: set currentSearchFilter on viewModel
                                // viewModel.currentSearchFilter = it.toString()
                            }, onError = {
                                Timber.e(it) { "Error filtering list" }
                            })
                    }
                }
            }, onOptionsMenuItemClicked = {
                when (it.itemId) {
                    R.id.refresh -> {
                        // TODO: notify viewmodel to reload data
                        true
                    }
                    else -> false
                }
            })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        optionsMenuComponent
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

}
