package de.markusressel.mkdocseditor.feature.browser.ui

import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject

internal class ApplyCurrentBackendConfigUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke() {
        getCurrentBackendConfigUseCase()?.let { config ->
            val serverConfig = requireNotNull(config.serverConfig)
            restClient.setHostname(serverConfig.domain)
            restClient.setPort(serverConfig.port)
            restClient.setUseSSL(serverConfig.useSsl)

            restClient.setBasicAuthConfig(
                config.authConfig?.let {
                    BasicAuthConfig(
                        username = it.username,
                        password = it.password
                    )
                }
            )
        }
    }
}
