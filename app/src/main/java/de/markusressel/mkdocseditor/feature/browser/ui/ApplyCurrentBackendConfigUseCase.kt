package de.markusressel.mkdocseditor.feature.browser.ui

import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class ApplyCurrentBackendConfigUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke() {
        getCurrentBackendConfigUseCase().filterNotNull().first().let { config ->
            val serverConfig = requireNotNull(config.serverConfig)
            restClient.setHostname(serverConfig.domain)
            restClient.setPort(serverConfig.port)
            restClient.setUseSSL(serverConfig.useSsl)

            val authConfig = requireNotNull(config.authConfig)
            restClient.setBasicAuthConfig(
                BasicAuthConfig(
                    username = authConfig.username,
                    password = authConfig.password
                )
            )
        }
    }
}
