package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject

internal class ApplyCurrentBackendConfigUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val restClient: IMkDocsRestClient,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() {
        getCurrentBackendConfigUseCase()?.let { config ->
            val serverConfig = requireNotNull(config.serverConfig)
            restClient.setHostname(serverConfig.domain)
            restClient.setPort(serverConfig.port)
            restClient.setUseSSL(serverConfig.useSsl)

            restClient.setAuthorizationProvider(AuthorizationProvider.bearer { authRepository.token(config.id) })
            restClient.setUnauthorizedListener { authRepository.onUnauthenticated(config.id) }
        }
    }
}
