package de.markusressel.mkdocseditor.feature.backendconfigselection.domain

import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendServerConfig
import javax.inject.Inject

internal class GetBackendConfigItemsUseCase @Inject constructor(

) {
    suspend operator fun invoke(): List<BackendConfig> {
        return listOf(
            BackendConfig(
                id = "1",
                name = "Test",
                description = "Test",
                serverConfiguration = BackendServerConfig(
                    domain = "test",
                    port = 1234,
                    useSsl = false,
                    username = "test",
                    password = "test",
                ),
            ),
            BackendConfig(
                id = "2",
                name = "Test2",
                description = "Test2",
                serverConfiguration = BackendServerConfig(
                    domain = "test2",
                    port = 1234,
                    useSsl = false,
                    username = "test2",
                    password = "test2",
                ),
            ),
        )
    }
}