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

package de.markusressel.mkdocseditor.ui.fragment.base

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.annotation.CallSuper
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.airbnb.epoxy.Typed3EpoxyController
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindUntilEvent
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.databinding.FragmentRecyclerviewBinding
import de.markusressel.mkdocseditor.ui.component.OptionsMenuComponent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit


/**
 * List base class
 */
abstract class ListFragmentBase : DaggerSupportFragmentBase() {

    lateinit var binding: FragmentRecyclerviewBinding

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

    protected open fun getFabConfig(): FabConfig? = null

    internal val epoxyController by lazy { createEpoxyController() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRecyclerviewBinding.inflate(layoutInflater, container, false)
        return binding.root
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

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = binding.recyclerView
        recyclerView.setController(epoxyController)
        recyclerView.layoutManager = StaggeredGridLayoutManager(
            resources.getInteger(R.integer.list_column_count),
            StaggeredGridLayoutManager.VERTICAL
        )

        setupFabs()
    }

    internal fun scrollToItemPosition(itemPosition: Int) {
        val layoutManager = binding.recyclerView.layoutManager
        // this always returns 0 :(
//        if (itemPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION && layoutManager.childCount > 0) {
//            layoutManager
//                    .scrollToPosition((itemPosition.coerceIn(0, layoutManager.childCount - 1)))
//        }
    }

    /**
     * Create the epoxy controller here.
     * The epoxy controller defines what information is displayed.
     */
    abstract fun createEpoxyController(): Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>

    private fun setupFabs() {
        val fabConfig = getFabConfig() ?: return

        binding.speedDial.apply {
            setMainFabClosedDrawable(iconHandler.getFabIcon(MaterialDesignIconic.Icon.gmi_plus))
            setMainFabOpenedDrawable(iconHandler.getFabIcon(MaterialDesignIconic.Icon.gmi_close))

            val fabItems = (fabConfig.left + fabConfig.right).map {
                SpeedDialActionItem.Builder(it.id, iconHandler.getFabIcon(it.icon))
                    .setLabel(it.description)
                    .create()
            }
            addAllActionItems(fabItems)

            setOnActionSelectedListener { actionItem ->
                val item = (fabConfig.right + fabConfig.left).find { it.id == actionItem.id }
                if (item != null) {
                    item.onClick?.invoke()
                    // close the speed dial
                    false
                } else {
                    // keep the speed dial open
                    true
                }
            }
        }
    }

    /**
     * Show the "empty" screen
     */
    internal fun showEmpty() {
        binding.recyclerView.visibility = View.INVISIBLE
        binding.layoutEmptyList.layoutEmpty.visibility = View.VISIBLE
    }

    /**
     * Hide the "empty" screen
     */
    internal fun hideEmpty() {
        binding.recyclerView.visibility = View.VISIBLE
        binding.layoutEmptyList.layoutEmpty.visibility = View.INVISIBLE
    }

    override fun onStop() {
        val layoutManager = binding.recyclerView.layoutManager
        if (layoutManager != null && layoutManager is StaggeredGridLayoutManager) {
            val visibleItems = layoutManager.findFirstVisibleItemPositions(null)
            // TODO: set scroll position on viewModel
            //viewModel.lastScrollPosition = if (visibleItems.isNotEmpty()) {
            //    visibleItems.first()
            //} else {
            //    0
            //}
        }

        super.onStop()
    }

}