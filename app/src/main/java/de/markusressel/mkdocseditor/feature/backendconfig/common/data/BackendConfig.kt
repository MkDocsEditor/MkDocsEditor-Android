package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity


data class BackendConfig(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val serverConfig: BackendServerConfig?,
    val authConfig: AuthConfig?,
    val isSelected: Boolean,
)

internal fun BackendConfigEntity.toBackendConfig() = BackendConfig(
    id = entityId,
    name = name,
    description = description,
    isSelected = isSelected,
    serverConfig = serverConfig.target.toServerConfig(),
    authConfig = authConfig.target?.toBackendAuthConfig(),
)
