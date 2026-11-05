package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.link.JoinLinkParser
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import javax.inject.Inject

/**
 * The link to invite someone to a document. Unlike [GetDocumentUrlUseCase] it has no credentials in it, so it can be sent
 * to anyone.
 */
internal class CreateJoinLinkUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val authRepository: AuthRepository,
) {
    /**
     * @param instanceId the client instance the link should follow
     * @return the link, or null if the server does not know its web client
     */
    suspend operator fun invoke(documentId: String, instanceId: String): String? {
        val backend = getCurrentBackendConfigUseCase()?.toBackendRef() ?: return null
        val clientUrl = authRepository.serverInfo(backend).webClientUrl?.takeIf { it.isNotBlank() } ?: return null
        // a server without accounts has no sender to look for
        val from = if (authRepository.isSignedIn(backend.id)) authRepository.authenticated(backend) { account(it).id } else null
        return JoinLinkParser.build(clientUrl, documentId, from, instanceId)
    }
}
