package de.markusressel.mkdocseditor.feature.notifications.data

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.feature.notifications.domain.SignedInAccount
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesServer
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.dashboard.FeedPageModel
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import javax.inject.Inject

/** The REST client always talks to the selected server, so this asks that one too */
internal class RestWikiChangesServer @Inject constructor(
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
    private val restClient: IMkDocsRestClient,
) : WikiChangesServer {

    override suspend fun signedInAccount(): SignedInAccount? {
        val backend = backends.selected()?.toBackendRef() ?: return null
        if (!authRepository.isSignedIn(backend.id)) return null
        // the session only knows the username, but the feed compares accounts by id
        val accountId = authRepository.authenticated(backend) { account(it).id }
        return SignedInAccount(backend.id, accountId)
    }

    override suspend fun pins(): PinsModel? = restClient.getPins().orNullIfUnsupported()

    override suspend fun feed(before: String?, limit: Int): FeedPageModel? =
        restClient.getFeed(before = before, limit = limit).orNullIfUnsupported()
}

private fun <T : Any> Result<T, FuelError>.orNullIfUnsupported(): T? = when (this) {
    is Result.Success -> value
    is Result.Failure -> if (error.isUnsupportedEndpoint()) null else throw error
}
