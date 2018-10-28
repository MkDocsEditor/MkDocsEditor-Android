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

import androidx.lifecycle.Lifecycle
import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import androidx.appcompat.widget.SearchView
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.TypedEpoxyController
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import com.trello.rxlifecycle2.kotlin.bindUntilEvent
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.extensions.common.android.gui.toast
import de.markusressel.mkdocseditor.network.ServerConnectivityManager
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.SectionBackstackItem
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_recyclerview.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Created by Markus on 29.01.2018.
 */
abstract class MultiPersistableListFragmentBase : NewListFragmentBase() {

    @Inject
    lateinit var connectivityManager: ServerConnectivityManager

    @Inject
    lateinit var restClient: MkDocsRestClient

    protected val listValues: MutableList<IdentifiableListItem> = ArrayList()

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(hostFragment = this, optionsMenuRes = R.menu.options_menu_list, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            val refreshMenuItem = menu
                    ?.findItem(R.id.refresh)
            refreshMenuItem
                    ?.icon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)

            val searchMenuItem = menu
                    ?.findItem(R.id.search)
            searchMenuItem
                    ?.icon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_search)

            val searchView = searchMenuItem?.actionView as SearchView?
            searchView
                    ?.let {
                        RxSearchView
                                .queryTextChanges(it)
                                .skipInitialValue()
                                .bindUntilEvent(this, Lifecycle.Event.ON_DESTROY)
                                .debounce(300, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(onNext = {
                                    currentSearchFilter = it
                                            .toString()
                                    reloadDataFromPersistence()
                                }, onError = {
                                    Timber
                                            .e(it) { "Error filtering list" }
                                })
                    }

        }, onOptionsMenuItemClicked = {
            when {
                it.itemId == R.id.refresh -> {
                    reload()
                    true
                }
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super
                .initComponents(context)
        optionsMenuComponent
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super
                .onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent
                .onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        return optionsMenuComponent
                .onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super
                .onViewCreated(view, savedInstanceState)

        reload()
    }

    private fun reload() {
        setRefreshing(true)

        restClient
                .isHostAlive()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
                .subscribeBy(onSuccess = {
                    activity!!
                            .toast("Online :)")
                    reloadDataFromSource()
                }, onError = {
                    if (it is CancellationException) {
                        setRefreshing(false)
                    } else {
                        activity!!
                                .toast("Server unavailable :(")
                        reloadDataFromPersistence()
                    }
                })
    }

    /**
     * Loads the data using {@link loadListDataFromPersistence()}
     */
    protected fun reloadDataFromPersistence() {
        setRefreshing(true)

        Single
                .fromCallable { loadListDataFromPersistence() }
                .map {
                    backstack
                            .clear()
                    backstack
                            .push(SectionBackstackItem(it as SectionEntity))
                    it
                }
                .map {
                    sectionToList(it as SectionEntity)
                }
                .map {
                    filterAndSortList(it)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
                .subscribeBy(onSuccess = {
                    updateAdapterList(it)
                    scrollToItemPosition(lastScrollPosition)
                }, onError = {
                    if (it is CancellationException) {
                        Timber
                                .d { "reload from persistence cancelled" }
                    } else {
                        loadingComponent
                                .showError(it)
                        setRefreshing(false)
                    }
                })
    }

    /**
     * Filter list items that don't match this function from the visible list
     * @return true, if the item is ok, false if it should be filtered
     */
    internal open fun filterListItem(item: IdentifiableListItem): Boolean {
        return true
    }

    /**
     * @return true, if a match was found, false otherwise
     */
    abstract fun itemContainsCurrentSearchString(item: IdentifiableListItem): Boolean

    /**
     * Updates the content of the adapter behind the recyclerview
     */
    protected fun updateAdapterList(newData: List<IdentifiableListItem>) {
        listValues
                .clear()
        listValues
                .addAll(newData)

        if (listValues.isEmpty()) {
            showEmpty()
        } else {
            hideEmpty()
        }
        setRefreshing(false)

        updateControllerData(newData)
    }

    abstract fun updateControllerData(newData: List<IdentifiableListItem>)

    /**
     * Filters and sorts the given list by the currently active filter and sort options
     * Remember to call this before using {@link updateAdapterList }
     */
    private fun filterAndSortList(newData: List<IdentifiableListItem>): List<IdentifiableListItem> {
        val filteredNewData = newData
                .filter {
                    currentSearchFilter.isEmpty() || itemContainsCurrentSearchString(it)
                }
                .filter { filterListItem(it) }
                .toList()
        return sortListData(filteredNewData)
    }

    /**
     * Sorts a list by the currently selected SortOptions
     */
    abstract fun sortListData(listData: List<IdentifiableListItem>): List<IdentifiableListItem>

    /**
     * Reload list data from it's original source, persist it and display it to the user afterwards
     */
    override fun reloadDataFromSource() {
        setRefreshing(true)

        getLoadDataFromSourceFunction()
                .map {
                    mapToEntity(it)
                }
                .map {
                    persistListData(it)
                    it
                }
                .map {
                    backstack
                            .clear()
                    backstack
                            .push(SectionBackstackItem(it as SectionEntity))
                    it
                }
                .map {
                    sectionToList(it as SectionEntity)
                }
                .map {
                    filterAndSortList(it)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
                .subscribeBy(onSuccess = {
                    updateLastUpdatedFromSource()
                    updateAdapterList(it)
                    scrollToItemPosition(lastScrollPosition)
                }, onError = {
                    if (it is CancellationException) {
                        Timber
                                .d { "reload from source cancelled" }
                    } else {
                        loadingComponent
                                .showError(it)
                        setRefreshing(false)
                    }
                })
    }

    private fun sectionToList(section: SectionEntity): List<IdentifiableListItem> {
        return listOf(*section.subsections.toTypedArray(), *section.documents.toTypedArray(), *section.resources.toTypedArray())
    }

    internal fun openSection(section: SectionEntity, addToBackstack: Boolean = true) {
        Timber
                .d { "Opening Section '${section.name}'" }

        if (addToBackstack) {
            backstack
                    .push(SectionBackstackItem(section))
        }

        listValues
                .clear()
        listValues
                .addAll(sectionToList(section))

        updateControllerData(sectionToList(section))

        loadingComponent
                .showContent()
    }

    /**
     * Define a Single that returns the complete list of data from the (server) source
     */
    abstract internal fun getLoadDataFromSourceFunction(): Single<Any>

    /**
     * Map the source object to the persistence object
     */
    abstract fun mapToEntity(it: Any): IdentifiableListItem

    /**
     * Persist the current list data
     */
    abstract internal fun persistListData(data: IdentifiableListItem)

    private fun getLastUpdatedFromSource(): Long {
        // TODO:
        return 0
    }

    private fun updateLastUpdatedFromSource() {
        // TODO:
    }

    /**
     * Load the data to be displayed in the list from the persistence
     */
    abstract fun loadListDataFromPersistence(): IdentifiableListItem?

}
