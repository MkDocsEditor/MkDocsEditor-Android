package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendConfig
import javax.inject.Inject

internal class GetBackendConfigItemsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke() = backendConfigRepository.getBackendConfigs().map {
        it.toBackendConfig()
    } + listOf(
        BackendConfig(
            name = "Test",
            description = "Test",
            serverConfig = BackendServerConfig(
                domain = "test",
                port = 1234,
                useSsl = false,
                webBaseUri = "https://test.de",
            ),
            authConfig = AuthConfig(
                username = "test",
                password = "test",
            )
            ),
            BackendConfig(
                name = "Test2",
                description = "Test2",
                serverConfig = BackendServerConfig(
                    domain = "test2",
                    port = 1234,
                    useSsl = false,
                    webBaseUri = "https://test2.de",
                ),
                authConfig = AuthConfig(
                    username = "test2",
                    password = "test2",
                )
            ),
        )
}

