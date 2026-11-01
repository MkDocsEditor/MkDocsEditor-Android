package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.RequestManager

/**
 * The REST client of the whole app. It reads the selected backend for every request (on the
 * thread of the request, startup reads nothing), so there is nothing a screen or a worker has
 * to apply first, and a selected or edited backend counts from the next request on. Without a
 * selected backend, requests fail with a BackendNotConfiguredException.
 *
 * @param selectedBackend reads the selected backend from the database
 */
internal fun createSelectedBackendRestClient(
    selectedBackend: () -> BackendConfig?,
    authRepository: AuthRepository,
): MkDocsRestClient {
    val current = {
        try {
            selectedBackend()
        } catch (ex: Exception) {
            Timber.e(ex) { "Could not read the selected backend" }
            null
        }
    }
    return MkDocsRestClient(
        requestManager = RequestManager(
            baseUrl = { current()?.serverConfig?.baseUrl },
            authorizationProvider = AuthorizationProvider.bearer {
                current()?.let { authRepository.token(it.id) }
            },
            unauthorizedListener = {
                current()?.let { authRepository.onUnauthenticated(it.id) }
            },
        ),
    )
}
