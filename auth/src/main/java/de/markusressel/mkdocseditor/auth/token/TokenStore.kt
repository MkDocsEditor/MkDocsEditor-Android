package de.markusressel.mkdocseditor.auth.token

import kotlinx.coroutines.flow.Flow

/**
 * Stores the session of each backend configuration, keyed by the backend config id.
 */
interface TokenStore {

    /**
     * @return the stored session of the given backend, or null if there is none (or it cannot be read anymore)
     */
    fun get(backendId: Long): AuthSession?

    /**
     * Stores (or replaces) the session of the given backend
     */
    fun put(backendId: Long, session: AuthSession)

    /**
     * Removes the session of the given backend
     */
    fun remove(backendId: Long)

    /**
     * Emits the id of a backend whenever its session is stored or removed
     */
    val changes: Flow<Long>
}
