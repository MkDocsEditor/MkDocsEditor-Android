package de.markusressel.mkdocseditor.data.persistence.base

import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The database that a persistence manager works on */
interface Database {
    val current: StateFlow<BoxStore>
}

/** A database that never changes, for what is the same for all servers */
class FixedDatabase(store: BoxStore) : Database {
    override val current: StateFlow<BoxStore> = MutableStateFlow(store)
}

/**
 * The wiki data of the selected server and account. Each pair has its own database, so a switch keeps everything for
 * offline use. Document ids are a hash of the path, so the data of two servers can't share one database.
 */
class WikiDatabase(initial: BoxStore) : Database {
    private val _current = MutableStateFlow(initial)
    override val current: StateFlow<BoxStore> = _current

    fun switchTo(store: BoxStore) {
        _current.value = store
    }
}
