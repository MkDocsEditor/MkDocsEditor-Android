package de.markusressel.mkdocseditor.feature.common.data

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional API features of the selected server (`features` of `GET /server-info/`, see `ServerFeature`). The answer
 * is kept for the app's lifetime, so a page opened offline still shows what the server had once it was reached.
 */
@Singleton
internal class ServerFeatures @Inject constructor(
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) {
    private val known = ConcurrentHashMap<String, Set<String>>()

    /** false while the server can't be asked and has not been before */
    suspend fun has(feature: String): Boolean = feature in features()

    private suspend fun features(): Set<String> {
        val backend = backends.selected()?.toBackendRef() ?: return emptySet()
        known[backend.baseUrl]?.let { return it }
        return try {
            authRepository.serverInfo(backend).features.toSet().also { known[backend.baseUrl] = it }
        } catch (e: Exception) {
            Timber.w(e, "Could not read the server features")
            emptySet()
        }
    }
}
