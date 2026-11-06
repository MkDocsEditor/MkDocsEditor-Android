package de.markusressel.mkdocseditor.feature.avatar.data

import coil3.ImageLoader
import coil3.Uri
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.Options
import coil3.toUri
import de.markusressel.mkdocseditor.auth.api.avatarPath
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
import java.io.IOException

/**
 * The profile picture of an account, as a model for Coil
 *
 * @param accountId the stable id of the account
 * @param version the `avatar` field of the account or its presence, which changes with every new picture
 * @param backendId the backend of the account, null for the selected one
 */
internal data class Avatar(
    val accountId: String,
    val version: String,
    val backendId: Long? = null,
) {
    companion object {
        /** null when the account has no picture, or the server doesn't send the account id */
        fun of(accountId: String?, version: String?, backendId: Long? = null): Avatar? =
            if (accountId.isNullOrEmpty() || version.isNullOrEmpty()) null else Avatar(accountId, version, backendId)
    }
}

/** The version changes with the picture, so it can stay in the memory cache for good */
internal object AvatarKeyer : Keyer<Avatar> {
    override fun key(data: Avatar, options: Options): String = "avatar:${data.backendId}:${data.accountId}:${data.version}"
}

/**
 * Resolves an [Avatar] to the URL of its backend and loads it through the network fetcher with
 * the session of that backend. The backend is read here and not when composing, because
 * reading it touches the database; and the token only goes to the backend it belongs to.
 */
internal class AvatarFetcher(
    private val data: Avatar,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val backend = (data.backendId?.let { backends.get(it) } ?: backends.selected())
            ?: throw IOException("no backend for the avatar")
        val baseUrl = backend.serverConfig?.baseUrl ?: throw IOException("the backend has no server")
        val url: Uri = (baseUrl + avatarPath(data.accountId, data.version)).toUri()

        val headers = NetworkHeaders.Builder().apply {
            authRepository.token(backend.id)?.let { set("Authorization", "Bearer $it") }
        }.build()
        val networkOptions = options.copy(
            extras = options.extras.newBuilder().set(coil3.Extras.Key.httpHeaders, headers).build(),
        )
        val (fetcher, _) = imageLoader.components.newFetcher(url, networkOptions, imageLoader)
            ?: throw IOException("no fetcher for $url")
        return fetcher.fetch()
    }

    class Factory(
        private val backends: AuthBackends,
        private val authRepository: AuthRepository,
    ) : Fetcher.Factory<Avatar> {
        override fun create(data: Avatar, options: Options, imageLoader: ImageLoader): Fetcher =
            AvatarFetcher(data, options, imageLoader, backends, authRepository)
    }
}
