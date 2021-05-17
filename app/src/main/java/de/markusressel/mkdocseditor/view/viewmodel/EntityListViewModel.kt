package de.markusressel.mkdocseditor.view.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.section.SectionModel
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Base class for implementing a ViewModel for item lists
 */
abstract class EntityListViewModel : ViewModel() {

    // TODO: this probably doesn't simply work like that?
    @Inject
    lateinit var restClient: MkDocsRestClient

    // TODO: use savedState
    val currentSearchFilter = MutableLiveData<String>()

    // TODO: save in state
    val lastScrollPosition = MutableLiveData(0)


    suspend fun reload() {
        restClient.isHostAlive().fold(success = {
            reloadDataFromSource()
            // TODO: tell the view about the state
            // serverUnavailableSnackbar?.dismiss()
        }, failure = {
            if (it is CancellationException) {
                Timber.d { "Reload cancelled" }
            } else {
                Timber.e(it)
                // serverUnavailableSnackbar?.dismiss()
                // serverUnavailableSnackbar = binding.recyclerView.snack(
                //     text = R.string.server_unavailable,
                //     duration = Snackbar.LENGTH_INDEFINITE,
                //     actionTitle = R.string.retry,
                //     action = {
                //         CoroutineScope(Dispatchers.IO).launch {
                //             reload()
                //         }
                //     })
            }
        })
    }

    /**EntityType
     * Reload list data from it's original source, persist it and display it to the user afterwards
     */
    abstract suspend fun reloadDataFromSource()

    /**
     * Define a Single that returns the complete list of data from the (server) source
     */
    internal abstract suspend fun getLoadDataFromSourceFunction(): Result<SectionModel, Exception>

    /**
     * Map the source object to the persistence object
     */
    abstract fun mapToEntity(it: Any): IdentifiableListItem

    /**
     * Persist the current list data
     */
    internal abstract fun persistListData(data: IdentifiableListItem)

    /**
     * Override this if you want to use a different page size
     */
    open fun getPageSize(): Int {
        return DEFAULT_PAGING_SIZE
    }

    companion object {
        private val DEFAULT_PAGING_SIZE = 1
    }

}